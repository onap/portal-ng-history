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

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalng.history.actions.ActionFixtures;
import org.onap.portalng.history.configuration.SchedulerConfig;
import org.onap.portalng.history.entities.ActionsDao;
import org.onap.portalng.history.repository.ActionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScheduledCleanupIntegrationTest {

  @Autowired private SchedulerConfig schedulerConfig;
  @Autowired private ActionsRepository actionsRepository;
  @Autowired private MeterRegistry meterRegistry;

  @BeforeEach
  void setup() {
    actionsRepository.truncateTable();
  }

  @Test
  void thatScheduledJobDeletesActionsOlderThanSaveInterval() {
    final var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    final List<ActionsDao> actions = new ArrayList<>();
    // save-interval is 72 hours in the test configuration
    actions.addAll(ActionFixtures.actionsDaoListHourOffsetOnly(3, "expired", now.minusHours(72)));
    actions.addAll(ActionFixtures.actionsDaoListHourOffsetOnly(3, "retained", now.minusHours(68)));
    actionsRepository.saveAll(actions);

    schedulerConfig.runDeleteActions();

    assertThat(actionsRepository.findAll())
        .extracting(ActionsDao::getUserId)
        .containsExactlyInAnyOrder("retained", "retained", "retained");
  }

  @Test
  void thatScheduledDeletionsAreCounted() {
    final var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    actionsRepository.saveAll(
        ActionFixtures.actionsDaoListHourOffsetOnly(2, "expired", now.minusHours(72)));
    final var deleted = meterRegistry.counter("history.actions.deleted", "trigger", "retention");
    final var before = deleted.count();

    schedulerConfig.runDeleteActions();

    assertThat(deleted.count() - before).isEqualTo(2);
  }
}
