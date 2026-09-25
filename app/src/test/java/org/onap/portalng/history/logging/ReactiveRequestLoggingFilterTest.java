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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReactiveRequestLoggingFilterTest {

  private final ReactiveRequestLoggingFilter filter =
      new ReactiveRequestLoggingFilter(new LoggerProperties("X-Request-Id", true, List.of()));
  private final Logger filterLogger =
      (Logger) LoggerFactory.getLogger(ReactiveRequestLoggingFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachAppender() {
    appender.start();
    filterLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    filterLogger.detachAppender(appender);
  }

  @Test
  void thatFailedRequestIsLoggedAndItsErrorPassedOn() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/actions"));
    var error = new ResponseStatusException(HttpStatus.NOT_FOUND);

    StepVerifier.create(filter.filter(exchange, ex -> Mono.error(error)))
        .expectErrorSatisfies(ex -> assertThat(ex).isSameAs(error))
        .verify();

    ILoggingEvent failed = appender.list.getLast();
    assertThat(failed.getFormattedMessage()).isEqualTo("FAILED: 404 NOT_FOUND");
    assertThat(failed.getMDCPropertyMap()).containsEntry("status", "ERROR");
  }

  @Test
  void thatCompletedRequestIsLoggedWithItsStatus() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/actions"));

    StepVerifier.create(
            filter.filter(
                exchange,
                ex -> {
                  ex.getResponse().setStatusCode(HttpStatus.OK);
                  return Mono.empty();
                }))
        .verifyComplete();

    ILoggingEvent finished = appender.list.getLast();
    assertThat(finished.getFormattedMessage()).isEqualTo("FINISHED");
    assertThat(finished.getMDCPropertyMap())
        .containsEntry("status", "COMPLETE")
        .containsEntry("httpStatus", "200");
  }
}
