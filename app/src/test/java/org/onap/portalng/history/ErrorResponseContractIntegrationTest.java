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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.onap.portalng.history.ActionsPayloadContractIntegrationTest.assertJson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalng.history.repository.ActionsRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Pins the raw error responses bff receives from this service: status, content type and the exact
 * {@code application/problem+json} body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorResponseContractIntegrationTest {

  @MockitoBean private ActionsRepository actionsRepository;

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
  void thatFailedCreateIsRenderedAsProblem() {
    when(actionsRepository.save(any())).thenThrow(new IllegalStateException("database down"));

    assertJson(
        authenticated()
            .post()
            .uri("/v1/actions/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                "{\"userId\":\"user\",\"actionCreatedAt\":\"2026-01-02T03:04:05Z\",\"action\":{}}")
            .exchange()
            .expectBody()
            .returnResult(),
        HttpStatus.BAD_REQUEST,
        MediaType.APPLICATION_PROBLEM_JSON,
        """
        {"title":"400 BAD_REQUEST","status":400,\
        "detail":"Action for user can not be executed for user with id user"}""");
  }

  @Test
  void thatFailedReadIsRenderedAsProblem() {
    when(actionsRepository.findAllByUserIdAndActionCreatedAtAfter(any(), any(), any()))
        .thenThrow(new IllegalStateException("database down"));

    assertJson(
        get("/v1/actions/user"),
        HttpStatus.BAD_REQUEST,
        MediaType.APPLICATION_PROBLEM_JSON,
        """
        {"title":"400 BAD_REQUEST","status":400,\
        "detail":"Get actions can not be executed for user with id user"}""");
  }

  @Test
  void thatUnexpectedExceptionIsRenderedAsProblemWithInternalServerErrorBody() {
    assertJson(
        get("/v1/actions?page=0"),
        HttpStatus.BAD_REQUEST,
        MediaType.APPLICATION_PROBLEM_JSON,
        """
        {"title":"Internal Server Error","status":500,\
        "detail":"listActions.arg0: must be greater than or equal to 1"}""");
  }

  @Test
  void thatRequestResolutionErrorsHaveNoBody() {
    assertEmpty(get("/v1/actions?page=abc"), HttpStatus.BAD_REQUEST);
    assertEmpty(
        authenticated()
            .post()
            .uri("/v1/actions/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{not json")
            .exchange()
            .expectBody()
            .returnResult(),
        HttpStatus.BAD_REQUEST);
    assertEmpty(get("/v1/unknown"), HttpStatus.NOT_FOUND);
    assertEmpty(
        webTestClient.get().uri("/v1/actions").exchange().expectBody().returnResult(),
        HttpStatus.UNAUTHORIZED);
  }

  private WebTestClient authenticated() {
    return webTestClient.mutateWith(
        SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")));
  }

  private EntityExchangeResult<byte[]> get(String uri) {
    return authenticated().get().uri(uri).exchange().expectBody().returnResult();
  }

  private static void assertEmpty(EntityExchangeResult<byte[]> result, HttpStatus status) {
    assertThat(result.getStatus()).isEqualTo(status);
    assertThat(result.getResponseHeaders().getContentType()).isNull();
    assertThat(result.getResponseBodyContent()).isNullOrEmpty();
  }
}
