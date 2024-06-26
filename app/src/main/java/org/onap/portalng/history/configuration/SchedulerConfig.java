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

package org.onap.portalng.history.configuration;

import lombok.extern.slf4j.Slf4j;

import org.onap.portalng.history.services.ActionsService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class SchedulerConfig {

  private final ActionsService actionsService;
  private final HistoryConfig historyConfig;

  public SchedulerConfig(ActionsService actionsService, HistoryConfig historyConfig){
    this.actionsService = actionsService;
    this.historyConfig = historyConfig;
  }

  /**
   * This method will be trigger by Spring Boot scheduler.
   * The cron execution time is configured in the application properties as well as the save interval.
   */
  @Scheduled(cron="${history.delete-interval}")
  public void runDeleteActions(){
    actionsService.deleteActions(historyConfig.getSaveInterval());
    log.info("Delete actions in scheduled job");
  }
}
