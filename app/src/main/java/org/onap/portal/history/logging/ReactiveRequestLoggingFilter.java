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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveRequestLoggingFilter implements WebFilter {

  private final LoggerProperties loggerProperties;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (loggingDisabled(exchange)) {
      return chain.filter(exchange);
    }

    var logMessageMetadata =
        WebExchangeUtils.getRequestMetadata(exchange, loggerProperties.traceIdHeaderName());

    LoggingHelper.info(log, logMessageMetadata, "RECEIVED");

    var invocationStart = LocalDateTime.now();
    return chain
        .filter(exchange)
        .doOnTerminate(
            () -> {
              logMessageMetadata.put(
                  LogContextVariable.STATUS,
                  exchange.getResponse().getStatusCode().isError()
                      ? StatusCode.ERROR.name()
                      : StatusCode.COMPLETE.name());
              logMessageMetadata.put(
                  LogContextVariable.HTTP_STATUS,
                  String.valueOf(exchange.getResponse().getStatusCode().value()));
              logMessageMetadata.put(
                  LogContextVariable.EXECUTION_TIME,
                  String.valueOf(
                      Duration.between(invocationStart, LocalDateTime.now()).toMillis()));
            })
        .doOnSuccess(res -> LoggingHelper.info(log, logMessageMetadata, "FINISHED"))
        .doOnError(
            ex -> LoggingHelper.warn(log, logMessageMetadata, "FAILED: {}", ex.getMessage()));
  }

  private boolean loggingDisabled(ServerWebExchange exchange) {
    boolean loggingDisabled = loggerProperties.enabled() == null || !loggerProperties.enabled();

    boolean urlShouldBeSkipped =
        WebExchangeUtils.matchUrlsPatternsToPath(loggerProperties.excludePaths(), exchange.getRequest().getPath().value());

    return loggingDisabled || urlShouldBeSkipped;
  }
}
