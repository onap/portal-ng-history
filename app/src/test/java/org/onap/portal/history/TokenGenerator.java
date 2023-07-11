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

package org.onap.portal.history;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class TokenGenerator {

  private static final String ROLES_CLAIM = "roles";
  private static final String USERID_CLAIM = "sub";

  private final Clock clock;
  private final RSAKey jwk;
  private final JWKSet jwkSet;
  private final JWSSigner signer;

  @Autowired
  public TokenGenerator(Clock clock) {
    try {
      this.clock = clock;
      jwk =
          new RSAKeyGenerator(2048)
              .keyUse(KeyUse.SIGNATURE)
              .keyID(UUID.randomUUID().toString())
              .generate();
      jwkSet = new JWKSet(jwk);
      signer = new RSASSASigner(jwk);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public JWKSet getJwkSet() {
    return jwkSet;
  }

  public String generateToken(TokenGeneratorConfig config) {
    final Instant iat = clock.instant();
    final Instant exp = iat.plus(config.expireIn);

    final JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .subject(UUID.randomUUID().toString())
            .issuer(config.issuer())
            .issueTime(Date.from(iat))
            .expirationTime(Date.from(exp))
            .claim(ROLES_CLAIM, config.getRoles())
            .claim(USERID_CLAIM, config.getSub())
            .build();

    final SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(jwk.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(),
            claims);

    try {
      jwt.sign(signer);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return jwt.serialize();
  }

  @Getter
  @Builder
  public static class TokenGeneratorConfig {
    private final int port;

    @NonNull private final String sub;

    @NonNull private final String realm;

    @NonNull @Builder.Default private final Duration expireIn = Duration.ofMinutes(5);

    @Builder.Default private final List<String> roles = Collections.emptyList();

    public String issuer() {
      return String.format("http://localhost:%d/auth/realms/%s", port, realm);
    }
  }
}
