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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiBenchmarkCalculationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectReleaseRepo;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelServiceImpl;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.parser.KpiDataCountParser;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.parser.KpiParserStrategy;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

@ExtendWith(MockitoExtension.class)
class KpiBenchmarkCalculationServiceTest {

	@Mock private KpiParserStrategy kpiParserStrategy;
	@Mock private KnowHOWClient knowHOWClient;
	@Mock private ProjectBasicConfigRepository projectBasicConfigRepository;
	@Mock private HierarchyLevelServiceImpl hierarchyLevelServiceImpl;
	@Mock private SprintRepository sprintRepository;
	@Mock private ProjectReleaseRepo projectReleaseRepo;
	@Mock private KpiDataCountParser parser;
    @InjectMocks
    private KpiBenchmarkCalculationService service;

	@Test
	void testGetKpiWiseBenchmarkValues_WithValidData() {
		// Setup test data
		KpiDataDTO dto1 =
				KpiDataDTO.builder()
						.kpiId("kpi1")
						.kpiName("KPI 1")
						.chartType("line")
						.kpiFilter("dropdown")
                        .isPositiveTrend(true)
						.build();

		ProjectBasicConfig config = new ProjectBasicConfig();
		config.setProjectNodeId("project1");

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi1");
		kpiElement.setTrendValueList(Arrays.asList("data"));

		Map<String, List<Double>> dataPoints = new HashMap<>();
		dataPoints.put("value", Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0));

		// Setup mocks
		HierarchyLevel projectLevel = new HierarchyLevel();
		projectLevel.setLevel(5);
		projectLevel.setHierarchyLevelId("project");

		when(hierarchyLevelServiceImpl.getProjectHierarchyLevel()).thenReturn(projectLevel);
		when(projectBasicConfigRepository.findAll()).thenReturn(Arrays.asList(config));
		when(knowHOWClient.getKpiIntegrationValuesAsync(any())).thenReturn(Arrays.asList(kpiElement));
		when(kpiParserStrategy.getParser(anyString())).thenReturn(parser);
		when(parser.getKpiDataPoints(any())).thenReturn(dataPoints);

		// Execute
		KpiBenchmarkValues result = service.getKpiWiseBenchmarkValues(dto1);

		// Verify
		assertEquals("kpi1", result.getKpiId());
		assertNotNull(result.getFilterWiseBenchmarkValues());
	}

	@Test
	void testGetKpiWiseBenchmarkValues_WithEmptyProjects() {
		KpiDataDTO dto = KpiDataDTO.builder().kpiId("kpi1").isPositiveTrend(true).build();

		when(projectBasicConfigRepository.findAll()).thenReturn(Collections.emptyList());

		KpiBenchmarkValues result = service.getKpiWiseBenchmarkValues(dto);

		assertNotNull(result);
	}

	@Test
	void testGetKpiWiseBenchmarkValues_WithNullTrendValueList() {
		KpiDataDTO dto = KpiDataDTO.builder().kpiId("kpi1").kpiFilter("dropdown").isPositiveTrend(true).build();

		ProjectBasicConfig config = new ProjectBasicConfig();
		config.setProjectNodeId("project1");

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi1");
		kpiElement.setTrendValueList(null);

        // Setup mocks
        HierarchyLevel projectLevel = new HierarchyLevel();
        projectLevel.setLevel(5);
        projectLevel.setHierarchyLevelId("project");

        when(hierarchyLevelServiceImpl.getProjectHierarchyLevel()).thenReturn(projectLevel);
		when(projectBasicConfigRepository.findAll()).thenReturn(Arrays.asList(config));
		when(knowHOWClient.getKpiIntegrationValuesAsync(any())).thenReturn(Arrays.asList(kpiElement));

		KpiBenchmarkValues result = service.getKpiWiseBenchmarkValues(dto);

		assertNotNull(result);
		assertEquals("kpi1", result.getKpiId());
		assertTrue(result.getFilterWiseBenchmarkValues().isEmpty());
	}

}
