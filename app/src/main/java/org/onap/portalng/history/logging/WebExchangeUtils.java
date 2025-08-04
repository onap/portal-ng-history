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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WebExchangeUtils {
  private static final String DEFAULT_TRACE_ID = "REQUEST_ID_IS_NOT_SET";
  private static final String DEFAULT_REQUEST_URL = "REQUEST_URL_IS_ABSENT";
  private static final String DEFAULT_REQUEST_METHOD = "HTTP_METHOD_IS_ABSENT";

  private static final PathMatcher pathMatcher = new AntPathMatcher();

  public static String getRequestId(ServerWebExchange webExchange, String traceIdHeaderName) {
    if (webExchange == null || traceIdHeaderName == null) {
      return DEFAULT_TRACE_ID;
    }

    var requestIdHeaders = webExchange.getRequest().getHeaders().get(traceIdHeaderName);
    if (requestIdHeaders != null) {
      return requestIdHeaders.stream().findAny().orElse(DEFAULT_TRACE_ID);
    } else {
      return DEFAULT_TRACE_ID;
    }
  }

  public static String getRequestUrl(ServerWebExchange webExchange) {
    if (webExchange == null) {
      return DEFAULT_REQUEST_URL;
    }
    return webExchange.getRequest().getURI().toString();
  }

  public static String getRequestHttpMethod(ServerWebExchange webExchange) {
    if (webExchange == null) {
      return DEFAULT_REQUEST_METHOD;
    }
    return webExchange.getRequest().getMethod().name();
  }

  public static boolean matchUrlPatternToPath(String pattern, String path) {
    return pathMatcher.match(pattern, path);
  }

  public static boolean matchUrlsPatternsToPath(List<String> patterns, String path) {
    return patterns != null
        && patterns.stream().anyMatch(pathPattern -> matchUrlPatternToPath(pathPattern, path));
  }

  public static Map<LogContextVariable, String> getRequestMetadata(
      ServerWebExchange exchange, String traceIdHeaderName) {
    var traceId = WebExchangeUtils.getRequestId(exchange, traceIdHeaderName);
    var requestMethod = WebExchangeUtils.getRequestHttpMethod(exchange);
    var requestUrl = WebExchangeUtils.getRequestUrl(exchange);

    var logMessageMetadata = new EnumMap<LogContextVariable, String>(LogContextVariable.class);
    logMessageMetadata.put(LogContextVariable.TRACE_ID, traceId);
    logMessageMetadata.put(LogContextVariable.STATUS, StatusCode.REQUEST.name());
    logMessageMetadata.put(LogContextVariable.NORTHBOUND_METHOD, requestMethod);
    logMessageMetadata.put(LogContextVariable.NORTHBOUND_URL, requestUrl);

    return logMessageMetadata;
  }
}
