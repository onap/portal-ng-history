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

package org.onap.portal.history.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configures the access control of the API endpoints.
 */
// https://hantsy.github.io/spring-reactive-sample/security/config.html
@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain springSecurityWebFilterChain(ServerHttpSecurity http) {
    return http.httpBasic().disable()
        .formLogin().disable()
        .csrf().disable()
        .cors()
        .and()
        .authorizeExchange()
        .pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
        .anyExchange().authenticated()
        .and()
        .oauth2ResourceServer(ServerHttpSecurity.OAuth2ResourceServerSpec::jwt)
        .build();
  }
}
