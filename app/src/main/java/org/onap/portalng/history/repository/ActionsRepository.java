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

package org.onap.portalng.history.repository;

import java.util.Date;

import org.onap.portalng.history.entities.ActionsDao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ActionsRepository extends ReactiveMongoRepository<ActionsDao, String> {

  Flux<ActionsDao> findAllByActionCreatedAtAfter(Pageable pageable, Date actionCreatedAt);

  Flux<ActionsDao> findAllByUserIdAndActionCreatedAtAfter(Pageable pageable, String userId, Date actionCreatedAt);

  Mono<Long> deleteAllByUserIdAndActionCreatedAtIsBefore(String userId, Date actionCreatedAt);

  Mono<Long> deleteAllByActionCreatedAtIsBefore(Date actionCreatedAt);
}
