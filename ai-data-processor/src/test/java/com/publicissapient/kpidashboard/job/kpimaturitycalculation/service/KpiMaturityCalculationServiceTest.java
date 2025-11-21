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

package com.publicissapient.kpidashboard.job.kpimaturitycalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.common.model.application.KpiCategoryMapping;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.EfficiencyScore;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KPIData;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KpiMaturity;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.MaturityScore;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.KpiMasterCustomRepository;
import com.publicissapient.kpidashboard.common.repository.projection.BasicKpiMasterProjection;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.config.CalculationConfig;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.config.KpiMaturityCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.shared.dto.SprintInputDTO;

import io.micrometer.common.util.StringUtils;

@ExtendWith(MockitoExtension.class)
class KpiMaturityCalculationServiceTest {
	@Mock
	private KpiMasterCustomRepository kpiMasterCustomRepository;

	@Mock
	private KpiCategoryMappingRepository kpiCategoryMappingRepository;

	@Mock
	private KnowHOWClient knowHOWClient;

	@Mock
	private KpiMaturityCalculationConfig kpiMaturityCalculationConfig;

	@Mock
	private CalculationConfig calculationConfig;

	@Mock
	private CalculationConfig.DataPoints dataPoints;

	@Mock
	private CalculationConfig.Maturity maturity;

	@InjectMocks
	private KpiMaturityCalculationService kpiMaturityCalculationService;

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
	void when_KpiMaturityCalculationConfigContainsErrors_Expect_IllegalStateExceptionIsThrown() {
		when(kpiMaturityCalculationConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getConfigValidationErrors()).thenReturn(Set.of("Validation error"));

		assertThrows(IllegalStateException.class,
				() -> kpiMaturityCalculationService.calculateKpiMaturityForProject(testProjectInputDTO));
	}

	@ParameterizedTest
	@MethodSource("generateTestKpiMaturityCategoryWeights")
	void when_VariousMaturityKpiCategoriesAreReceived_Expect_KpiElementsConsideredForCalculationAreTheOnesRequested(
			Map<String, Double> kpiMaturityCategoryWeights) {
		List<KpiMaster> kpisConsideredForMaturityCalculation = ReflectionTestUtils
				.invokeMethod(kpiMaturityCalculationService, "loadKpisEligibleForMaturityCalculation");

		if (MapUtils.isEmpty(kpiMaturityCategoryWeights)) {
			assertTrue(CollectionUtils.isEmpty(kpisConsideredForMaturityCalculation));
		} else {
			assertNotNull(kpisConsideredForMaturityCalculation);
			kpisConsideredForMaturityCalculation.forEach(kpiMaster -> {
				assertTrue(StringUtils.isNotEmpty(kpiMaster.getKpiCategory()));
				assertTrue(kpiMaturityCategoryWeights.containsKey(kpiMaster.getKpiCategory()));
			});
		}
	}

	@Test
	void when_KpiElementResponseIsEmpty_Expect_MaturityIsNotCalculated() {
		when(kpiMaturityCalculationConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getConfigValidationErrors()).thenReturn(Collections.emptySet());

		initializeKpisUsedForMaturityCalculation();
		ReflectionTestUtils.invokeMethod(kpiMaturityCalculationService, "initializePreloadedData");

		when(knowHOWClient.getKpiIntegrationValues(any())).thenReturn(Collections.emptyList());

		assertNull(kpiMaturityCalculationService.calculateKpiMaturityForProject(testProjectInputDTO));
	}

	@Test
	void when_NoKpiElementContainsANumericOverallMaturity_Expect_MaturityIsNotCalculated() {
		when(kpiMaturityCalculationConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getConfigValidationErrors()).thenReturn(Collections.emptySet());

		initializeKpisUsedForMaturityCalculation();
		ReflectionTestUtils.invokeMethod(kpiMaturityCalculationService, "initializePreloadedData");

		when(knowHOWClient.getKpiIntegrationValues(any()))
				.thenReturn(List.of(KpiElement.builder().kpiId("kpi1").overallMaturity(null).build(),
						KpiElement.builder().kpiId("kpi1").overallMaturity("").build(),
						KpiElement.builder().kpiId("kpi1").overallMaturity("overall-maturity").build()));

		assertNull(kpiMaturityCalculationService.calculateKpiMaturityForProject(testProjectInputDTO));
	}

