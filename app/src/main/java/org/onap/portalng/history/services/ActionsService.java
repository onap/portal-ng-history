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

package org.onap.portalng.history.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.onap.portalng.history.entities.ActionsDao;
import org.onap.portalng.history.exception.ProblemException;
import org.onap.portalng.history.openapi.model.ActionResponseApiDto;
import org.onap.portalng.history.openapi.model.ActionsListResponseApiDto;
import org.onap.portalng.history.openapi.model.CreateActionRequestApiDto;
import org.onap.portalng.history.repository.ActionsRepository;
import org.onap.portalng.history.util.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

@Transactional
@RequiredArgsConstructor
@Slf4j
@Service
public class ActionsService {

  private static final String SAVED_METRIC = "history.actions.saved";
  private static final String DELETED_METRIC = "history.actions.deleted";

  private final ActionsRepository repository;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  /**
   * Retrieve actions for a given userId from the database and provide a list with actions
   *
   * @param userId only actions for this <code>userId</code> should be retrieved
   * @param page which page should be retrieved from the list of actions. From a user perspective
   *     the first page has the page number 1. In the response list the first page starts with 0.
   *     Therefore, a subtraction is needed.
   * @param pageSize length of the response list
   * @param showLastHours for which hours from the current time the actions should be retrieved.
   * @param saveInterval value will be part of the response action object. This value is set in the
   *     application properties. In the future this value can be provided from the client.
   * @return If successful object with an item list of action objects and an item with the list
   *     count, otherwise Mono error
   */
  public Mono<ActionsListResponseApiDto> getActions(
      String userId, Integer page, Integer pageSize, Integer showLastHours, Integer saveInterval) {
    Pageable paging =
        PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "actionCreatedAt"));
    var dateAfter = Date.from(ZonedDateTime.now().minusHours(showLastHours).toInstant());
    return Mono.fromCallable(
            () -> repository.findAllByUserIdAndActionCreatedAtAfter(paging, userId, dateAfter))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable)
        .map(actionDao -> toActionResponse(actionDao, saveInterval))
        .collectList()
        .map(this::toActionsListResponse)
        .switchIfEmpty(Mono.just(new ActionsListResponseApiDto().totalCount(0)))
        .onErrorResume(
            ex -> {
              Logger.errorLog("Get actions cannot be executed for user with id ", userId);
              return getError("Get actions can not be executed for user with id " + userId);
            });
  }

  /**
   * Create an action data record in the database
   *
   * @param userId the id of the user for which the action should be stored
   * @param createActionRequest the action object which should be stored
   * @param saveInterval value will be part of the response action object. This value is set in the
   *     application properties. In the future this value can be provided from the client.
   * @return If successful object with the stored action, otherwise Mono error
   */
  public Mono<ActionResponseApiDto> createActions(
      String userId, CreateActionRequestApiDto createActionRequest, Integer saveInterval) {
    return Mono.fromCallable(() -> repository.save(toActionsDao(userId, createActionRequest)))
        .subscribeOn(Schedulers.boundedElastic())
        .map(action -> toActionResponse(action, saveInterval))
        .doOnNext(action -> countSaved("success"))
        .doOnError(ex -> countSaved("failure"))
        .onErrorResume(
            ex -> {
              Logger.errorLog("Action for user can not be executed for user with id ", userId);
              return Mono.error(
                  new ProblemException(
                      HttpStatus.BAD_REQUEST,
                      "Action for user can not be executed for user with id " + userId));
            });
  }

  /**
   * List all actions without a userId filter.
   *
   * @param page which page should be retrieved from the list of actions. From a user perspective
   *     the first page has the page number 1. In the response list the first page starts with 0.
   *     Therefore, a subtraction is needed.
   * @param pageSize length of the response list
   * @param showLastHours for which hours from the current time the actions should be retrieved.
   * @param saveInterval value will be part of the response action object. This value is set in the
   *     application properties. * In the future this value can be provided from the client.
   * @return If successful list with action response object, otherwise Mono error
   */
  public Mono<ActionsListResponseApiDto> listActions(
      Integer page, Integer pageSize, Integer showLastHours, Integer saveInterval) {

    var paging =
        PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "actionCreatedAt"));
    var dateAfter = Date.from(ZonedDateTime.now().minusHours(showLastHours).toInstant());

    return Mono.fromCallable(() -> repository.findAllByActionCreatedAtAfter(paging, dateAfter))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable)
        .map(actionDto -> toActionResponse(actionDto, saveInterval))
        .collectList()
        .map(this::toActionsListResponse)
        .onErrorResume(
            ProblemException.class,
            ex -> {
              Logger.errorLog("List actions cannot be created", null);
              return getError("List actions cannot be created");
            });
  }

  /**
   * Delete actions for a given userId and action is create after hours
   *
   * @param userId the id of the user for which the action should be deleted
   * @param deleteAfterHours hours after the actions should be deleted
   * @return If successful empty Mono object, otherwise Mono error
   */
  public Mono<Object> deleteUserActions(String userId, Integer deleteAfterHours) {
    var dateAfter = Date.from(ZonedDateTime.now().minusHours(deleteAfterHours).toInstant());
    return Mono.fromCallable(
            () -> repository.deleteAllByUserIdAndActionCreatedAtIsBefore(userId, dateAfter))
        .subscribeOn(Schedulers.boundedElastic())
        .doOnNext(deleted -> countDeleted("user", deleted))
        .map(resp -> new Object())
        .onErrorResume(
            ProblemException.class,
            ex -> {
              Logger.errorLog("Deletion of actions cannot be executed for user", userId);
              return Mono.error(ex);
            });
  }

  /**
   * Delete the actions of all users that are older than the given number of hours. Used by the
   * scheduled retention cleanup.
   *
   * @param deleteAfterHours hours after the actions should be deleted
   * @return the number of deleted actions, otherwise Mono error
   */
  public Mono<Long> deleteActions(Integer deleteAfterHours) {
    var dateBefore = Date.from(Instant.now().minus(deleteAfterHours, ChronoUnit.HOURS));
    return Mono.fromCallable(() -> repository.deleteAllByActionCreatedAtIsBefore(dateBefore))
        .subscribeOn(Schedulers.boundedElastic())
        .doOnNext(deleted -> countDeleted("retention", deleted));
  }

  private void countSaved(String outcome) {
    Counter.builder(SAVED_METRIC)
        .description("Actions stored, by outcome")
        .tag("outcome", outcome)
        .register(meterRegistry)
        .increment();
  }

  private void countDeleted(String trigger, long deleted) {
    Counter.builder(DELETED_METRIC)
        .description("Actions deleted, by what triggered the deletion")
        .tag("trigger", trigger)
        .register(meterRegistry)
        .increment(deleted);
  }

  /**
   * @param resp List of ActionResponses
   * @param saveInterval value will be part of the response action object. This value is set in the
   *     application properties.
   * @return ActionsListResponse
   */
  private ActionsListResponseApiDto toActionsListResponse(
      java.util.List<ActionResponseApiDto> actionResponses) {
    var actionsListResponse = new ActionsListResponseApiDto();
    actionsListResponse.setActionsList(actionResponses);
    actionsListResponse.setTotalCount(actionResponses.size());
    return actionsListResponse;
  }

  /**
   * @param actionsDao ActionsDao, return from the MongoDB repository query
   * @param saveInterval value will be part of the response action object. This value is set in the
   *     application properties.
   * @return action response object
   */
  public ActionResponseApiDto toActionResponse(ActionsDao actionsDao, Integer saveInterval) {
    return new ActionResponseApiDto()
        .actionCreatedAt(
            actionsDao.getActionCreatedAt().toInstant().atOffset(ZoneOffset.ofHours(0)))
        .saveInterval(saveInterval)
        .action(actionsDao.getAction());
  }

  private ActionsDao toActionsDao(String userId, CreateActionRequestApiDto createActionRequest) {
    var actionsDao = new ActionsDao();
    actionsDao.setUserId(userId);
    actionsDao.setActionCreatedAt(
        new Date(createActionRequest.getActionCreatedAt().toEpochSecond() * 1000));
    actionsDao.setAction(objectMapper.valueToTree(createActionRequest.getAction()));
    return actionsDao;
  }

  /**
   * Build a problem exception with given message
   *
   * @param message will be detail part of the problem object
   * @return Mono error with problem exception
   */
  private Mono<ActionsListResponseApiDto> getError(String message) {
    return Mono.error(new ProblemException(HttpStatus.BAD_REQUEST, message));
  }
}
