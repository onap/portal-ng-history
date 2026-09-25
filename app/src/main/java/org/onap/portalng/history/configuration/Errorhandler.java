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

import lombok.RequiredArgsConstructor;
import org.onap.portalng.history.exception.ProblemException;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class Errorhandler implements ErrorWebExceptionHandler {

  private final ObjectMapper objectMapper;

  /**
   * Renders the exception as an {@code application/problem+json} body. A {@link ProblemException}
   * keeps its own status and body. Any other exception is answered with HTTP 400 and a body whose
   * {@code status} is 500: bff has always received that combination, so do not align the two.
   */
  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ServerHttpResponse httpResponse = exchange.getResponse();
    ProblemDetail problemDetail;
    if (ex instanceof ProblemException problemException) {
      httpResponse.setStatusCode(problemException.getStatusCode());
      problemDetail = problemException.getBody();
    } else {
      httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
      problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
      problemDetail.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
      problemDetail.setDetail(ex.getMessage());
    }
    httpResponse.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return httpResponse.writeWith(
        Mono.fromSupplier(
            () -> {
              DataBufferFactory bufferFactory = httpResponse.bufferFactory();
              try {
                return bufferFactory.wrap(objectMapper.writeValueAsBytes(problemDetail));
              } catch (JacksonException e) {
                return bufferFactory.wrap(new byte[0]);
              }
            }));
  }
}
