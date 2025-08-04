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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.onap.portalng.history.exception.ProblemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import reactor.core.publisher.Mono;

@Component
public class Errorhandler implements ErrorWebExceptionHandler {

  @Autowired ObjectMapper objectMapper;

  /**
   * Override the handle methode to implement custom error handling Set response status code to BAD
   * REQUEST, set header content-type and fill the body with the Problem object along the API model
   */
  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ServerHttpResponse httpResponse = exchange.getResponse();
    setResponseStatus(httpResponse, ex);
    httpResponse.getHeaders().add("Content-Type", "application/problem+json");
    return httpResponse.writeWith(
        Mono.fromSupplier(
            () -> {
              DataBufferFactory bufferFactory = httpResponse.bufferFactory();
              try {
                return (httpResponse.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                    ? httpResponse
                        .bufferFactory()
                        .wrap(
                            objectMapper.writeValueAsBytes(
                                setProblemException(httpResponse, ex.getMessage())))
                    : httpResponse.bufferFactory().wrap(objectMapper.writeValueAsBytes(ex));
              } catch (JsonProcessingException e) {
                return bufferFactory.wrap(new byte[0]);
              }
            }));
  }

  /**
   * Set the response status
   *
   * @param httpResponse response which status code should be set
   * @param ex throwable exception to identify the Problem class
   */
  private void setResponseStatus(ServerHttpResponse httpResponse, Throwable ex) {
    if (ex instanceof Problem) {
      httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
    } else {
      httpResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Build a problem exception and set the response status code to BAD REQUEST for every response
   *
   * @param httpResponse response which status code should be set
   * @param message for the detail of the problem exception
   * @return problem exception instance
   */
  private ProblemException setProblemException(ServerHttpResponse httpResponse, String message) {
    httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
    return ProblemException.builder()
        .status(Status.INTERNAL_SERVER_ERROR)
        .title(Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
        .detail(message)
        .build();
  }
}
