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

package com.publicissapient.kpidashboard.job.productivitycalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.client.customapi.CustomApiClient;
import com.publicissapient.kpidashboard.client.customapi.model.IssueKpiModalValue;
import com.publicissapient.kpidashboard.client.customapi.model.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.model.KpiRequest;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.ProductivityCalculation;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.ProductivityMetrics;
import com.publicissapient.kpidashboard.common.repository.productivity.calculation.ProductivityCalculationRepository;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.CalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.ProductivityCalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.productivitycalculation.dto.SprintInputDTO;

@ExtendWith(MockitoExtension.class)
class ProductivityCalculationServiceTest {

    @Mock
    private ProductivityCalculationRepository productivityCalculationRepository;

    @Mock
    private CustomApiClient customApiClient;

    @Mock
    private CalculationConfig calculationConfig;

    @Mock
    private ProductivityCalculationConfig productivityCalculationJobConfig;

    @InjectMocks
    private ProductivityCalculationService productivityCalculationService;

    @Test
    void when_ConfigValidationErrorsExistThen_ThrowsIllegalStateException() {
        // Arrange
        ProjectInputDTO projectInput = createMockProjectInput();
        Set<String> validationErrors = Set.of("Invalid configuration");

        when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
        when(calculationConfig.getConfigValidationErrors()).thenReturn(validationErrors);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            productivityCalculationService.calculateProductivityGainForProject(projectInput);
        });

        assertTrue(exception.getMessage().contains("config validations errors"));
    }

    @Test
    void when_DataCountTrendValuesThen_MapsValuesByDataPointsCorrectly() {
        // Arrange
        Object kpiConfig = createMockKPIConfiguration("kpi39", "SPRINTS");
        List<KpiElement> kpiElements = createKpiElementsWithDataCount();
        ProjectInputDTO projectInput = createMockProjectInput();

        // Act
        Map<Integer, List<Double>> result = ReflectionTestUtils.invokeMethod(
                productivityCalculationService, "constructKpiValuesByDataPointMap",
                kpiConfig, kpiElements, projectInput);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void when_DataCountGroupTrendValuesThen_FiltersAndMapsGroupValuesCorrectly() {
        // Arrange
        Object kpiConfig = createMockKPIConfiguration("kpi158", "WEEKS");
        List<KpiElement> kpiElements = createKpiElementsWithDataCountGroup();
        ProjectInputDTO projectInput = createMockProjectInput();

        // Act
        Map<Integer, List<Double>> result = ReflectionTestUtils.invokeMethod(
                productivityCalculationService, "constructKpiValuesByDataPointMap",
                kpiConfig, kpiElements, projectInput);

        // Assert
        assertNotNull(result);
    }

    @Test
    void when_IterationBasedKpiThen_ProcessesIterationKpiDataCorrectly() {
        // Arrange
        Object kpiConfig = createMockKPIConfiguration("kpi131", "ITERATION");
        List<KpiElement> kpiElements = createKpiElementsWithIssueData();
        ProjectInputDTO projectInput = createMockProjectInput();

        // Act
        Map<Integer, List<Double>> result = ReflectionTestUtils.invokeMethod(
                productivityCalculationService, "constructKpiValuesByDataPointMap",
                kpiConfig, kpiElements, projectInput);

        // Assert
        assertNotNull(result);
    }

    @Test
    void when_ValidCalculationDataThen_ReturnsCorrectCategorizedGain() {
        // Arrange
        List<Object> calculationData = createMockKPIVariationCalculationData();

        // Act
        Double result = ReflectionTestUtils.invokeMethod(
                productivityCalculationService, "calculateCategorizedGain", calculationData);

        // Assert
        assertNotNull(result);
        assertTrue(result >= 0.0);
    }

    @Test
    void when_EmptyCalculationDataThen_ReturnsZeroGain() {
        // Arrange
        List<Object> emptyCalculationData = Collections.emptyList();

        // Act
        Double result = ReflectionTestUtils.invokeMethod(
                productivityCalculationService, "calculateCategorizedGain", emptyCalculationData);

        // Assert
        assertEquals(0.0, result);
    }

    @Test
    void when_MatchingFiltersThen_ReturnsTrueForMatchingFilters() {
        // Arrange
        DataCountGroup dataCountGroup = new DataCountGroup();
        dataCountGroup.setFilter("Overall");

        Object kpiConfig = createMockKPIConfigurationWithFilter("Overall", null, null);

        // Act
        Boolean result = ReflectionTestUtils.invokeMethod(
                productivityCalculationService, "dataCountGroupMatchesFiltersSetForOverallProductivityGainCalculation",
                dataCountGroup, kpiConfig);

        // Assert
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void when_NonMatchingFiltersThen_ReturnsFalseForNonMatchingFilters() {
        // Arrange
        DataCountGroup dataCountGroup = new DataCountGroup();
        dataCountGroup.setFilter("Specific");

        Object kpiConfig = createMockKPIConfigurationWithFilter("Overall", null, null);

        // Act
        Boolean result = ReflectionTestUtils.invokeMethod(
                productivityCalculationService, "dataCountGroupMatchesFiltersSetForOverallProductivityGainCalculation",
                dataCountGroup, kpiConfig);

        // Assert
        assertNotEquals(Boolean.TRUE, result);
    }

    @Test
    void when_AllCategoriesConfiguredThen_CreatesCompleteConfigurationMap() {
        // Arrange
        when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
        when(calculationConfig.getAllConfiguredCategories()).thenReturn(
                Set.of("speed", "quality", "efficiency", "productivity"));

        // Act
        Map<String, Map<String, Object>> result =
                ReflectionTestUtils.invokeMethod(productivityCalculationService, "constructCategoryKpiIdConfigurationMap");

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size());
        assertTrue(result.containsKey("speed"));
        assertTrue(result.containsKey("quality"));
        assertTrue(result.containsKey("efficiency"));
        assertTrue(result.containsKey("productivity"));
    }

    @Test
    void when_ValidProductivityCalculationsThen_SavesAllCalculationsSuccessfully() {
        // Arrange
        List<ProductivityCalculation> calculations = Arrays.asList(
                createMockProductivityCalculation("project1"),
                createMockProductivityCalculation("project2")
        );

        // Act
        productivityCalculationService.saveAll(calculations);

        // Assert
        verify(productivityCalculationRepository).saveAll(calculations);
    }

    @Test
    void when_ValidKpiRequestsThen_ReturnsKpiElementsFromApi() {
        // Arrange
        List<KpiRequest> kpiRequests = Collections.singletonList(createMockKpiRequest());
        List<KpiElement> expectedElements = createMockKpiElements();

        when(customApiClient.getKpiIntegrationValues(kpiRequests)).thenReturn(expectedElements);

        // Act
        List<KpiElement> result = ReflectionTestUtils.invokeMethod(
                productivityCalculationService, "processAllKpiRequests", kpiRequests);

        // Assert
        assertEquals(expectedElements, result);
        verify(customApiClient).getKpiIntegrationValues(kpiRequests);
    }

    @Test
    void when_PopulateIterationBasedKpiThen_ProcessesWastageKpiCorrectly() {
        // Arrange
        Map<Integer, List<Double>> dataPointMap = new HashMap<>();
        ProjectInputDTO projectInput = createMockProjectInput();
        List<KpiElement> kpiData = createKpiElementsWithIssueData();
        String kpiId = "kpi131"; // Wastage KPI

        // Act
        ReflectionTestUtils.invokeMethod(productivityCalculationService,
                "populateKpiValuesByDataPointMapForIterationBasedKpi",
                dataPointMap, projectInput, kpiData, kpiId);

        // Assert
        assertFalse(dataPointMap.isEmpty());
        assertEquals(projectInput.sprints().size(), dataPointMap.size());
    }

    @Test
    void when_PopulateIterationBasedKpiThen_ProcessesWorkStatusKpiCorrectly() {
        // Arrange
        Map<Integer, List<Double>> dataPointMap = new HashMap<>();
        ProjectInputDTO projectInput = createMockProjectInput();
        List<KpiElement> kpiData = createKpiElementsWithCategoryWiseDelay();
        String kpiId = "kpi128"; // Work Status KPI

        // Act
        ReflectionTestUtils.invokeMethod(productivityCalculationService,
                "populateKpiValuesByDataPointMapForIterationBasedKpi",
                dataPointMap, projectInput, kpiData, kpiId);

        // Assert
        assertFalse(dataPointMap.isEmpty());
        assertEquals(projectInput.sprints().size(), dataPointMap.size());
    }

    // Helper methods
    private ProjectInputDTO createMockProjectInput() {
        List<SprintInputDTO> sprints = Arrays.asList(
                SprintInputDTO.builder()
                        .nodeId("sprint1")
                        .name("Sprint 1")
                        .hierarchyLevel(6)
                        .hierarchyLabel("sprint")
                        .build(),
                SprintInputDTO.builder()
                        .nodeId("sprint2")
                        .name("Sprint 2")
                        .hierarchyLevel(6)
                        .hierarchyLabel("sprint")
                        .build()
        );

        return ProjectInputDTO.builder()
                .nodeId("project1")
                .name("TestProject")
                .hierarchyLevel(5)
                .hierarchyLabel("project")
                .sprints(sprints)
                .build();
    }

    private List<KpiElement> createMockKpiElements() {
        KpiElement kpiElement = new KpiElement();
        kpiElement.setKpiId("kpi39");
        kpiElement.setKpiName("Sprint Velocity");
        kpiElement.setTrendValueList(createMockDataCountList());
        return List.of(kpiElement);
    }

    private List<KpiElement> createKpiElementsWithDataCount() {
        KpiElement kpiElement = new KpiElement();
        kpiElement.setKpiId("kpi39");
        kpiElement.setTrendValueList(createMockDataCountList());
        return List.of(kpiElement);
    }

    private List<KpiElement> createKpiElementsWithDataCountGroup() {
        KpiElement kpiElement = new KpiElement();
        kpiElement.setKpiId("kpi158");
        kpiElement.setTrendValueList(createMockDataCountGroupList());
        return List.of(kpiElement);
    }

    private List<KpiElement> createKpiElementsWithIssueData() {
        KpiElement kpiElement = new KpiElement();
        kpiElement.setKpiId("kpi131");
        kpiElement.setSprintId("sprint1");

        IssueKpiModalValue issueData = new IssueKpiModalValue();
        issueData.setIssueBlockedTime(10);
        issueData.setIssueWaitTime(5);

        kpiElement.setIssueData(Set.of(issueData));
        return List.of(kpiElement);
    }

    private List<KpiElement> createKpiElementsWithCategoryWiseDelay() {
        KpiElement kpiElement = new KpiElement();
        kpiElement.setKpiId("kpi128");
        kpiElement.setSprintId("sprint1");

        IssueKpiModalValue issueData = new IssueKpiModalValue();
        Map<String, Integer> categoryWiseDelay = new HashMap<>();
        categoryWiseDelay.put("Planned", 15);
        issueData.setCategoryWiseDelay(categoryWiseDelay);

        kpiElement.setIssueData(Set.of(issueData));
        return List.of(kpiElement);
    }

    private List<DataCount> createMockDataCountList() {
        DataCount dataCount = new DataCount();
        dataCount.setData("Project1");
        dataCount.setValue(Arrays.asList(
                createDataCountValue(10.0),
                createDataCountValue(15.0),
                createDataCountValue(20.0)
        ));
        return List.of(dataCount);
    }

    private List<DataCountGroup> createMockDataCountGroupList() {
        DataCountGroup dataCountGroup = new DataCountGroup();
        dataCountGroup.setFilter("Overall");
        dataCountGroup.setValue(List.of(createMockDataCount()));
        return List.of(dataCountGroup);
    }

    private DataCount createMockDataCount() {
        DataCount dataCount = new DataCount();
        dataCount.setData("Project1");
        dataCount.setValue(Arrays.asList(
                createDataCountValue(5.0),
                createDataCountValue(8.0),
                createDataCountValue(12.0)
        ));
        return dataCount;
    }

    private DataCount createDataCountValue(Double value) {
        DataCount dataCount = new DataCount();
        dataCount.setValue(value);
        return dataCount;
    }

    private Object createMockKPIConfiguration(String kpiId, String xAxisMeasurement) {
        try {
            Class<?> kpiConfigClass = Class.forName(
                    "com.publicissapient.kpidashboard.job.productivitycalculation.service.ProductivityCalculationService$KPIConfiguration");
            Object kpiConfig = kpiConfigClass.getDeclaredConstructor().newInstance();

            ReflectionTestUtils.setField(kpiConfig, "kpiId", kpiId);
            ReflectionTestUtils.setField(kpiConfig, "weightInProductivityScoreCalculation", 1.0);

            // Set XAxisMeasurement enum
            Class<?> xAxisEnum = Class.forName(
                    "com.publicissapient.kpidashboard.job.productivitycalculation.service.ProductivityCalculationService$KPIConfiguration$XAxisMeasurement");
            Object xAxisValue = Enum.valueOf((Class<Enum>) xAxisEnum, xAxisMeasurement);
            ReflectionTestUtils.setField(kpiConfig, "xAxisMeasurement", xAxisValue);

            return kpiConfig;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock KPI configuration", e);
        }
    }

    private Object createMockKPIConfigurationWithFilter(String filter, String filter1, String filter2) {
        try {
            Class<?> kpiConfigClass = Class.forName(
                    "com.publicissapient.kpidashboard.job.productivitycalculation.service.ProductivityCalculationService$KPIConfiguration");
            Object kpiConfig = kpiConfigClass.getDeclaredConstructor().newInstance();

            ReflectionTestUtils.setField(kpiConfig, "dataCountGroupFilterUsedForCalculation", filter);
            ReflectionTestUtils.setField(kpiConfig, "dataCountGroupFilter1UsedForCalculation", filter1);
            ReflectionTestUtils.setField(kpiConfig, "dataCountGroupFilter2UsedForCalculation", filter2);

            return kpiConfig;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Object> createMockKPIVariationCalculationData() {
        try {
            Class<?> variationClass = Class.forName(
                    "com.publicissapient.kpidashboard.job.productivitycalculation.service.ProductivityCalculationService$KPIVariationCalculationData");
            Object variationData = variationClass.getDeclaredConstructor().newInstance();

            ReflectionTestUtils.setField(variationData, "dataPointGainWeightSumProduct", 10.0);
            ReflectionTestUtils.setField(variationData, "weightParts", 5.0);
            ReflectionTestUtils.setField(variationData, "kpiName", "Test KPI");
            ReflectionTestUtils.setField(variationData, "kpiId", "kpi123");

            return List.of(variationData);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private ProductivityCalculation createMockProductivityCalculation(String nodeId) {
        ProductivityCalculation calculation = new ProductivityCalculation();
        calculation.setHierarchyEntityNodeId(nodeId);
        calculation.setHierarchyEntityName("Test Project");
        calculation.setCalculationDate(Instant.now());
        calculation.setProductivityMetrics(ProductivityMetrics.builder()
                .speed(10.0)
                .quality(15.0)
                .efficiency(12.0)
                .productivity(8.0)
                .overall(11.25)
                .build());
        return calculation;
    }

    private KpiRequest createMockKpiRequest() {
        return KpiRequest.builder()
                .kpiIdList(List.of("kpi39"))
                .selectedMap(Map.of("project", List.of("project1")))
                .ids(new String[]{"project1"})
                .level(5)
                .label("project")
                .build();
    }
}

