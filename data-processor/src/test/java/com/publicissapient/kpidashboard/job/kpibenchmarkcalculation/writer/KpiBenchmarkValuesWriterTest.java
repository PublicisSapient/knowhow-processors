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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.writer;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.item.Chunk;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.kpibenchmark.KpiBenchmarkValuesRepository;

class KpiBenchmarkValuesWriterTest {

	@Mock private KpiBenchmarkValuesRepository persistentService;

	private KpiBenchmarkValuesWriter writer;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		writer = new KpiBenchmarkValuesWriter(persistentService);
	}

	@Test
	void testWrite_WithValidChunk() throws Exception {
		BenchmarkPercentiles percentiles =
				BenchmarkPercentiles.builder()
						.filter("Overall")
						.seventyPercentile(70.0)
						.eightyPercentile(80.0)
						.nintyPercentile(90.0)
						.build();

		KpiBenchmarkValues values1 =
				KpiBenchmarkValues.builder()
						.kpiId("kpi1")
						.filterWiseBenchmarkValues(Arrays.asList(percentiles))
						.calculationDate(Instant.now())
						.build();

		KpiBenchmarkValues values2 =
				KpiBenchmarkValues.builder()
						.kpiId("kpi2")
						.filterWiseBenchmarkValues(Arrays.asList(percentiles))
						.calculationDate(Instant.now())
						.build();

		Chunk<KpiBenchmarkValues> chunk = new Chunk<>(Arrays.asList(values1, values2));

		writer.write(chunk);

		verify(persistentService).saveAll(Arrays.asList(values1, values2));
	}
}
