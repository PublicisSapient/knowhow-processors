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

import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.kpibenchmark.KpiBenchmarkValuesRepository;

class KpiBenchmarkValuesPersistentServiceImplTest {

	@Mock private KpiBenchmarkValuesRepository repository;

	private KpiBenchmarkValuesPersistentServiceImpl service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new KpiBenchmarkValuesPersistentServiceImpl(repository);
	}

	@Test
	void testSaveKpiBenchmarkValues_NewRecord() {
		KpiBenchmarkValues newValue = createKpiBenchmarkValues("kpi1");

		when(repository.findByKpiId("kpi1")).thenReturn(Optional.empty());

		service.saveKpiBenchmarkValues(newValue);

		verify(repository).findByKpiId("kpi1");
		verify(repository).save(newValue);
	}

	@Test
	void testSaveKpiBenchmarkValues_ExistingRecord() {
		KpiBenchmarkValues existingValue = createKpiBenchmarkValues("kpi1");
		existingValue.setLastUpdatedTimestamp(1000L);

		KpiBenchmarkValues newValue = createKpiBenchmarkValues("kpi1");
		newValue.setLastUpdatedTimestamp(2000L);

		when(repository.findByKpiId("kpi1")).thenReturn(Optional.of(existingValue));

		service.saveKpiBenchmarkValues(newValue);

		verify(repository).findByKpiId("kpi1");
		verify(repository).save(newValue);

	}

	private KpiBenchmarkValues createKpiBenchmarkValues(String kpiId) {
		BenchmarkPercentiles percentiles =
				BenchmarkPercentiles.builder()
						.filter("Overall")
						.seventyPercentile(70.0)
						.eightyPercentile(80.0)
						.nintyPercentile(90.0)
						.build();

		return KpiBenchmarkValues.builder()
				.kpiId(kpiId)
				.filterWiseBenchmarkValues(Arrays.asList(percentiles))
				.lastUpdatedTimestamp(System.currentTimeMillis())
				.build();
	}
}