	@Test
	void when_RequestIsValid_Expect_KpiMaturityIsComputedAsExpected() {
		initializeKpiMaturityCalculationConfigurations();

		Set<String> expectedKpiIdsAfterMaturityCalculation = Set.of("kpi1", "kpi3", "kpi4", "kpi6", "kpi7", "kpi10",
				"kpi11");
		Set<String> expectedMaturityCategories = Set.of("quality", "value", "speed", "dora");

		Map<String, MaturityScore> expectedMaturityScoresGroupedByCategory = Map.of("quality",
				MaturityScore.builder().kpiCategory("quality").level("M4").score(3.5D).build(), "dora",
				MaturityScore.builder().kpiCategory("dora").level("M1").score(1.0D).build(), "value",
				MaturityScore.builder().kpiCategory("value").level("M2").score(2.0D).build(), "speed",
				MaturityScore.builder().kpiCategory("speed").level("M1").score(1.0D).build());

		EfficiencyScore expectedEfficiencyScore = EfficiencyScore.builder().percentage(37.5).score(1.9).build();

		KpiMaturity resultedKpiMaturity = kpiMaturityCalculationService
				.calculateKpiMaturityForProject(testProjectInputDTO);

		assertNotNull(resultedKpiMaturity);
		assertNotNull(resultedKpiMaturity.getCalculationDate());
		assertEquals(testProjectInputDTO.hierarchyLevel(), resultedKpiMaturity.getHierarchyLevel());
		assertEquals(testProjectInputDTO.hierarchyLevelId(), resultedKpiMaturity.getHierarchyLevelId());
		assertEquals(testProjectInputDTO.name(), resultedKpiMaturity.getHierarchyEntityName());
		assertEquals(testProjectInputDTO.nodeId(), resultedKpiMaturity.getHierarchyEntityNodeId());

		List<KPIData> resultedKpiData = resultedKpiMaturity.getKpis();
		assertTrue(CollectionUtils.isNotEmpty(resultedKpiData));
		assertTrue(resultedKpiData.stream().map(KPIData::getKpiId)
				.allMatch(expectedKpiIdsAfterMaturityCalculation::contains));

		Set<String> resultedKpiDataIds = resultedKpiData.stream().map(KPIData::getKpiId).collect(Collectors.toSet());
		assertTrue(resultedKpiDataIds.containsAll(expectedKpiIdsAfterMaturityCalculation));

		List<MaturityScore> resultedMaturityScores = resultedKpiMaturity.getMaturityScores();
		assertTrue(CollectionUtils.isNotEmpty(resultedMaturityScores));
		assertTrue(resultedMaturityScores.stream().map(MaturityScore::getKpiCategory)
				.allMatch(expectedMaturityCategories::contains));
		assertTrue(resultedMaturityScores.stream().allMatch(maturityScore -> maturityScore
				.equals(expectedMaturityScoresGroupedByCategory.get(maturityScore.getKpiCategory()))));

		EfficiencyScore resultedEfficiencyScore = resultedKpiMaturity.getEfficiency();
		assertNotNull(resultedEfficiencyScore);
		assertEquals(resultedEfficiencyScore, expectedEfficiencyScore);
	}

	private void initializeKpiMaturityCalculationConfigurations() {
		initializeCalculationConfig();
		initializeKpisUsedForMaturityCalculation();
		ReflectionTestUtils.invokeMethod(kpiMaturityCalculationService, "initializePreloadedData");
		when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(createMockKpiElements());
	}

	private void initializeKpisUsedForMaturityCalculation() {
		when(kpiMasterCustomRepository
				.findByDeliveryMethodologyTypeSupportingMaturityCalculation(ProjectDeliveryMethodology.SCRUM))
				.thenReturn(createMockKpiMasterProjections());
		when(kpiCategoryMappingRepository.findAllByKpiIdIn(anySet())).thenReturn(createMockKpiCategoryMapping());
	}

