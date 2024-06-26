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

package org.onap.portalng.history.controller;

import java.util.Optional;

import org.onap.portalng.history.openapi.api.ActionsApi;
import org.onap.portalng.history.openapi.model.ActionResponse;
import org.onap.portalng.history.openapi.model.ActionsListResponse;
import org.onap.portalng.history.openapi.model.CreateActionRequest;
import org.onap.portalng.history.configuration.HistoryConfig;
import org.onap.portalng.history.services.ActionsService;
import org.onap.portalng.history.util.IdTokenExchange;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
public class ActionsController implements ActionsApi {

  private final ActionsService actionsService;
  private final HistoryConfig historyConfig;

  public ActionsController(ActionsService actionsService, HistoryConfig historyConfig){
    this.actionsService = actionsService;
    this.historyConfig = historyConfig;
  }

  @Override
  public Mono<ResponseEntity<ActionResponse>> createAction(String userId, String xRequestId, Mono<CreateActionRequest> createActionRequest, ServerWebExchange exchange) {

    return IdTokenExchange
      .validateUserId(userId, exchange, xRequestId)
      .then(createActionRequest.flatMap(action -> actionsService.createActions(userId, action, historyConfig.getSaveInterval(), xRequestId)))
      .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Object>> deleteActions(String userId, String xRequestId, Integer deleteAfterHours, ServerWebExchange exchange) {

    return IdTokenExchange
      .validateUserId(userId, exchange, xRequestId)
      .then(actionsService.deleteUserActions(userId, deleteAfterHours, xRequestId))
      .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<ActionsListResponse>> getActions(String userId, String xRequestId, Optional<Integer> page, Optional<Integer> pageSize, Optional<Integer> showLastHours, ServerWebExchange exchange) {

    return IdTokenExchange
      .validateUserId(userId, exchange, xRequestId)
      .then(actionsService.getActions(userId, page.orElse(1), pageSize.orElse(10), showLastHours.orElse(historyConfig.getSaveInterval()), historyConfig.getSaveInterval(), xRequestId))
      .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<ActionsListResponse>> listActions(String xRequestId, Optional<Integer> page, Optional<Integer> pageSize, Optional<Integer> showLastHours, ServerWebExchange exchange) {

    return actionsService
      .listActions(page.orElse(1), pageSize.orElse(10), showLastHours.orElse(historyConfig.getSaveInterval()), historyConfig.getSaveInterval(), xRequestId)
      .map(ResponseEntity::ok);
  }
}
