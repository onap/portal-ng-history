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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.onap.portalng.history.openapi.model.ActionResponse;
import org.onap.portalng.history.openapi.model.ActionsListResponse;
import org.onap.portalng.history.openapi.model.CreateActionRequest;
import org.onap.portalng.history.entities.ActionsDao;
import org.onap.portalng.history.exception.ProblemException;
import org.onap.portalng.history.repository.ActionsRepository;
import org.onap.portalng.history.util.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
@Service
public class ActionsService {

  @Autowired
  private ActionsRepository repository;

  /**
   * Retrieve actions for a given userId from the database and provide a list with actions
   * @param userId only actions for this <code>userId</code> should be retrieved
   * @param page which page should be retrieved from the list of actions. From a user perspective the first page has the page number 1.
   *            In the response list the first page starts with 0. Therefore, a subtraction is needed.
   * @param pageSize length of the response list
   * @param showLastHours for which hours from the current time the actions should be retrieved.
   * @param saveInterval value will be part of the response action object. This value is set in the application properties.
   *                    In the future this value can be provided from the client.
   * @param xRequestId from the request header. Will be used in an error log
   * @return If successful object with an item list of action objects and an item with the list count, otherwise Mono error
   */
  public Mono<ActionsListResponse> getActions(String userId, Integer page, Integer pageSize, Integer showLastHours, Integer saveInterval, String xRequestId){
    Pageable paging = PageRequest.of(page - 1 , pageSize, Sort.by(Sort.Direction.DESC, "actionCreatedAt"));
    var dateAfter = Date.from(ZonedDateTime.now().minusHours(showLastHours).toInstant());
    return repository
      .findAllByUserIdAndActionCreatedAtAfter(paging,userId, dateAfter)
      .map(actionDao -> toActionResponse(actionDao, saveInterval))
      .collectList()
      .map(this::toActionsListResponse)
      .switchIfEmpty(Mono.just(new ActionsListResponse().totalCount(0)))
      .onErrorResume(ex -> {
        Logger.errorLog(xRequestId,"Get actions cannot be executed for user with id ", userId);
        return getError("Get actions can not be executed for user with id " + userId);
      });
  }

  /**
   * Create an action data record in the database
   * @param userId the id of the user for which the action should be stored
   * @param createActionRequest the action object which should be stored
   * @param saveInterval value will be part of the response action object. This value is set in the application properties.
   *                     In the future this value can be provided from the client.
   * @param xRequestId from the request header. Will be used in an error log
   * @return If successful object with the stored action, otherwise Mono error
   */
  public Mono<ActionResponse> createActions(String userId, CreateActionRequest createActionRequest, Integer saveInterval, String xRequestId) {
    return repository
      .save(toActionsDao(userId, createActionRequest))
      .map(action -> toActionResponse(action, saveInterval))
      .onErrorResume(ex -> {
        Logger.errorLog(xRequestId,"Action for user can not be executed for user with id ", userId );
        return Mono.error(ProblemException.builder()
          .type(Problem.DEFAULT_TYPE)
          .status(Status.BAD_REQUEST)
          .title(HttpStatus.BAD_REQUEST.toString())
          .detail("Action for user can not be executed for user with id " + userId)
          .build());
      });
  }

  /**
   * List all actions without a userId filter.
   * @param page which page should be retrieved from the list of actions. From a user perspective the first page has the page number 1.
   *            In the response list the first page starts with 0. Therefore, a subtraction is needed.
   * @param pageSize length of the response list
   * @param showLastHours for which hours from the current time the actions should be retrieved.
   * @param saveInterval value will be part of the response action object. This value is set in the application properties.
   *    *                    In the future this value can be provided from the client.
   * @param xRequestId from the request header. Will be used in an error log
   * @return If successful list with action response object, otherwise Mono error
   */
  public Mono<ActionsListResponse> listActions(Integer page, Integer pageSize, Integer showLastHours, Integer saveInterval, String xRequestId){

    var paging = PageRequest.of(page - 1 , pageSize, Sort.by(Sort.Direction.DESC, "actionCreatedAt"));
    var dateAfter = Date.from(ZonedDateTime.now().minusHours(showLastHours).toInstant());

    return repository
      .findAllByActionCreatedAtAfter(paging,dateAfter)
      .map(actionDto -> toActionResponse(actionDto, saveInterval))
      .collectList()
      .map(this::toActionsListResponse)
      .onErrorResume(ProblemException.class,
        ex -> {
          Logger.errorLog(xRequestId,"List actions cannot be created", null );
          return getError("List actions cannot be created");
      });
  }

