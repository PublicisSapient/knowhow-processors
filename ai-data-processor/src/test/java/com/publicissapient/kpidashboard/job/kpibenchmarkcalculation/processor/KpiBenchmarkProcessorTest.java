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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.impl.KpiBenchmarkProcessorServiceImpl;
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

		when(processorService.getKpiWiseBenchmarkValues(dto1)).thenReturn(benchmarkValues1);
        when(processorService.getKpiWiseBenchmarkValues(dto2)).thenReturn(benchmarkValues2);

		KpiBenchmarkValues result1 = processor.process(dto1);
        KpiBenchmarkValues result2 = processor.process(dto2);

		assertNotNull(result1);
        assertNotNull(result2);
		assertEquals("kpi1", result1.getKpiId());
		assertEquals("kpi2", result2.getKpiId());
		verify(processorService).getKpiWiseBenchmarkValues(dto1);
        verify(processorService).getKpiWiseBenchmarkValues(dto2);
	}

	@Test
	void testProcess_ServiceThrowsException() {
		KpiDataDTO input = KpiDataDTO.builder().kpiId("kpi1").build();

		when(processorService.getKpiWiseBenchmarkValues(input))
				.thenThrow(new RuntimeException("Processing error"));

		assertThrows(Exception.class, () -> processor.process(input));
		verify(processorService).getKpiWiseBenchmarkValues(input);
	}

	@Test
	void testProcess_WithNullResult() throws Exception {
		KpiDataDTO inputList = KpiDataDTO.builder().kpiId("kpi1").build();

		when(processorService.getKpiWiseBenchmarkValues(inputList)).thenReturn(null);

		KpiBenchmarkValues result = processor.process(inputList);

		assertNull(result);
		verify(processorService).getKpiWiseBenchmarkValues(inputList);
	}
}
