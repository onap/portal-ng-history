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

package org.onap.portal.history.actions;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.onap.portal.history.entities.ActionsDao;
import org.onap.portal.history.openapi.model.CreateActionRequest;

public class ActionFixtures {

  public static List<CreateActionRequest> createActionRequestList(
    Integer numberOfActions, String userId, OffsetDateTime createdAt){
    List<CreateActionRequest> createActionRequestList = new ArrayList<>();
    for (Integer i = 1; i <= numberOfActions; i++) {
      createActionRequestList.add(
          generateActionRequest(
              "Instantiation", "create", "action" + i, i.toString(), "SO", i, i, i, userId, createdAt));
    }
    return createActionRequestList;
  }

  public static List<CreateActionRequest> createActionRequestListHourOffsetOnly(
      Integer numberOfActions, String userId, OffsetDateTime createdAt){
    List<CreateActionRequest> createActionRequestList = new ArrayList<>();
    for (Integer i = 1; i <= numberOfActions; i++) {
      createActionRequestList.add(
          generateActionRequest(
              "Instantiation", "create", "action" + i, i.toString(), "SO", i, 0, 0, userId, createdAt));
    }
    return createActionRequestList;
  }

  public static CreateActionRequest generateActionRequest(
      String type,
      String action,
      String message,
      String id,
      String downStreamSystem,
      Integer deltaHours,
      Integer deltaMinutes,
      Integer deltaSeconds,
      String userId,
      OffsetDateTime createdAt) {
    ActionDto actionDto = new ActionDto();
    actionDto.setType(type);
    actionDto.setAction(action);
    actionDto.setMessage(message);
    actionDto.setDownStreamSystem(downStreamSystem);
    actionDto.setDownStreamId(id);

    return new CreateActionRequest()
        .userId(userId)
        .action(actionDto)
        .actionCreatedAt(createdAt.minusHours(deltaHours).minusMinutes(deltaMinutes).minusSeconds(deltaSeconds));
  }

  public static List<ActionsDao> actionsDaoList(
    Integer numberOfActions, String userId, OffsetDateTime createdAt){
    List<ActionsDao> actionsDaoList = new ArrayList<>();
    for (Integer i = 1; i <= numberOfActions; i++) {
      actionsDaoList.add(
          generateActionsDao(
              "Instantiation", "create", "action" + i, i.toString(), "SO", i, i, i, userId, createdAt));
    }
    return actionsDaoList;
  }

  public static ActionsDao generateActionsDao(
      String type,
      String action,
      String message,
      String id,
      String downStreamSystem,
      Integer deltaHours,
      Integer deltaMinutes,
      Integer deltaSeconds,
      String userId,
      OffsetDateTime createdAt) {
    ActionDto actionDto = new ActionDto();
    actionDto.setType(type);
    actionDto.setAction(action);
    actionDto.setMessage(message);
    actionDto.setDownStreamSystem(downStreamSystem);
    actionDto.setDownStreamId(id);

    ActionsDao actionsDao = new ActionsDao();
    actionsDao.setUserId(userId);
    actionsDao.setAction(actionDto);
    actionsDao.setActionCreatedAt(new Date(createdAt.minusHours(deltaHours).minusMinutes(deltaMinutes).minusSeconds(deltaSeconds).toEpochSecond()*1000));
    return actionsDao;
  }

  public static List<ActionsDao> actionsDaoListHourOffsetOnly(
    Integer numberOfActions, String userId, OffsetDateTime createdAt){
    List<ActionsDao> actionsDaoList = new ArrayList<>();
    for (Integer i = 1; i <= numberOfActions; i++) {
      actionsDaoList.add(
          generateActionsDao(
              "Instantiation", "create", "action" + i, i.toString(), "SO", i, 0, 0, userId, createdAt));
    }
    return actionsDaoList;
  }
}
