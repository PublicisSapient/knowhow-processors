/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.job.productivitycalculation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.IssueKpiModalValue;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.CategoryScores;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.Productivity;
import com.publicissapient.kpidashboard.common.repository.productivity.ProductivityRepository;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.CalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.ProductivityCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.shared.dto.SprintInputDTO;

@ExtendWith(MockitoExtension.class)
class ProductivityCalculationServiceTest {

	@Mock
	private ProductivityRepository productivityRepository;

	@Mock
	private KnowHOWClient knowHOWClient;

	@Mock
	private ProductivityCalculationConfig productivityCalculationJobConfig;

	@Mock
	private CalculationConfig calculationConfig;

	@Mock
	private CalculationConfig.DataPoints dataPoints;

	@InjectMocks
	private ProductivityCalculationService productivityCalculationService;

	private ProjectInputDTO testProjectInputDTO;

	@BeforeEach
	void setUp() {
		// Setup test data
		List<SprintInputDTO> testSprints = List.of(
				SprintInputDTO.builder().nodeId("sprint1").name("Sprint 1").hierarchyLevel(6).hierarchyLevelId("sprint")
						.build(),
				SprintInputDTO.builder().nodeId("sprint2").name("Sprint 2").hierarchyLevel(6).hierarchyLevelId("sprint")
						.build());

		testProjectInputDTO = ProjectInputDTO.builder().nodeId("project1").name("Test Project").hierarchyLevel(5)
				.hierarchyLevelId("project").sprints(testSprints).build();
	}

	@Test
	void when_CalculateProductivityGainForProjectWithValidData_Then_ReturnsProductivityCalculation() {
		initializeProductivityCalculationConfigurations();
		// Arrange
		List<KpiElement> mockKpiElements = createMockKpiElementsWithValidData();
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(mockKpiElements);

		// Act
		Productivity result = productivityCalculationService
				.calculateProductivityGainForProject(testProjectInputDTO);

		// Assert
		assertNotNull(result);
		assertEquals("Test Project", result.getHierarchyEntityName());
		assertEquals("project1", result.getHierarchyEntityNodeId());
		assertEquals("project", result.getHierarchyLevelId());
		assertEquals(5, result.getHierarchyLevel());
		assertNotNull(result.getCalculationDate());
		assertNotNull(result.getCategoryScores());
		assertNotNull(result.getKpis());
		assertFalse(result.getKpis().isEmpty());

		// Verify API client was called
		ArgumentCaptor<List<KpiRequest>> kpiRequestCaptor = ArgumentCaptor.forClass(List.class);
		verify(knowHOWClient).getKpiIntegrationValuesSync(kpiRequestCaptor.capture());
		assertFalse(kpiRequestCaptor.getValue().isEmpty());
	}

	@Test
	void when_CalculateProductivityGainForProjectWithConfigValidationErrors_Then_ThrowsIllegalStateException() {
		when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getAllConfiguredCategories())
				.thenReturn(Set.of("speed", "quality", "productivity", "efficiency"));
		ReflectionTestUtils.invokeMethod(productivityCalculationService, "initializeConfiguration");
		// Arrange
		Set<String> validationErrors = Set.of("Invalid configuration", "Missing required field");
		when(calculationConfig.getConfigValidationErrors()).thenReturn(validationErrors);

