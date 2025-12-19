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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.reader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiMasterBatchService;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

class KpiItemReaderTest {

	@Mock private KpiMasterBatchService kpiMasterBatchService;

	private KpiItemReader reader;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		reader = new KpiItemReader(kpiMasterBatchService);
	}

	@Test
	void testRead_WithValidBatch() {
		KpiDataDTO dto1 =
				KpiDataDTO.builder()
						.kpiId("kpi1")
						.kpiName("KPI 1")
						.chartType("line")
						.kpiFilter("dropdown")
						.build();

		when(kpiMasterBatchService.getNextKpiData()).thenReturn(dto1);

		KpiDataDTO result = reader.read();

		assertNotNull(result);
		assertEquals("kpi1", result.kpiId());
		verify(kpiMasterBatchService).getNextKpiData();
	}


	@Test
	void testRead_ServiceThrowsException() {
		when(kpiMasterBatchService.getNextKpiData())
				.thenThrow(new RuntimeException("Service error"));

		assertThrows(RuntimeException.class, () -> reader.read());
		verify(kpiMasterBatchService).getNextKpiData();
	}
}
