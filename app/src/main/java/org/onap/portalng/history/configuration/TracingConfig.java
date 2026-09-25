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

package org.onap.portalng.history.configuration;

import io.micrometer.observation.ObservationPredicate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.util.AntPathMatcher;

@Configuration
public class TracingConfig {

  private static final List<String> UNTRACED_PATHS =
      List.of("/actuator/health/**", "/actuator/prometheus");

  @Bean
  ObservationPredicate untracedPathsPredicate(
      @Value("${history.tracing.untraced-paths-enabled:true}") boolean enabled) {
    AntPathMatcher pathMatcher = new AntPathMatcher();
    return (name, context) -> {
      if (!enabled || !(context instanceof ServerRequestObservationContext serverContext)) {
        return true;
      }
      String path = serverContext.getCarrier().getPath().pathWithinApplication().value();
      return UNTRACED_PATHS.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    };
  }
}