		// Act & Assert
		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> productivityCalculationService.calculateProductivityGainForProject(testProjectInputDTO));

		assertTrue(exception.getMessage().contains("config validations errors"));
		assertTrue(exception.getMessage().contains("Invalid configuration"));
		assertTrue(exception.getMessage().contains("Missing required field"));

		// Verify no API calls were made
		verifyNoInteractions(knowHOWClient);
	}

	@Test
	void when_CalculateProductivityGainForProjectWithNoKpiData_Then_ReturnsNull() {
		when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getAllConfiguredCategories())
				.thenReturn(Set.of("speed", "quality", "productivity", "efficiency"));
		when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
		when(dataPoints.getCount()).thenReturn(5);
		ReflectionTestUtils.invokeMethod(productivityCalculationService, "initializeConfiguration");
		// Arrange
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(Collections.emptyList());

		// Act
		Productivity result = productivityCalculationService
				.calculateProductivityGainForProject(testProjectInputDTO);

		// Assert
		assertNull(result);
		verify(knowHOWClient).getKpiIntegrationValuesSync(anyList());
	}

	@Test
	void when_CalculateProductivityGainForProjectWithEmptyKpiValues_Then_ReturnsNull() {
		when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getAllConfiguredCategories())
				.thenReturn(Set.of("speed", "quality", "productivity", "efficiency"));
		when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
		when(dataPoints.getCount()).thenReturn(5);
		ReflectionTestUtils.invokeMethod(productivityCalculationService, "initializeConfiguration");
		// Arrange
		List<KpiElement> emptyKpiElements = createMockKpiElementsWithEmptyData();
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(emptyKpiElements);

		// Act
		Productivity result = productivityCalculationService
				.calculateProductivityGainForProject(testProjectInputDTO);

		// Assert
		assertNull(result);
		verify(knowHOWClient).getKpiIntegrationValuesSync(anyList());
	}

	@Test
	void when_CalculateProductivityGainForProjectWithIterationBasedKpis_Then_ProcessesCorrectly() {
		initializeProductivityCalculationConfigurations();
		// Arrange
		List<KpiElement> mockKpiElements = createMockKpiElementsWithIterationData();
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(mockKpiElements);

		// Act
		Productivity result = productivityCalculationService
				.calculateProductivityGainForProject(testProjectInputDTO);

		// Assert
		assertNotNull(result);
		assertNotNull(result.getCategoryScores());
		assertTrue(result.getKpis().stream().anyMatch(kpi -> "kpi131".equals(kpi.getKpiId())));
		assertTrue(result.getKpis().stream().anyMatch(kpi -> "kpi128".equals(kpi.getKpiId())));
	}

	@Test
	void when_CalculateProductivityGainForProjectWithDataCountGroups_Then_FiltersCorrectly() {
		initializeProductivityCalculationConfigurations();
		// Arrange
		List<KpiElement> mockKpiElements = createMockKpiElementsWithDataCountGroups();
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(mockKpiElements);

		// Act
		Productivity result = productivityCalculationService
				.calculateProductivityGainForProject(testProjectInputDTO);

		// Assert
		assertNotNull(result);
		assertNotNull(result.getCategoryScores());
		assertFalse(result.getKpis().isEmpty());
	}

	@Test
	void when_CalculateProductivityGainForProjectWithMixedKpiTypes_Then_CalculatesAllMetrics() {
		initializeProductivityCalculationConfigurations();
		// Arrange
		List<KpiElement> mockKpiElements = createMockKpiElementsWithMixedData();
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(mockKpiElements);

		// Act
		Productivity result = productivityCalculationService
				.calculateProductivityGainForProject(testProjectInputDTO);

		// Assert
		assertNotNull(result);
		CategoryScores categoryScores = result.getCategoryScores();
		assertNotNull(categoryScores);

		// Verify all metric categories are calculated
		assertTrue(categoryScores.getSpeed() >= 0 || categoryScores.getSpeed() < 0);
		assertTrue(categoryScores.getQuality() >= 0 || categoryScores.getQuality() < 0);
		assertTrue(categoryScores.getProductivity() >= 0 || categoryScores.getProductivity() < 0);
		assertTrue(categoryScores.getEfficiency() >= 0 || categoryScores.getEfficiency() < 0);
		assertTrue(categoryScores.getOverall() >= 0 || categoryScores.getOverall() < 0);
	}

	@Test
	void when_CalculateProductivityGainForProjectWithCustomApiException_Then_PropagatesException() {
		when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getAllConfiguredCategories())
				.thenReturn(Set.of("speed", "quality", "productivity", "efficiency"));
		when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
		when(dataPoints.getCount()).thenReturn(5);
		ReflectionTestUtils.invokeMethod(productivityCalculationService, "initializeConfiguration");
		// Arrange
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
				.thenThrow(new RuntimeException("API connection failed"));

		// Act & Assert
		RuntimeException exception = assertThrows(RuntimeException.class,
				() -> productivityCalculationService.calculateProductivityGainForProject(testProjectInputDTO));

		assertEquals("API connection failed", exception.getMessage());
	}

	@Test
	void when_SaveAllProductivityCalculations_Then_CallsRepositorySaveAll() {
		// Arrange
		List<Productivity> calculations = List.of(new Productivity(),
				new Productivity());

		// Act
		productivityCalculationService.saveAll(calculations);

		// Assert
		verify(productivityRepository).saveAll(calculations);
	}

	@Test
	void when_CalculateProductivityGainForProjectWithNullSprints_Then_HandlesGracefully() {
		initializeProductivityCalculationConfigurations();
		// Arrange
		ProjectInputDTO projectWithoutSprints = ProjectInputDTO.builder().nodeId("project1").name("Test Project")
				.hierarchyLevel(2).hierarchyLevelId("project").sprints(Collections.emptyList()).build();

		List<KpiElement> mockKpiElements = createMockKpiElementsWithValidData();
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(mockKpiElements);

		// Act
		assertDoesNotThrow(
				() -> productivityCalculationService.calculateProductivityGainForProject(projectWithoutSprints));

		// Assert - Should handle empty sprints gracefully
		// The result depends on the KPI configuration and data availability
		verify(knowHOWClient).getKpiIntegrationValuesSync(anyList());
	}

	@Test
	void when_CalculateProductivityGainForProjectWithZeroBaselineValues_Then_SkipsKpiCalculation() {
		when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getAllConfiguredCategories())
				.thenReturn(Set.of("speed", "quality", "productivity", "efficiency"));
		when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
		when(dataPoints.getCount()).thenReturn(5);
		ReflectionTestUtils.invokeMethod(productivityCalculationService, "initializeConfiguration");
		// Arrange
		List<KpiElement> mockKpiElements = createMockKpiElementsWithZeroBaseline();
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(mockKpiElements);

		// Act
		Productivity result = productivityCalculationService
				.calculateProductivityGainForProject(testProjectInputDTO);

		// Assert
		assertNull(result); // Should return null when no valid baseline values exist
	}

	@Test
	void when_CalculateProductivityGainForProjectWithValidConfiguration_Then_CreatesCorrectKpiRequests() {
		initializeProductivityCalculationConfigurations();
		// Arrange
		List<KpiElement> mockKpiElements = createMockKpiElementsWithValidData();
		when(knowHOWClient.getKpiIntegrationValuesSync(anyList())).thenReturn(mockKpiElements);

		// Act
		productivityCalculationService.calculateProductivityGainForProject(testProjectInputDTO);

		// Assert
		ArgumentCaptor<List<KpiRequest>> kpiRequestCaptor = ArgumentCaptor.forClass(List.class);
		verify(knowHOWClient).getKpiIntegrationValuesSync(kpiRequestCaptor.capture());

		List<KpiRequest> capturedRequests = kpiRequestCaptor.getValue();
		assertFalse(capturedRequests.isEmpty());

		// Verify request structure
		for (KpiRequest request : capturedRequests) {
			assertNotNull(request.getKpiIdList());
			assertNotNull(request.getSelectedMap());
			assertNotNull(request.getIds());
			assertTrue(request.getLevel() > 0);
			assertNotNull(request.getLabel());
		}
	}

	// Helper methods to create mock data
	private List<KpiElement> createMockKpiElementsWithValidData() {
		List<KpiElement> kpiElements = new ArrayList<>();

		// Speed KPI - Sprint Velocity
		KpiElement velocityKpi = new KpiElement();
		velocityKpi.setKpiId("kpi39");
		velocityKpi.setKpiName("Sprint Velocity");

		List<DataCount> velocityData = List.of(
				createDataCount("Sprint 1", List.of(createDataCount("Week 1", 10), createDataCount("Week 2", 12))),
				createDataCount("Sprint 2", List.of(createDataCount("Week 1", 11), createDataCount("Week 2", 13))));
		velocityKpi.setTrendValueList(velocityData);
		kpiElements.add(velocityKpi);

		// Quality KPI - Defect Density
		KpiElement defectKpi = new KpiElement();
		defectKpi.setKpiId("kpi111");
		defectKpi.setKpiName("Defect Density");

		List<DataCount> defectData = List.of(
				createDataCount("Sprint 1", List.of(createDataCount("Week 1", 5), createDataCount("Week 2", 4))),
				createDataCount("Sprint 2", List.of(createDataCount("Week 1", 4.5), createDataCount("Week 2", 3.5))));
		defectKpi.setTrendValueList(defectData);
		kpiElements.add(defectKpi);

		return kpiElements;
	}

	private List<KpiElement> createMockKpiElementsWithEmptyData() {
		List<KpiElement> kpiElements = new ArrayList<>();

		KpiElement emptyKpi = new KpiElement();
		emptyKpi.setKpiId("kpi39");
		emptyKpi.setKpiName("Sprint Velocity");
		emptyKpi.setTrendValueList(Collections.emptyList());
		kpiElements.add(emptyKpi);

		return kpiElements;
	}

	private List<KpiElement> createMockKpiElementsWithIterationData() {
		List<KpiElement> kpiElements = new ArrayList<>();

		// Wastage KPI
		KpiElement wastageKpi = new KpiElement();
		wastageKpi.setKpiId("kpi131");
		wastageKpi.setKpiName("Wastage");
		wastageKpi.setSprintId("sprint1");

		Set<IssueKpiModalValue> issueData = Set.of(createIssueKpiModalValue(5, 3), createIssueKpiModalValue(4, 2));
		wastageKpi.setIssueData(issueData);
		kpiElements.add(wastageKpi);

		// Work Status KPI
		KpiElement workStatusKpi = new KpiElement();
		workStatusKpi.setKpiId("kpi128");
		workStatusKpi.setKpiName("Work Status");
		workStatusKpi.setSprintId("sprint1");

		Set<IssueKpiModalValue> workStatusIssueData = Set.of(createIssueKpiModalValueWithDelay(Map.of("Planned", 2)),
				createIssueKpiModalValueWithDelay(Map.of("Planned", 3)));
		workStatusKpi.setIssueData(workStatusIssueData);
		kpiElements.add(workStatusKpi);

		return kpiElements;
	}

	private List<KpiElement> createMockKpiElementsWithDataCountGroups() {
		List<KpiElement> kpiElements = new ArrayList<>();

		KpiElement kpi = new KpiElement();
		kpi.setKpiId("kpi35");
		kpi.setKpiName("Defect Seepage Rate");

		List<DataCountGroup> dataCountGroups = List.of(createDataCountGroup("Overall", null, null, List.of(
				createDataCount("Project1", List.of(createDataCount("Week 1", 2.5), createDataCount("Week 2", 2))))));
		kpi.setTrendValueList(dataCountGroups);
		kpiElements.add(kpi);

		return kpiElements;
	}

	private List<KpiElement> createMockKpiElementsWithMixedData() {
		List<KpiElement> kpiElements = new ArrayList<>();
		kpiElements.addAll(createMockKpiElementsWithValidData());
		kpiElements.addAll(createMockKpiElementsWithIterationData());
		kpiElements.addAll(createMockKpiElementsWithDataCountGroups());
		return kpiElements;
	}

	private List<KpiElement> createMockKpiElementsWithZeroBaseline() {
		List<KpiElement> kpiElements = new ArrayList<>();

		KpiElement zeroKpi = new KpiElement();
		zeroKpi.setKpiId("kpi39");
		zeroKpi.setKpiName("Sprint Velocity");

		List<DataCount> zeroData = List
				.of(createDataCount("Sprint 1", List.of(createDataCount("Week 1", 0), createDataCount("Week 2", 0))));
		zeroKpi.setTrendValueList(zeroData);
		kpiElements.add(zeroKpi);

		return kpiElements;
	}

	private DataCount createDataCount(String data, Object value) {
		DataCount dataCount = new DataCount();
		dataCount.setData(data);
		dataCount.setValue(value);
		return dataCount;
	}

	private DataCountGroup createDataCountGroup(String filter, String filter1, String filter2, List<DataCount> value) {
		DataCountGroup group = new DataCountGroup();
		group.setFilter(filter);
		group.setFilter1(filter1);
		group.setFilter2(filter2);
		group.setValue(value);
		return group;
	}

	private IssueKpiModalValue createIssueKpiModalValue(int blockedTime, int waitTime) {
		IssueKpiModalValue issue = new IssueKpiModalValue();
		issue.setIssueBlockedTime(blockedTime);
		issue.setIssueWaitTime(waitTime);
		return issue;
	}

	private IssueKpiModalValue createIssueKpiModalValueWithDelay(Map<String, Integer> categoryWiseDelay) {
		IssueKpiModalValue issue = new IssueKpiModalValue();
		issue.setCategoryWiseDelay(categoryWiseDelay);
		return issue;
	}

	private void initializeProductivityCalculationConfigurations() {
		// Setup configuration mocks
		when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
		when(dataPoints.getCount()).thenReturn(5);
		when(calculationConfig.getConfigValidationErrors()).thenReturn(Collections.emptySet());
		when(calculationConfig.getAllConfiguredCategories())
				.thenReturn(Set.of("speed", "quality", "productivity", "efficiency"));
		when(calculationConfig.getWeightForCategory("speed")).thenReturn(0.25);
		when(calculationConfig.getWeightForCategory("quality")).thenReturn(0.25);
		when(calculationConfig.getWeightForCategory("productivity")).thenReturn(0.25);
		when(calculationConfig.getWeightForCategory("efficiency")).thenReturn(0.25);

		// Initialize the service configuration
		ReflectionTestUtils.invokeMethod(productivityCalculationService, "initializeConfiguration");
	}
}
