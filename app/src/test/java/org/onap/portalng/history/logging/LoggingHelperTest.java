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

package org.onap.portalng.history.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LoggingHelperTest {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingHelperTest.class);

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void thatOnlyItsOwnMdcKeysAreRemoved() {
    MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

    LoggingHelper.info(LOG, Map.of(LogContextVariable.STATUS, "REQUEST"), "RECEIVED");

    assertThat(MDC.getCopyOfContextMap())
        .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736")
        .doesNotContainKey(LogContextVariable.STATUS.getVariableName());
  }
}
