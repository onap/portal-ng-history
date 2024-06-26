/*
 *
 * Copyright (c) 2023. Deutsche Telekom AG
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

import lombok.Getter;

@Getter
public enum LogContextVariable {
  TRACE_ID("trace_id"),
  STATUS("status"),
  NORTHBOUND_METHOD("northbound.method"),
  NORTHBOUND_URL("northbound.url"),
  EXECUTION_TIME("execution.time_ms"),
  HTTP_STATUS("httpStatus");

  private final String variableName;

  LogContextVariable(String variableName) {
    this.variableName = variableName;
  }
}
