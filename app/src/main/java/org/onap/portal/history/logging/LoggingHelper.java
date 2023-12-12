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

package org.onap.portal.history.logging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.Map;
import java.util.function.BiConsumer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoggingHelper {
  public static void error(
      Logger logger, Map<LogContextVariable, String> metadata, String message, Object... args) {
    log(logger::error, metadata, message, args);
  }

  public static void debug(
      Logger logger, Map<LogContextVariable, String> metadata, String message, Object... args) {
    log(logger::debug, metadata, message, args);
  }

  public static void info(
      Logger logger, Map<LogContextVariable, String> metadata, String message, Object... args) {
    log(logger::info, metadata, message, args);
  }

  public static void warn(
      Logger logger, Map<LogContextVariable, String> metadata, String message, Object... args) {
    log(logger::warn, metadata, message, args);
  }

  public static void trace(
      Logger logger, Map<LogContextVariable, String> metadata, String message, Object... args) {
    log(logger::trace, metadata, message, args);
  }

  private static void log(
      BiConsumer<String, Object[]> logMethod,
      Map<LogContextVariable, String> metadata,
      String message,
      Object... args) {
    metadata.forEach((variable, value) -> MDC.put(variable.getVariableName(), value));
    logMethod.accept(message, args);
    MDC.clear();
  }
}
