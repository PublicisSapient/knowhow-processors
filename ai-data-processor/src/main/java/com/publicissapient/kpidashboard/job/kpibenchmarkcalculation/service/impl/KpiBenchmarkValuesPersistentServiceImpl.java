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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.kpibenchmark.KpiBenchmarkValuesRepository;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiBenchmarkValuesPersistentService;

/**
 * Implementation of KpiBenchmarkValuesPersistentService for database operations. Handles saving and
 * updating of KPI benchmark values with upsert logic to maintain the latest benchmark calculations.
 *
 * @author kunkambl
 */
@Service
public class KpiBenchmarkValuesPersistentServiceImpl
		implements KpiBenchmarkValuesPersistentService {

	private final KpiBenchmarkValuesRepository repository;

	/**
	 * Constructs the persistent service with repository dependency.
	 *
	 * @param repository repository for KPI benchmark values operations
	 */
	public KpiBenchmarkValuesPersistentServiceImpl(KpiBenchmarkValuesRepository repository) {
		this.repository = repository;
	}

	/** {@inheritDoc} */
	public void saveKpiBenchmarkValues(List<KpiBenchmarkValues> kpiBenchmarkValuesList) {
		kpiBenchmarkValuesList.forEach(
				kpiBenchmarkValues -> {
					if (kpiBenchmarkValues != null) {
						Optional<KpiBenchmarkValues> existing =
								repository.findByKpiId(kpiBenchmarkValues.getKpiId());
						if (existing.isPresent()) {
							KpiBenchmarkValues existingValue = existing.get();
							kpiBenchmarkValues.setId(existingValue.getId());
						}
						repository.save(kpiBenchmarkValues);
					}
				});
	}
}
