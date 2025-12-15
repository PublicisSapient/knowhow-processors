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

package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl.KpiBenchmarkProcessorServiceImpl;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

class KpiBenchmarkProcessorTest {

	@Mock private KpiBenchmarkProcessorServiceImpl processorService;

	private KpiBenchmarkProcessor processor;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		processor = new KpiBenchmarkProcessor(processorService);
	}

	@Test
	void testProcess_WithValidInput() throws Exception {
		KpiDataDTO dto1 =
				KpiDataDTO.builder()
						.kpiId("kpi1")
						.kpiName("KPI 1")
						.chartType("line")
						.kpiFilter("dropdown")
						.build();

		KpiDataDTO dto2 =
				KpiDataDTO.builder()
						.kpiId("kpi2")
						.kpiName("KPI 2")
						.chartType("grouped_column_plus_line")
						.kpiFilter("radiobutton")
						.build();

		List<KpiDataDTO> inputList = Arrays.asList(dto1, dto2);

		BenchmarkPercentiles percentiles =
				BenchmarkPercentiles.builder()
						.filter("Overall")
						.seventyPercentile(70.0)
						.eightyPercentile(80.0)
						.nintyPercentile(90.0)
						.build();

		KpiBenchmarkValues benchmarkValues1 =
				KpiBenchmarkValues.builder()
						.kpiId("kpi1")
						.filterWiseBenchmarkValues(Arrays.asList(percentiles))
						.lastUpdatedTimestamp(System.currentTimeMillis())
						.build();

		KpiBenchmarkValues benchmarkValues2 =
				KpiBenchmarkValues.builder()
						.kpiId("kpi2")
						.filterWiseBenchmarkValues(Arrays.asList(percentiles))
						.lastUpdatedTimestamp(System.currentTimeMillis())
						.build();

		List<KpiBenchmarkValues> expectedResult = Arrays.asList(benchmarkValues1, benchmarkValues2);

		when(processorService.getKpiWiseBenchmarkValues(inputList)).thenReturn(expectedResult);

		List<KpiBenchmarkValues> result = processor.process(inputList);

		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("kpi1", result.get(0).getKpiId());
		assertEquals("kpi2", result.get(1).getKpiId());
		verify(processorService).getKpiWiseBenchmarkValues(inputList);
	}

	@Test
	void testProcess_WithEmptyInput() throws Exception {
		List<KpiDataDTO> emptyList = Collections.emptyList();
		List<KpiBenchmarkValues> expectedResult = Collections.emptyList();

		when(processorService.getKpiWiseBenchmarkValues(emptyList)).thenReturn(expectedResult);

		List<KpiBenchmarkValues> result = processor.process(emptyList);

		assertNotNull(result);
		assertTrue(result.isEmpty());
		verify(processorService).getKpiWiseBenchmarkValues(emptyList);
	}

	@Test
	void testProcess_ServiceThrowsException() {
		List<KpiDataDTO> inputList = Arrays.asList(KpiDataDTO.builder().kpiId("kpi1").build());

		when(processorService.getKpiWiseBenchmarkValues(inputList))
				.thenThrow(new RuntimeException("Processing error"));

		assertThrows(Exception.class, () -> processor.process(inputList));
		verify(processorService).getKpiWiseBenchmarkValues(inputList);
	}

	@Test
	void testProcess_WithNullResult() throws Exception {
		List<KpiDataDTO> inputList = Arrays.asList(KpiDataDTO.builder().kpiId("kpi1").build());

		when(processorService.getKpiWiseBenchmarkValues(inputList)).thenReturn(null);

		List<KpiBenchmarkValues> result = processor.process(inputList);

		assertNull(result);
		verify(processorService).getKpiWiseBenchmarkValues(inputList);
	}
}
