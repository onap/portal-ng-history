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

package org.onap.portalng.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.jwk.JWKSet;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.onap.portalng.history.util.IdTokenExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

/** Base class for all tests that has the common config including port, realm, logging and auth. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
public abstract class BaseIntegrationTest {

//  @TestConfiguration
//  public static class Config {
//    @Bean
//    WireMockConfigurationCustomizer optionsCustomizer() {
//      return options -> options.extensions(new ResponseTemplateTransformer(true));
//    }
//  }

  @LocalServerPort protected int port;
  @Value("${history.realm}")
  protected String realm;

  @Value("${history.delete-interval}")
  protected String deleteInterval;

  @Autowired protected ObjectMapper objectMapper;
  @Autowired private TokenGenerator tokenGenerator;

  @BeforeAll
  public static void setup() {
    RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
  }

  /** Mocks the OIDC auth flow. */
  @BeforeEach
  public void mockAuth() {
    WireMock.reset();

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/realms/%s/protocol/openid-connect/certs", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", JWKSet.MIME_TYPE)
                    .withBody(tokenGenerator.getJwkSet().toString())));

    final TokenGenerator.TokenGeneratorConfig config =
        TokenGenerator.TokenGeneratorConfig.builder().port(port).realm(realm).sub("test-user").build();

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format("/realms/%s/protocol/openid-connect/token", realm)))
            .withBasicAuth("test", "test")
            .withRequestBody(WireMock.containing("grant_type=client_credentials"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        objectMapper
                            .createObjectNode()
                            .put("token_type", "bearer")
                            .put("access_token", tokenGenerator.generateToken(config))
                            .put("expires_in", config.getExpireIn().getSeconds())
                            .put("refresh_token", tokenGenerator.generateToken(config))
                            .put("refresh_expires_in", config.getExpireIn().getSeconds())
                            .put("not-before-policy", 0)
                            .put("session_state", UUID.randomUUID().toString())
                            .put("scope", "email profile")
                            .toString())));
  }

    /**
   * Builds an OAuth2 configuration including the roles, port and realm. This config can be used to
   * generate OAuth2 access tokens.
   *
   * @param sub the userId
   * @param roles the roles used for RBAC
   * @return the OAuth2 configuration
   */
  protected TokenGenerator.TokenGeneratorConfig getTokenGeneratorConfig(String sub, List<String> roles) {
    return TokenGenerator.TokenGeneratorConfig.builder()
        .port(port)
        .sub(sub)
        .realm(realm)
        .roles(roles)
        .build();
  }

  /** Get a RequestSpecification that does not have an Identity header. */
  protected RequestSpecification unauthenticatedRequestSpecification() {
    return RestAssured.given().port(port);
  }

  /**
   * Object to store common attributes of requests that are going to be made. Adds an Identity
   * header for the <code>onap_admin</code> role to the request.
   * @return the definition of the incoming request (northbound)
   */
  protected RequestSpecification requestSpecification() {
    final String idToken = tokenGenerator.generateToken(getTokenGeneratorConfig("test-user", List.of("foo")));

    return unauthenticatedRequestSpecification()
        .auth()
        .preemptive()
        .oauth2(idToken)
        .header(IdTokenExchange.X_AUTH_IDENTITY_HEADER, "Bearer " + idToken);
  }

  /**
   * Object to store common attributes of requests that are going to be made. Adds an Identity
   * header for the <code>onap_admin</code> role to the request.
   * @param userId the userId that should be contained in the incoming request
   * @return the definition of the incoming request (northbound)
   */
  protected RequestSpecification requestSpecification(String userId) {
    final String idToken = tokenGenerator.generateToken(getTokenGeneratorConfig(userId, List.of("foo")));

    return unauthenticatedRequestSpecification()
        .auth()
        .preemptive()
        .oauth2(idToken)
        .header(IdTokenExchange.X_AUTH_IDENTITY_HEADER, "Bearer " + idToken);
  }

  /**
   * Object to store common attributes of requests that are going to be made. Adds an Identity
   * header for the <code>onap_admin</code> role to the request.
   * @param userId the userId that should be contained in the incoming request
   * @return the definition of the incoming request (northbound)
   */
  protected RequestSpecification wrongHeaderRequestSpecification(String userId) {
    final String idToken = tokenGenerator.generateToken(getTokenGeneratorConfig(userId, List.of("foo")));

    return unauthenticatedRequestSpecification()
        .auth()
        .preemptive()
        .oauth2(idToken)
        .header("X-WRONG-HEADER", "Bearer " + idToken);
  }
}
