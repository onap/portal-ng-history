/*
 *
 * Copyright (c) 2026. Deutsche Telekom AG
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UntracedPathsIntegrationTest {

  private WebTestClient webTestClient;

  @BeforeEach
  void setup(final ApplicationContext context) {
    webTestClient =
        WebTestClient.bindToApplicationContext(context)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();
  }

  @Test
  void thatProbesAndScrapesAreNotObservedWhileApiRequestsAre() {
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri("/v1/actions")
        .exchange()
        .expectStatus()
        .isOk();
    for (String probe :
        new String[] {
          "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
        }) {
      webTestClient.get().uri(probe).exchange().expectStatus().isOk();
    }
    scrape();

    String metrics = scrape();

    assertThat(metrics)
        .contains("http_server_requests_seconds_count{")
        .contains("uri=\"/v1/actions\"")
        .doesNotContain("uri=\"/actuator/health")
        .doesNotContain("uri=\"/actuator/prometheus\"");
  }

  private String scrape() {
    return webTestClient
        .get()
        .uri("/actuator/prometheus")
        .accept(MediaType.TEXT_PLAIN)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }
}
