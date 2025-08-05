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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalng.history.actions.ActionDto;
import org.onap.portalng.history.actions.ActionFixtures;
import org.onap.portalng.history.entities.ActionsDao;
import org.onap.portalng.history.openapi.model.ActionResponseApiDto;
import org.onap.portalng.history.openapi.model.ActionsListResponseApiDto;
import org.onap.portalng.history.openapi.model.CreateActionRequestApiDto;
import org.onap.portalng.history.openapi.model.ProblemApiDto;
import org.onap.portalng.history.repository.ActionsRepository;
import org.onap.portalng.history.services.ActionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActionsControllerIntegrationTest {
  @Autowired private WebTestClient webTestClient;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ActionsRepository actionsRepository;
  private final Integer saveInterval = 72;

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
  void testAuthenticatedAccess() {
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri("/v1/actions")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void testUnauthorizedAccess() {
    webTestClient.get().uri("/v1/actions").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void thatUserCanHaveNoHistoryYet() {
    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri("/v1/actions/user")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertNotNull(response);
    assertEquals(0, response.getTotalCount());
  }

  @Test
  void thatActionCanBeSaved() throws Exception {
    final ActionDto actionDto = new ActionDto();
    actionDto.setType("instantiation");
    actionDto.setAction("create");
    actionDto.setDownStreamId("1234");
    actionDto.setDownStreamSystem("SO");
    actionDto.setMessage("no details");
    final CreateActionRequestApiDto actionRequest =
        new CreateActionRequestApiDto()
            .actionCreatedAt(
                OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.SECONDS))
            .userId("user")
            .action(actionDto);

    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .post()
            .uri("/v1/actions/user")
            .body(Mono.just(actionRequest), CreateActionRequestApiDto.class)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(actionRequest.getActionCreatedAt(), response.getActionCreatedAt());
    assertEquals(saveInterval, response.getSaveInterval());
    assertEquals(
        objectMapper.writeValueAsString(actionRequest.getAction()),
        objectMapper.writeValueAsString(response.getAction()));
  }

  @Test
  void thatActionsCanBeListedWithoutParamter() {
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoList(
            500,
            "user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    actionsRepository.saveAll(actionsDaoList);

    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri("/v1/actions")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(10, response.getTotalCount());
    assertEquals(saveInterval, response.getActionsList().get(0).getSaveInterval());
    assertEquals(saveInterval, response.getActionsList().get(9).getSaveInterval());
    assertEquals(
        actionsDaoList.get(0).getAction(),
        objectMapper.valueToTree(response.getActionsList().get(0).getAction()));
    assertEquals(
        actionsDaoList.get(9).getAction(),
        objectMapper.valueToTree(response.getActionsList().get(9).getAction()));
  }

  @Test
  void thatActionsCanBeListedWithParameter() {
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoList(
            20,
            "user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    actionsRepository.saveAll(actionsDaoList);
    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 5)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(5, response.getTotalCount());
    assertEquals(saveInterval, response.getActionsList().get(0).getSaveInterval());
    assertEquals(saveInterval, response.getActionsList().get(4).getSaveInterval());
    assertEquals(
        actionsDaoList.get(0).getAction(),
        objectMapper.valueToTree(response.getActionsList().get(0).getAction()));
    assertEquals(
        actionsDaoList.get(4).getAction(),
        objectMapper.valueToTree(response.getActionsList().get(4).getAction()));
  }

  @Test
  void thatActionsCanBeListedWithParameterInOrderByActionCreatedAt() {
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoList(
            5,
            "user",
            OffsetDateTime.of(LocalDateTime.now().minusDays(2), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(
        ActionFixtures.actionsDaoList(
            5,
            "user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)));
    actionsDaoList.addAll(
        ActionFixtures.actionsDaoList(
            5,
            "user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(6), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)));
    actionsDaoList.addAll(
        ActionFixtures.actionsDaoList(
            5,
            "user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(12), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)));
    actionsRepository.saveAll(actionsDaoList);

    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 5)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(5, response.getTotalCount());
    assertEquals(saveInterval, response.getActionsList().get(0).getSaveInterval());
    assertEquals(saveInterval, response.getActionsList().get(4).getSaveInterval());
    assertEquals(
        actionsDaoList.get(5).getActionCreatedAt().toInstant().atOffset(ZoneOffset.UTC),
        response.getActionsList().get(0).getActionCreatedAt());
    assertEquals(
        actionsDaoList.get(9).getActionCreatedAt().toInstant().atOffset(ZoneOffset.UTC),
        response.getActionsList().get(4).getActionCreatedAt());
  }

  @Test
  void thatActionsCanBeListedWithShowLastHours() {
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            20, "user", OffsetDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.SECONDS));
    actionsRepository.saveAll(actionsDaoList);

    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .queryParam("showLastHours", 12)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(12, response.getTotalCount());
    assertEquals(saveInterval, response.getActionsList().get(0).getSaveInterval());
    assertEquals(
        actionsDaoList.get(0).getAction(),
        objectMapper.valueToTree(response.getActionsList().get(0).getAction()));
    assertEquals(
        actionsDaoList.get(11).getAction(),
        objectMapper.valueToTree(response.getActionsList().get(11).getAction()));
  }

  @Test
  void thatActionsCanNotBeListedWithWrongPageParameter() {
    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions")
                        .queryParam("page", 0)
                        .queryParam("pageSize", 5)
                        .build())
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody(ProblemApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
  }

  @Test
  void thatActionsCanBeGetForUserWithShowLastHours() {
    // First mixed user actions for different users
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10, "user", OffsetDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            10, "user2", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            10, "user3", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));

    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);

    actionsRepository.saveAll(actionsDaoList);

    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .queryParam("showLastHours", 2)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(2, response.getTotalCount());
    assertEquals(saveInterval, response.getActionsList().get(0).getSaveInterval());
  }

  @Test
  void thatActionsCanBeGottenForUserWithShowLastHoursWithMinusValue() {
    // First mixed user actions for different users
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            10,
            "user2",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            10,
            "user3",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList4 =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "user",
            OffsetDateTime.of(LocalDateTime.now().plusHours(48), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsDaoList.addAll(actionsDaoList4);

    actionsRepository.saveAll(actionsDaoList);

    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .queryParam("showLastHours", -2)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(10, response.getTotalCount());
  }

  @Test
  void thatActionsCanBeGottenForUserWithoutParameter() {
    // First mixed user actions for different users
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            10,
            "user2",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            10,
            "user3",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList4 =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsDaoList.addAll(actionsDaoList4);
    actionsRepository.saveAll(actionsDaoList);

    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri("/v1/actions/user")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(10, response.getTotalCount());
    assertEquals(saveInterval, response.getActionsList().get(0).getSaveInterval());
  }

  @Test
  void thatActionsCanBeGottenForUserWithShowLastHoursWithEmptyList() {
    // First mixed user actions for different users
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            10,
            "user2",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            10,
            "user3",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList4 =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsDaoList.addAll(actionsDaoList4);
    actionsRepository.saveAll(actionsDaoList);

    final var response =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user4")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user4")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .queryParam("showLastHours", 2)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(0, response.getTotalCount());
  }

  @Test
  void thatActionsCanBeDeleted() {
    // First mixed user actions for different users
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10, "user", OffsetDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            5, "user2", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            3, "user3", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsRepository.saveAll(actionsDaoList);
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .delete()
        .uri(
            uriBuilder ->
                uriBuilder.path("/v1/actions/user").queryParam("deleteAfterHours", 2).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ActionsListResponseApiDto.class);

    final var responseUser =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    final var responseUser2 =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user2")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user2")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    final var responseUser3 =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user3")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user3")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(2, responseUser.getTotalCount());
    assertEquals(5, responseUser2.getTotalCount());
    assertEquals(3, responseUser3.getTotalCount());
  }

  @Test
  void thatActionsCanBeDeletedForAllUsers(@Autowired final ActionsService actionsService) {
    // First mixed user actions for different users
    final List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(96), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            8,
            "user2",
            OffsetDateTime.of(LocalDateTime.now().minusHours(24), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            5,
            "user3",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    final List<ActionsDao> actionsDaoList4 =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(48), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));

    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsDaoList.addAll(actionsDaoList4);
    actionsRepository.saveAll(actionsDaoList);

    actionsService.deleteActions(72).block();

    final var responseUser =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    final var responseUser2 =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user2")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user2")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    final var responseUser3 =
        webTestClient
            .mutateWith(
                SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user3")))
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v1/actions/user3")
                        .queryParam("page", 1)
                        .queryParam("pageSize", 20)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ActionsListResponseApiDto.class)
            .returnResult()
            .getResponseBody();

    assertEquals(10, responseUser.getTotalCount());
    assertEquals(8, responseUser2.getTotalCount());
    assertEquals(5, responseUser3.getTotalCount());
  }
}
