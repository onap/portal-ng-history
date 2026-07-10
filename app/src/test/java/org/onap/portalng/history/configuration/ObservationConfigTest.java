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

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

/**
 * Verifies that {@link ObservationConfig} suppresses observations for actuator endpoints (so that
 * liveness/readiness probes are not exported as Zipkin spans) while keeping every other
 * observation.
 */
class ObservationConfigTest {

  private final ObservationPredicate predicate =
      new ObservationConfig().noActuatorServerObservations();

  @Test
  void thatLivenessProbeIsNotObserved() {
    assertThat(observes("/actuator/health/liveness")).isFalse();
  }

  @Test
  void thatReadinessProbeIsNotObserved() {
    assertThat(observes("/actuator/health/readiness")).isFalse();
  }

  @Test
  void thatActuatorRootAndMetricsAreNotObserved() {
    assertThat(observes("/actuator")).isFalse();
    assertThat(observes("/actuator/prometheus")).isFalse();
  }

  @Test
  void thatBusinessEndpointIsObserved() {
    assertThat(observes("/v1/actions")).isTrue();
  }

  @Test
  void thatPathMerelyContainingActuatorIsObserved() {
    // Only paths *starting* with /actuator are suppressed; a business path that happens to
    // contain the word must still be traced.
    assertThat(observes("/v1/actuator-settings")).isTrue();
  }

  @Test
  void thatNonHttpObservationsAreAlwaysObserved() {
    // e.g. a JPA or downstream-client observation whose context is not a server request.
    assertThat(predicate.test("jpa.query", new Observation.Context())).isTrue();
  }

  private boolean observes(String path) {
    final MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
    final ServerRequestObservationContext context =
        new ServerRequestObservationContext(
            request, new MockServerHttpResponse(), request.getAttributes());
    return predicate.test("http.server.requests", context);
  }
}
