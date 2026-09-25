/*
 *
 * Copyright (c) 2025. Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *
 */

package org.onap.portalng.history;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class TracingIntegrationTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
  private static final String REQUEST_ID = "request-id-from-bff";
  private static final Duration EXPORT_TIMEOUT = Duration.ofSeconds(10);

  private static WireMockServer wireMockServer;

  @Autowired private ApplicationContext context;
  @Autowired private ObjectMapper objectMapper;

  private WebTestClient webTestClient;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();

    wireMockServer.stubFor(
        post(urlEqualTo("/api/v2/spans")).willReturn(aResponse().withStatus(202).withBody("[]")));
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null && wireMockServer.isRunning()) {
      wireMockServer.stop();
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("management.tracing.enabled", () -> "true");
    registry.add("management.tracing.export.enabled", () -> "true");
    registry.add("management.tracing.sampling.probability", () -> "1.0");
    registry.add("management.opentelemetry.tracing.export.schedule-delay", () -> "200ms");
    registry.add(
        "management.tracing.export.zipkin.endpoint",
        () -> "http://localhost:" + wireMockServer.port() + "/api/v2/spans");
  }

  @BeforeEach
  void setup() {
    webTestClient =
        WebTestClient.bindToApplicationContext(context)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();

    wireMockServer.resetRequests();
  }

  @Test
  void testThatTracesAreExported() throws InterruptedException {
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri("/non-existent-endpoint")
        .exchange()
        .expectStatus()
        .is4xxClientError();

    assertThat(awaitExportedSpans(spans -> !spans.isEmpty())).isTrue();
  }

  @Test
  void thatRequestLogLinesCarryTraceIdAndRequestId(CapturedOutput output) {
    getActions();

    List<JsonNode> requestLines =
        logLines(output).stream()
            .filter(line -> REQUEST_ID.equals(line.path("request_id").asString()))
            .toList();

    assertThat(requestLines)
        .extracting(line -> line.path("message").asString())
        .contains("RECEIVED", "FINISHED");
    assertThat(requestLines)
        .allSatisfy(
            line -> {
              assertThat(line.path("trace_id").asString()).isEqualTo(TRACE_ID);
              assertThat(line.path("span_id").asString()).matches("[0-9a-f]{16}");
            });
  }

  @Test
  void thatDatabaseCallsAreExportedAsSpansOfTheRequest() throws InterruptedException {
    getActions();

    assertThat(awaitExportedSpans(spans -> containsSpan(spans, TRACE_ID, "query"))).isTrue();
  }

  private void getActions() {
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri("/v1/actions/user")
        .headers(
            headers -> {
              headers.set("traceparent", TRACEPARENT);
              headers.set("X-Request-Id", REQUEST_ID);
            })
        .exchange()
        .expectStatus()
        .isOk();
  }

  private boolean awaitExportedSpans(Predicate<List<JsonNode>> condition)
      throws InterruptedException {
    long deadline = System.nanoTime() + EXPORT_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      List<JsonNode> spans =
          wireMockServer.findAll(postRequestedFor(urlEqualTo("/api/v2/spans"))).stream()
              .map(LoggedRequest::getBodyAsString)
              .map(this::readTree)
              .flatMap(body -> StreamSupport.stream(body.spliterator(), false))
              .toList();
      if (condition.test(spans)) {
        return true;
      }
      Thread.sleep(100);
    }
    return false;
  }

  private static boolean containsSpan(List<JsonNode> spans, String traceId, String name) {
    return spans.stream()
        .anyMatch(
            span ->
                traceId.equals(span.path("traceId").asString())
                    && name.equals(span.path("name").asString()));
  }

  private List<JsonNode> logLines(CapturedOutput output) {
    return output
        .getOut()
        .lines()
        .filter(line -> line.startsWith("{"))
        .map(this::readTree)
        .toList();
  }

  private JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      return objectMapper.nullNode();
    }
  }
}
