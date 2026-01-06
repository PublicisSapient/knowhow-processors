/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.kpibenchmark.KpiBenchmarkValuesRepository;

/**
 * Implementation of KpiBenchmarkValuesPersistentService for database operations. Handles saving and
 * updating of KPI benchmark values with upsert logic to maintain the latest benchmark calculations.
 *
 * @author kunkambl
 */
@Service
@RequiredArgsConstructor
public class KpiBenchmarkValuesPersistentService {

	private final KpiBenchmarkValuesRepository repository;

    /**
     * Saves or updates KPI benchmark values in the database. Performs upsert operations to maintain
     * current benchmark data.
     *
     * @param kpiBenchmarkValues list of benchmark values to persist
     */
	public void saveKpiBenchmarkValues(KpiBenchmarkValues kpiBenchmarkValues) {

		if (kpiBenchmarkValues != null) {
			Optional<KpiBenchmarkValues> existing = repository.findByKpiId(kpiBenchmarkValues.getKpiId());
            existing.ifPresent(existingValue -> kpiBenchmarkValues.setId(existingValue.getId()));
			repository.save(kpiBenchmarkValues);
		}
	}
}
