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

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalng.history.repository.ActionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Pins the raw JSON of successful responses and of the persisted {@code action} column, byte for
 * byte, so that changes to the JSON mapper configuration (Jackson defaults, inclusion rules,
 * property order, date/time format) cannot silently alter what bff receives or what is stored.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActionsPayloadContractIntegrationTest {

  private static final String ACTION =
      """
      {"type":"instantiation","nested":{"z":1,"a":[1,2.5,true,null,"x"],"inner":{"k":null,"v":"w"},\
      "empty":{}},"topNull":null,"big":12345678901234567890,"dec":0.10,"uni":"\\u00e4\\u20ac",\
      "b":false}""";

  @Autowired private ActionsRepository actionsRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private WebTestClient webTestClient;

  @BeforeEach
  void setup(final ApplicationContext context) {
    webTestClient =
        WebTestClient.bindToApplicationContext(context)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();
    actionsRepository.truncateTable();
  }

  @Test
  void thatCreatedActionIsRenderedAndStoredUnchanged() {
    final var result =
        post(
            """
            {"userId":"ignored","actionCreatedAt":"2026-01-02T05:04:05.789+02:00",\
            "action":%s,"unknownProperty":true}"""
                .formatted(ACTION));

    assertJson(
        result,
        HttpStatus.OK,
        MediaType.APPLICATION_JSON,
        """
        {"actionCreatedAt":"2026-01-02T03:04:05Z","action":{"type":"instantiation","nested":\
        {"z":1,"a":[1,2.5,true,null,"x"],"inner":{"v":"w"},"empty":{}},"big":12345678901234567890,\
        "dec":0.1,"uni":"ä€","b":false},"saveInterval":72}""");
    assertThat(jdbcTemplate.queryForObject("SELECT action::text FROM actions", String.class))
        .isEqualTo(
            """
            {"b": false, "big": 12345678901234567890, "dec": 0.1, "uni": "ä€", "type": \
            "instantiation", "nested": {"a": [1, 2.5, true, null, "x"], "z": 1, "empty": {}, \
            "inner": {"v": "w"}}}""");
  }

  @Test
  void thatNonObjectActionsAndEpochTimestampsAreAccepted() {
    assertJson(
        post("{\"userId\":\"u\",\"actionCreatedAt\":\"2026-01-02T03:04:05Z\",\"action\":[1,2]}"),
        HttpStatus.OK,
        MediaType.APPLICATION_JSON,
        "{\"actionCreatedAt\":\"2026-01-02T03:04:05Z\",\"action\":[1,2],\"saveInterval\":72}");
    assertJson(
        post("{\"userId\":\"u\",\"actionCreatedAt\":1767323045,\"action\":\"text\"}"),
        HttpStatus.OK,
        MediaType.APPLICATION_JSON,
        "{\"actionCreatedAt\":\"2026-01-02T03:04:05Z\",\"action\":\"text\",\"saveInterval\":72}");
  }

  @Test
  void thatStoredActionsAreListedUnchanged() {
    // Written straight to the table, as rows from earlier releases are, so the read path is
    // pinned independently of how the service serializes on write.
    insert("user", "2026-01-02T03:04:05Z", "{\"k\": null, \"n\": {\"x\": [null, 1.50]}}");
    insert("user", "2026-01-02T04:04:05Z", "\"plain\"");
    insert("other", "2026-01-02T05:04:05Z", "{}");

    final String userActions =
        """
        {"actionsList":[{"actionCreatedAt":"2026-01-02T04:04:05Z","action":"plain",\
        "saveInterval":72},{"actionCreatedAt":"2026-01-02T03:04:05Z","action":{"k":null,"n":\
        {"x":[null,1.5]}},"saveInterval":72}],"totalCount":2}""";
    assertJson(
        get("/v1/actions/user?showLastHours=1000000"),
        HttpStatus.OK,
        MediaType.APPLICATION_JSON,
        userActions);
    assertJson(
        get("/v1/actions?showLastHours=1000000&pageSize=1"),
        HttpStatus.OK,
        MediaType.APPLICATION_JSON,
        """
        {"actionsList":[{"actionCreatedAt":"2026-01-02T05:04:05Z","action":{},\
        "saveInterval":72}],"totalCount":1}""");
  }

  @Test
  void thatEmptyHistoryAndDeletionAreRenderedUnchanged() {
    assertJson(
        get("/v1/actions/user"),
        HttpStatus.OK,
        MediaType.APPLICATION_JSON,
        "{\"actionsList\":[],\"totalCount\":0}");

    final var deleted =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .delete()
            .uri("/v1/actions/user?deleteAfterHours=1")
            .exchange()
            .expectBody()
            .returnResult();
    assertJson(deleted, HttpStatus.OK, MediaType.APPLICATION_JSON, "{}");
  }

  private EntityExchangeResult<byte[]> post(String body) {
    return webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .post()
        .uri("/v1/actions/user")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectBody()
        .returnResult();
  }

  private EntityExchangeResult<byte[]> get(String uri) {
    return webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri(uri)
        .exchange()
        .expectBody()
        .returnResult();
  }

  private void insert(String userId, String createdAt, String actionJson) {
    jdbcTemplate.update(
        "INSERT INTO actions (id, user_id, action_created_at, action) VALUES (?, ?, ?, ?::jsonb)",
        UUID.randomUUID().toString(),
        userId,
        Timestamp.from(Instant.parse(createdAt)),
        actionJson);
  }

  static void assertJson(
      EntityExchangeResult<byte[]> result,
      HttpStatus status,
      MediaType contentType,
      String expectedBody) {
    assertThat(result.getStatus()).isEqualTo(status);
    assertThat(result.getResponseHeaders().getContentType()).isEqualTo(contentType);
    assertThat(new String(result.getResponseBodyContent(), StandardCharsets.UTF_8))
        .isEqualTo(expectedBody);
  }
}
