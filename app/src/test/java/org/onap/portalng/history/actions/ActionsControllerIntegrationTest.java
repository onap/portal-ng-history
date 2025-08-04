/*
 *
 * Copyright (c) 2022. Deutsche Telekom AG
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

package org.onap.portalng.history.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalng.history.BaseIntegrationTest;
import org.onap.portalng.history.entities.ActionsDao;
import org.onap.portalng.history.openapi.model.ActionResponseApiDto;
import org.onap.portalng.history.openapi.model.ActionsListResponseApiDto;
import org.onap.portalng.history.openapi.model.CreateActionRequestApiDto;
import org.onap.portalng.history.openapi.model.ProblemApiDto;
import org.onap.portalng.history.repository.ActionsRepository;
import org.onap.portalng.history.services.ActionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ActionsControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired ActionsService actionsService;

  @Autowired private ActionsRepository repository;

  // @Value("${history.save-interval}")
  protected Integer saveInterval = 72;

  @BeforeEach
  void deleteMongoDataBase() {
    repository.deleteAll();
  }

  @Test
  void thatUserCanHaveNoHistoryYet() throws JsonProcessingException {
    ActionsListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test-user")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertNotNull(response);
    assertThat(response.getTotalCount()).isEqualTo(0);
  }

  @Test
  void thatActionCanBeSaved() throws Exception {
    ActionDto actionDto = new ActionDto();
    actionDto.setType("instantiation");
    actionDto.setAction("create");
    actionDto.setDownStreamId("1234");
    actionDto.setDownStreamSystem("SO");
    actionDto.setMessage("no details");

    CreateActionRequestApiDto actionRequest =
        new CreateActionRequestApiDto()
            .actionCreatedAt(
                OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.SECONDS))
            .userId("test-user")
            .action(actionDto);

    ActionResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(actionRequest)
            .when()
            .post("/v1/actions/test-user")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionResponseApiDto.class);

    assertThat(response.getActionCreatedAt())
        .isEqualTo(
            actionRequest
                .getActionCreatedAt()
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_DATE_TIME));
    assertThat(response.getSaveInterval()).isEqualTo(saveInterval);
    assertThat(objectMapper.writeValueAsString(response.getAction()))
        .isEqualTo(objectMapper.writeValueAsString(actionRequest.getAction()));
  }

  @Test
  void thatActionsCanBeListedWithoutParameter() throws JsonProcessingException {
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoList(
            500,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    repository.saveAll(actionsDaoList);
    ActionsListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(response.getTotalCount()).isEqualTo(10);
    assertThat(response.getActionsList().get(0).getSaveInterval()).isEqualTo(saveInterval);
    assertThat(response.getActionsList().get(9).getSaveInterval()).isEqualTo(saveInterval);
    assertThat(
        objectMapper
            .valueToTree(response.getActionsList().get(0).getAction())
            .equals(actionsDaoList.get(0).getAction()));
    assertThat(
        objectMapper
            .valueToTree(response.getActionsList().get(9).getAction())
            .equals(actionsDaoList.get(9).getAction()));
  }

  @Test
  void thatActionsCanBeListedWithParameter() throws JsonProcessingException {
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoList(
            20,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    repository.saveAll(actionsDaoList);
    ActionsListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions?page=1&pageSize=5")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(response.getTotalCount()).isEqualTo(5);
    assertThat(response.getActionsList().get(0).getSaveInterval()).isEqualTo(saveInterval);
    assertThat(response.getActionsList().get(4).getSaveInterval()).isEqualTo(saveInterval);
    assertThat(
        objectMapper
            .valueToTree(response.getActionsList().get(0).getAction())
            .equals(actionsDaoList.get(0).getAction()));
    assertThat(
        objectMapper
            .valueToTree(response.getActionsList().get(4).getAction())
            .equals(actionsDaoList.get(4).getAction()));
  }

  @Test
  void thatActionsCanBeListedWithParameterInOrderByActionCreatedAt() {
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoList(
            5,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now().minusDays(2), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(
        ActionFixtures.actionsDaoList(
            5,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)));
    actionsDaoList.addAll(
        ActionFixtures.actionsDaoList(
            5,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(6), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)));
    actionsDaoList.addAll(
        ActionFixtures.actionsDaoList(
            5,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(12), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)));
    repository.saveAll(actionsDaoList);

    ActionsListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions?page=1&pageSize=5")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(response.getTotalCount()).isEqualTo(5);
    assertThat(response.getActionsList().get(0).getSaveInterval()).isEqualTo(saveInterval);
    assertThat(response.getActionsList().get(4).getSaveInterval()).isEqualTo(saveInterval);
    assertThat(response.getActionsList().get(0).getActionCreatedAt())
        .isEqualTo(actionsDaoList.get(5).getActionCreatedAt().toInstant().atOffset(ZoneOffset.UTC));
    assertThat(response.getActionsList().get(4).getActionCreatedAt())
        .isEqualTo(actionsDaoList.get(9).getActionCreatedAt().toInstant().atOffset(ZoneOffset.UTC));
  }

  @Test
  void thatActionsCanBeListedWithShowLastHours() throws JsonProcessingException {
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            20, "test-user", OffsetDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.SECONDS));
    repository.saveAll(actionsDaoList);

    ActionsListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions?page=1&pageSize=20&showLastHours=12")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(response.getTotalCount()).isEqualTo(12);
    assertThat(response.getActionsList().get(0).getSaveInterval()).isEqualTo(saveInterval);
    assertThat(
        objectMapper
            .valueToTree(response.getActionsList().get(0).getAction())
            .equals(actionsDaoList.get(0).getAction()));
    assertThat(
        objectMapper
            .valueToTree(response.getActionsList().get(11).getAction())
            .equals(actionsDaoList.get(11).getAction()));
  }

  @Test
  void thatActionsCanNotBeListedWithWrongPageParameter() {
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoList(
            5,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now().minusDays(2), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    repository.saveAll(actionsDaoList);

    ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions?page=0&pageSize=5")
            .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .extract()
            .body()
            .as(ProblemApiDto.class);

    assertThat(response.getStatus()).isEqualTo(500);
  }

  @Test
  void thatActionsCanBeGetForUserWithShowLastHours() {
    // First mixed user actions for different users
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10, "test-user", OffsetDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            10, "test2-user", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            10, "test3-user", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));

    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);

    repository.saveAll(actionsDaoList);

    ActionsListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test-user?page=1&pageSize=20&showLastHours=2")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getActionsList().get(0).getSaveInterval()).isEqualTo(saveInterval);
  }

  @Test
  void thatActionsCanBeGottenForUserWithShowLastHoursWithMinusValue() {
    // First mixed user actions for different users
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            10,
            "test2-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            10,
            "test3-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList4 =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now().plusHours(48), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsDaoList.addAll(actionsDaoList4);

    repository.saveAll(actionsDaoList);

    ActionsListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test-user?page=1&pageSize=20&showLastHours=-2")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(response.getTotalCount()).isEqualTo(10);
  }

  @Test
  void thatActionsCanBeGottenForUserWithoutParameter() {
    // First mixed user actions for different users
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            10,
            "test2-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            10,
            "test3-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList4 =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsDaoList.addAll(actionsDaoList4);
    repository.saveAll(actionsDaoList);

    ActionsListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test-user")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(response.getTotalCount()).isEqualTo(10);
    assertThat(response.getActionsList().get(0).getSaveInterval()).isEqualTo(saveInterval);
  }

  @Test
  void thatActionsCanBeGottenForUserWithShowLastHoursWithEmptyList() {
    // First mixed user actions for different users
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            10,
            "test2-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            10,
            "test3-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList4 =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsDaoList.addAll(actionsDaoList4);
    repository.saveAll(actionsDaoList);

    ActionsListResponseApiDto response =
        requestSpecification("test4-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test4-user?page=1&pageSize=20&showLastHours=2")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(response.getTotalCount()).isZero();
  }

  @Test
  void thatActionsCanBeDeleted() {
    // First mixed user actions for different users
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10, "test-user", OffsetDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            5, "test2-user", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            3, "test3-user", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    repository.saveAll(actionsDaoList);

    requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .when()
        .delete("/v1/actions/test-user?deleteAfterHours=2")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(ActionsListResponseApiDto.class);

    ActionsListResponseApiDto responseGetUser =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test-user?page=1&pageSize=20")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    ActionsListResponseApiDto responseGetUser2 =
        requestSpecification("test2-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test2-user")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    ActionsListResponseApiDto responseGetUser3 =
        requestSpecification("test3-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test3-user")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(responseGetUser.getTotalCount()).isEqualTo(2);
    assertThat(responseGetUser2.getTotalCount()).isEqualTo(5);
    assertThat(responseGetUser3.getTotalCount()).isEqualTo(3);
  }

  @Test
  void thatActionsCanBeDeletedForAllUsers() {
    // First mixed user actions for different users
    List<ActionsDao> actionsDaoList =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(96), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList2 =
        ActionFixtures.actionsDaoList(
            8,
            "test2-user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(24), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList3 =
        ActionFixtures.actionsDaoList(
            5,
            "test3-user",
            OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    List<ActionsDao> actionsDaoList4 =
        ActionFixtures.actionsDaoListHourOffsetOnly(
            10,
            "test-user",
            OffsetDateTime.of(LocalDateTime.now().minusHours(48), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS));

    actionsDaoList.addAll(actionsDaoList2);
    actionsDaoList.addAll(actionsDaoList3);
    actionsDaoList.addAll(actionsDaoList4);
    repository.saveAll(actionsDaoList);

    actionsService.deleteActions(72).block();

    ActionsListResponseApiDto responseGetUser =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test-user?page=1&pageSize=20")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    ActionsListResponseApiDto responseGetUser2 =
        requestSpecification("test2-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test2-user")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    ActionsListResponseApiDto responseGetUser3 =
        requestSpecification("test3-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/actions/test3-user")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(ActionsListResponseApiDto.class);

    assertThat(responseGetUser.getTotalCount()).isEqualTo(10);
    assertThat(responseGetUser2.getTotalCount()).isEqualTo(8);
    assertThat(responseGetUser3.getTotalCount()).isEqualTo(5);
  }
}
