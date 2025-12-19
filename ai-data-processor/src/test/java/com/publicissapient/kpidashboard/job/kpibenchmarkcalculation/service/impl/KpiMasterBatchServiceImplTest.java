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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.repository.application.KpiMasterRepository;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

class KpiMasterBatchServiceImplTest {

	@Mock private KpiMasterRepository kpiMasterRepository;

	private KpiMasterBatchServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		service = new KpiMasterBatchServiceImpl(kpiMasterRepository);

		// Call private @PostConstruct method using reflection
		Method initMethod =
				KpiMasterBatchServiceImpl.class.getDeclaredMethod("initializeBatchProcessingParameters");
		initMethod.setAccessible(true);
		initMethod.invoke(service);
	}

	@Test
	void testGetNextKpiDataBatch_FirstCall() {
		KpiMaster kpiMaster1 = createKpiMaster("kpi1", "KPI 1", "line", "dropdown");
		KpiMaster kpiMaster2 = createKpiMaster("kpi2", "KPI 2", "grouped_column_plus_line", null);
		KpiMaster kpiMaster3 = createKpiMaster("kpi3", "KPI 3", "bar", "radiobutton");

		when(kpiMasterRepository.count()).thenReturn(3L);
		when(kpiMasterRepository.findAll())
				.thenReturn(Arrays.asList(kpiMaster1, kpiMaster2, kpiMaster3));

		KpiDataDTO result = service.getNextKpiData();

		assertNotNull(result);
		assertEquals("kpi1", result.kpiId());
	}

	@Test
	void testGetNextKpiDataBatch_SecondCall() {
		KpiMaster kpiMaster = createKpiMaster("kpi1", "KPI 1", "line", "dropdown");
		when(kpiMasterRepository.count()).thenReturn(1L);
		when(kpiMasterRepository.findAll()).thenReturn(Arrays.asList(kpiMaster));

		// First call
		service.getNextKpiData();

		// Second call should return null
		KpiDataDTO result = service.getNextKpiData();
		assertNull(result);
	}

	@Test
	void testGetNextKpiDataBatch_EmptyRepository() {
		when(kpiMasterRepository.count()).thenReturn(0L);
		when(kpiMasterRepository.findAll()).thenReturn(Collections.emptyList());

		KpiDataDTO result = service.getNextKpiData();
		assertNull(result);
	}

	@Test
	void testGetNextKpiDataBatch_OnlyUnsupportedChartTypes() {
		KpiMaster kpiMaster = createKpiMaster("kpi1", "KPI 1", "pie", "dropdown");
		when(kpiMasterRepository.count()).thenReturn(1L);
		when(kpiMasterRepository.findAll()).thenReturn(Arrays.asList(kpiMaster));

		KpiDataDTO result = service.getNextKpiData();
		assertNull(result);
	}

	@Test
	void testGetNextKpiDataBatch_ExceptionHandling() {
		when(kpiMasterRepository.count()).thenThrow(new RuntimeException("Database error"));

		KpiDataDTO result = service.getNextKpiData();
		assertNull(result);
	}

	@Test
	void testConvertToKpiData() {
		KpiMaster kpiMaster = createKpiMaster("kpi1", "KPI 1", "line", "dropdown");
		when(kpiMasterRepository.count()).thenReturn(1L);
		when(kpiMasterRepository.findAll()).thenReturn(Arrays.asList(kpiMaster));

		KpiDataDTO result = service.getNextKpiData();

		assertNotNull(result);
		assertEquals("kpi1", result.kpiId());
		assertEquals("KPI 1", result.kpiName());
		assertEquals("line", result.chartType());
		assertEquals("dropdown", result.kpiFilter());
	}

	private KpiMaster createKpiMaster(
			String kpiId, String kpiName, String chartType, String kpiFilter) {
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setKpiName(kpiName);
		kpiMaster.setChartType(chartType);
		kpiMaster.setKpiFilter(kpiFilter);
		return kpiMaster;
	}
}
