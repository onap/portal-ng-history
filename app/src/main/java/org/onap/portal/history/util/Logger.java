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

package org.onap.portal.history.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.net.URI;

@Slf4j
public class Logger {

  private Logger(){}

  /**
   * Write log to stdout for incoming request
   * @param xRequestId from the request header
   * @param methode http methode which is invoke
   * @param path which is called be the request
   */
  public static void requestLog(String xRequestId, HttpMethod methode, URI path) {
    log.info("Portal-history - request - X-Request-Id {} {} {}", xRequestId, methode, path);
  }

  /**
   * Write log to stdout for the outgoing response
   * @param xRequestId from the request header
   * @param code http status of the response
   */
  public static void responseLog(String xRequestId, HttpStatusCode httpStatusCode) {
    log.info("Portal-history - response - X-Request-Id {} {}", xRequestId, httpStatusCode);
  }

  /**
   * Write error log to stdout
   * @param xRequestId from the request header
   * @param msg message which should be written
   * @param id of the related object of the message
   */
  public static void errorLog(String xRequestId, String msg, String id) {
    log.info(
        "Portal-history - error - X-Request-Id {} {} {} not found", xRequestId, msg, id);
  }
}
