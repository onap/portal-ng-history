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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * Filters out observations (and therefore the Zipkin spans derived from them) for actuator
 * endpoints, so that Kubernetes liveness/readiness probes and metric scrapes do not flood the
 * tracing backend with noise.
 *
 * <p>An {@link ObservationPredicate} is the supported way to suppress observations by request path:
 * observations are only reported when every registered predicate returns {@code true}. This is not
 * new to Spring Boot 4 — the mechanism has existed since Boot 3.0 / Micrometer 1.10 — but the
 * property-based {@code management.observations.enable.*} toggles cannot help here because all HTTP
 * server requests share the single observation name {@code http.server.requests}, so they can only
 * be distinguished by inspecting the request path at runtime.
 */
@Configuration
public class ObservationConfig {

  /** Actuator base path; every probe/metric endpoint sits underneath it. */
  static final String ACTUATOR_PATH_PREFIX = "/actuator";

  @Bean
  public ObservationPredicate noActuatorServerObservations() {
    return ObservationConfig::shouldObserve;
  }

  /**
   * Decides whether a given observation should be reported. Extracted as a plain static method so
   * the rule can be unit-tested without a Spring context, keeping the {@link ObservationPredicate}
   * bean a thin adapter over it.
   *
   * @param name the observation name (unused for path filtering, kept for parity with the SPI)
   * @param context the observation context
   * @return {@code true} if the observation should be reported, {@code false} to suppress it
   */
  static boolean shouldObserve(String name, Observation.Context context) {
    if (context instanceof ServerRequestObservationContext serverContext) {
      // Suppress spans for actuator traffic (liveness/readiness probes, metric scrapes).
      return !serverContext.getCarrier().getPath().value().startsWith(ACTUATOR_PATH_PREFIX);
    }
    // Keep every non-HTTP-server observation (JPA, downstream clients, etc.).
    return true;
  }
}
