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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.item.Chunk;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiBenchmarkValuesPersistentService;

class KpiBenchmarkValuesWriterTest {

	@Mock private KpiBenchmarkValuesPersistentService persistentService;

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
						.lastUpdatedTimestamp(System.currentTimeMillis())
						.build();

		KpiBenchmarkValues values2 =
				KpiBenchmarkValues.builder()
						.kpiId("kpi2")
						.filterWiseBenchmarkValues(Arrays.asList(percentiles))
						.lastUpdatedTimestamp(System.currentTimeMillis())
						.build();

		Chunk<KpiBenchmarkValues> chunk = new Chunk<>(Arrays.asList(values1, values2));

		writer.write(chunk);

		verify(persistentService).saveKpiBenchmarkValues(values1);
		verify(persistentService).saveKpiBenchmarkValues(values2);
	}

	@Test
	void testWrite_WithEmptyChunk() throws Exception {
		Chunk<KpiBenchmarkValues> emptyChunk = new Chunk<>(Collections.emptyList());

		writer.write(emptyChunk);

		verifyNoInteractions(persistentService);
	}

	@Test
	void testWrite_ServiceThrowsException() throws Exception {
		List<KpiBenchmarkValues> list =
				Arrays.asList(KpiBenchmarkValues.builder().kpiId("kpi1").build());

		Chunk<KpiBenchmarkValues> chunk = new Chunk<>(list);

		doThrow(new RuntimeException("Persistence error"))
				.when(persistentService)
				.saveKpiBenchmarkValues(KpiBenchmarkValues.builder().kpiId("kpi1").build());

		assertThrows(RuntimeException.class, () -> writer.write(chunk));
		verify(persistentService).saveKpiBenchmarkValues(KpiBenchmarkValues.builder().kpiId("kpi1").build());
	}

	@Test
	void testWrite_WithMultipleLists() throws Exception {
		KpiBenchmarkValues values1 = KpiBenchmarkValues.builder().kpiId("kpi1").build();
		KpiBenchmarkValues values2 = KpiBenchmarkValues.builder().kpiId("kpi2").build();
		KpiBenchmarkValues values3 = KpiBenchmarkValues.builder().kpiId("kpi3").build();

		List<KpiBenchmarkValues> list1 = Arrays.asList(values1, values2);
		List<KpiBenchmarkValues> list2 = Arrays.asList(values3);

		Chunk<KpiBenchmarkValues> chunk = new Chunk<>(Arrays.asList(values1, values2, values3));

		writer.write(chunk);

		verify(persistentService).saveKpiBenchmarkValues(values1);
		verify(persistentService).saveKpiBenchmarkValues(values2);
        verify(persistentService).saveKpiBenchmarkValues(values3);
		verifyNoMoreInteractions(persistentService);
	}
}
