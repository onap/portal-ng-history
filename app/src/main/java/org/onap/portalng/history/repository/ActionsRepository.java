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

import jakarta.transaction.Transactional;
import java.util.Date;
import java.util.List;
import org.onap.portalng.history.entities.ActionsDao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionsRepository extends JpaRepository<ActionsDao, String> {

  List<ActionsDao> findAllByActionCreatedAtAfter(Pageable pageable, Date actionCreatedAt);

  List<ActionsDao> findAllByUserIdAndActionCreatedAtAfter(
      Pageable pageable, String userId, Date actionCreatedAt);

  long deleteAllByUserIdAndActionCreatedAtIsBefore(String userId, Date actionCreatedAt);

  long deleteAllByActionCreatedAtIsBefore(Date actionCreatedAt);

  @Modifying
  @Transactional
  @Query(value = "TRUNCATE TABLE actions", nativeQuery = true)
  void truncateTable();
}
