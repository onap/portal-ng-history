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

package org.onap.portalng.history.util;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Represents a function that handles the <a href="https://jwt.io/introduction">JWT</a> identity
 * token. Use this to check if the incoming requests are authorized to call the given endpoint
 */
public final class IdTokenExchange {

  public static final String JWT_CLAIM_USERID = "sub";

  private IdTokenExchange() {}

  /**
   * Extract the <code>userId</code> from the given {@link ServerWebExchange}
   *
   * @param exchange the ServerWebExchange that contains information about the incoming request
   * @return the id of the user
   */
  public static Mono<String> extractUserId(ServerWebExchange exchange) {
    return exchange
        .getPrincipal()
        .cast(JwtAuthenticationToken.class)
        .map(auth -> auth.getToken().getClaimAsString(JWT_CLAIM_USERID));
  }
}