	private void initializeCalculationConfig() {
		// Setup configuration mocks
		when(kpiMaturityCalculationConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
		when(calculationConfig.getAllConfiguredCategories()).thenReturn(Set.of("quality", "value", "dora", "speed"));
		when(calculationConfig.getMaturity()).thenReturn(maturity);
		when(dataPoints.getCount()).thenReturn(5);
		when(maturity.getWeights()).thenReturn(Map.of("quality", 0.25, "value", 0.25, "dora", 0.25, "speed", 0.25));
		when(calculationConfig.getConfigValidationErrors()).thenReturn(Collections.emptySet());
	}

	private static List<KpiElement> createMockKpiElements() {
		return List.of(KpiElement.builder().kpiId("kpi1").overallMaturity("1").build(),
				KpiElement.builder().kpiId("kpi2").build(),
				KpiElement.builder().kpiId("kpi3").overallMaturity("3").build(),
				KpiElement.builder().kpiId("kpi4").overallMaturity("2").build(),
				KpiElement.builder().kpiId("kpi5").overallMaturity("5").build(),
				KpiElement.builder().kpiId("kpi6").overallMaturity("0").build(),
				KpiElement.builder().kpiId("kpi7").overallMaturity("2").build(),
				KpiElement.builder().kpiId("kpi8").overallMaturity("1").build(),
				KpiElement.builder().kpiId("kpi9").overallMaturity("4").build(),
				KpiElement.builder().kpiId("kpi10").overallMaturity("1").build(),
				KpiElement.builder().kpiId("kpi11").overallMaturity("5").build());
	}

	private static List<KpiCategoryMapping> createMockKpiCategoryMapping() {
		return List.of(KpiCategoryMapping.builder().kpiId("kpi1").categoryId("speed").build(),
				KpiCategoryMapping.builder().kpiId("kpi2").categoryId("quality").build(),
				KpiCategoryMapping.builder().kpiId("kpi3").categoryId("value").build(),
				KpiCategoryMapping.builder().kpiId("kpi7").categoryId("quality").build(),
				KpiCategoryMapping.builder().kpiId("kpi10").categoryId("value").build(),
				KpiCategoryMapping.builder().kpiId("kpi11").categoryId("quality").build(),
				KpiCategoryMapping.builder().kpiId("kpi12").categoryId("speed").build());
	}

	private static List<BasicKpiMasterProjection> createMockKpiMasterProjections() {
		return List.of(generateKpiMasterProjection("kpi1", "test kpi 1", "Sprints", null, "Jira"),
				generateKpiMasterProjection("kpi2", "test kpi 2", "Sprints", null, "Zypher"),
				generateKpiMasterProjection("kpi3", "test kpi 3", "Weeks", null, "Sonar"),
				generateKpiMasterProjection("kpi4", "test kpi 4", "Weeks", "Dora", "Jenkins"),
				generateKpiMasterProjection("kpi5", "test kpi 5", "Weeks", "Developer", "Bitbucket"),
				generateKpiMasterProjection("kpi6", "test kpi 6", "Weeks", "Dora", "Jira"),
				generateKpiMasterProjection("kpi7", "test kpi 7", "Weeks", null, "Jenkins"),
				generateKpiMasterProjection("kpi8", "test kpi 8", "", "Iteration", "Jira"),
				generateKpiMasterProjection("kpi9", "test kpi 9", "Days", "Developer", "BitBucket"),
				generateKpiMasterProjection("kpi10", "test kpi 10", "Months", null, "Sonar"),
				generateKpiMasterProjection("kpi11", "test kpi 11", "PIs", null, "Jira"),
				generateKpiMasterProjection("kpi12", "test kpi 12", "Range", null, "Jira"));
	}

	private static BasicKpiMasterProjection generateKpiMasterProjection(String kpiId, String kpiName, String xAxisLabel,
			String kpiCategory, String kpiSource) {
		return new BasicKpiMasterProjection() {
			@Override
			public String getKpiId() {
				return kpiId;
			}

			@Override
			public String getKpiName() {
				return kpiName;
			}

			@Override
			public String getxAxisLabel() {
				return xAxisLabel;
			}

			@Override
			public String getKpiCategory() {
				return kpiCategory;
			}

			@Override
			public String getKpiSource() {
				return kpiSource;
			}
		};
	}

	private static List<Map<String, Double>> generateTestKpiMaturityCategoryWeights() {
		return List.of(Map.of("speed", 1.0), Map.of("quality", 0.25, "value", 0.25, "dora", 0.25, "speed", 0.25),
				Map.of("quality", 0.25, "value", 0.25, "dora", 0.5, "speed", 0.0),
				Map.of("quality", 0.5, "test-category", 0.5), Map.of());
	}
}
