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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TracingIntegrationTest {

  private static WireMockServer wireMockServer;

  @Autowired private ApplicationContext context;

  private WebTestClient webTestClient;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();

    wireMockServer.stubFor(
        post(urlEqualTo("/api/v2/spans"))
            .willReturn(aResponse().withStatus(202).withBody("[]")));
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
    registry.add("management.tracing.sampling.probability", () -> "1.0");
    registry.add("management.tracing.opentelemetry.export.schedule-delay", () -> "200");
    registry.add(
        "management.zipkin.tracing.endpoint",
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

    Thread.sleep(1000);

    wireMockServer.verify(
        moreThanOrExactly(1), postRequestedFor(urlEqualTo("/api/v2/spans")).withRequestBody(containing("[")));
  }
}