  /**
   * Delete actions for a given userId and action is create after hours
   * @param userId the id of the user for which the action should be deleted
   * @param deleteAfterHours hours after the actions should be deleted
   * @param xRequestId from the request header. Will be used in an error log
   * @return If successful empty Mono object, otherwise Mono error
   */
  public Mono<Object> deleteUserActions(String userId, Integer deleteAfterHours, String xRequestId ){
    var dateAfter = Date.from(ZonedDateTime.now().minusHours(deleteAfterHours).toInstant());
    return repository
      .deleteAllByUserIdAndActionCreatedAtIsBefore(userId, dateAfter)
      .map(resp -> new Object())
      .onErrorResume(ProblemException.class,ex -> {
        Logger.errorLog(xRequestId,"Deletion of actions cannot be executed for user", userId );
        return Mono.error(ex);
      });
  }

  /**
   * Delete actions after hours. This service will be used in the cron job. The job will be implemented with a separate user story.
   * @param deleteAfterHours hours after the actions should be deleted
   * @return If successful empty Mono object, otherwise Mono error
   */
  public Mono<Object> deleteActions(Integer deleteAfterHours ){
    var dateAfter = Date.from(LocalDateTime.now().minusHours(deleteAfterHours).atZone(ZoneId.of("CET")).toInstant());
    return repository
      .deleteAllByActionCreatedAtIsBefore(dateAfter)
      .map(resp -> new Object())
      .onErrorResume(ProblemException.class,ex -> {
        Logger.errorLog(null,"Delete all actions in cron job cannot be executed ", null);
        return getError("Delete all actions after hours cannot be executed");
      });
  }

  /**
   *
   * @param resp List of ActionResponses
   * @param saveInterval value will be part of the response action object. This value is set in the application properties.
   * @return ActionsListResponse
   */
  private ActionsListResponse toActionsListResponse(java.util.List<ActionResponse> actionResponses) {
    var actionsListResponse = new ActionsListResponse();
    actionsListResponse.setActionsList(actionResponses);
    actionsListResponse.setTotalCount(actionResponses.size());
    return actionsListResponse;
  }

  /**
   *
   * @param actionsDao ActionsDao, return from the MongoDB repository query
   * @param saveInterval value will be part of the response action object. This value is set in the application properties.
   * @return action response object
   */
  public ActionResponse toActionResponse(ActionsDao actionsDao, Integer saveInterval){
    return new ActionResponse()
        .actionCreatedAt(actionsDao.getActionCreatedAt().toInstant().atOffset(ZoneOffset.ofHours(0)))
        .saveInterval(saveInterval)
        .action(actionsDao.getAction());
  }

  private ActionsDao toActionsDao(String userId, CreateActionRequest createActionRequest) {
    var actionsDao = new ActionsDao();
    actionsDao.setUserId(userId);
    actionsDao.setActionCreatedAt(new Date(createActionRequest.getActionCreatedAt().toEpochSecond()*1000));
    actionsDao.setAction(createActionRequest.getAction());
    return actionsDao;
  }

  /**
   * Build a problem exception with given message
   * @param message will be detail part of the problem object
   * @return Mono error with problem exception
   */
  private Mono<ActionsListResponse> getError(String message) {
    return Mono.error(ProblemException.builder()
        .type(Problem.DEFAULT_TYPE)
        .status(Status.BAD_REQUEST)
        .title(HttpStatus.BAD_REQUEST.toString())
        .detail(message)
        .build());
  }

}
