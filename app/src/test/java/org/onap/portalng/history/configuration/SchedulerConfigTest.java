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

package org.onap.portalng.history.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.onap.portalng.history.services.ActionsService;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Mono;

@ExtendWith(OutputCaptureExtension.class)
class SchedulerConfigTest {

  private final ActionsService actionsService = mock(ActionsService.class);
  private final SchedulerConfig schedulerConfig =
      new SchedulerConfig(actionsService, new HistoryConfig(72));

  @Test
  void thatDeletedCountIsLogged(CapturedOutput output) {
    when(actionsService.deleteActions(72)).thenReturn(Mono.just(5L));

    schedulerConfig.runDeleteActions();

    assertThat(output).contains("Scheduled job deleted 5 actions older than 72 hours");
  }

  @Test
  void thatFailureIsLoggedAsError(CapturedOutput output) {
    when(actionsService.deleteActions(72))
        .thenReturn(Mono.error(new DataAccessResourceFailureException("database unavailable")));

    assertThatNoException().isThrownBy(schedulerConfig::runDeleteActions);

    assertThat(output)
        .contains("Scheduled job failed to delete actions older than 72 hours")
        .contains("ERROR")
        .contains("DataAccessResourceFailureException: database unavailable");
  }
}
