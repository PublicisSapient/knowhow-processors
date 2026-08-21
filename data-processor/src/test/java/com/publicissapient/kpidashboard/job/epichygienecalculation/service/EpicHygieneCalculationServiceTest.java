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

package com.publicissapient.kpidashboard.job.epichygienecalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.client.customapi.dto.IterationKpiDataDTO;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.exception.EpicHygieneKpiUnavailableException;
import com.publicissapient.kpidashboard.job.epichygienecalculation.parser.EpicHygieneTrendValueParser;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("EpicHygieneCalculationService Tests")
class EpicHygieneCalculationServiceTest {

	@Mock private EpicHygieneKpiClientService kpiClientService;

	private EpicHygieneCalculationJobConfig jobConfig;
	private EpicHygieneCalculationService calculationService;
	private ProjectInputDTO projectInput;

	@BeforeEach
	void setUp() {
		EpicHygieneJobConfig calculationConfig = new EpicHygieneJobConfig();
		calculationConfig.setKpiId("kpi312");
		calculationConfig.setMaxRetryAttempts(3);
		calculationConfig.setRetryBackoffMillis(0L);
		calculationConfig.setFallbackEnabled(true);

		BatchConfig batchConfig = new BatchConfig();
		batchConfig.setChunkSize(10);

		jobConfig = new EpicHygieneCalculationJobConfig();
		jobConfig.setName("epic-hygiene-calculation");
		jobConfig.setBatching(batchConfig);
		jobConfig.setCalculationConfig(calculationConfig);

		calculationService =
				new EpicHygieneCalculationService(
						kpiClientService, new EpicHygieneTrendValueParser(), jobConfig);

		projectInput =
				ProjectInputDTO.builder()
						.nodeId("node-1")
						.name("Test Project")
						.hierarchyLevel(5)
						.hierarchyLevelId("project")
						.basicProjectConfigId("project-1")
						.sprints(Collections.emptyList())
						.build();
	}

	private KpiElement kpiElementWithTrendValues() {
		return KpiElement.builder()
				.kpiId("kpi312")
				.kpiName("Epic Hygiene")
				.trendValueList(
						List.of(
								IterationKpiDataDTO.builder()
										.label(EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS)
										.value(10d)
										.build(),
								IterationKpiDataDTO.builder()
										.label(EpicHygieneTrendValueParser.LABEL_CONSTRUCTION_READY)
										.value(6d)
										.build(),
								IterationKpiDataDTO.builder()
										.label(EpicHygieneTrendValueParser.LABEL_AT_RISK_BLOCKED)
										.value(2d)
										.build(),
								IterationKpiDataDTO.builder()
										.label(EpicHygieneTrendValueParser.LABEL_AVG_READINESS_SCORE)
										.value(64.5d)
										.build()))
				.build();
	}

	@Nested
	@DisplayName("Successful calculation")
	class SuccessfulCalculation {

		@Test
		@DisplayName("Should build a fully populated document from the KPI payload")
		void computeForProject_ValidKpi_ReturnsPopulatedDocument() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(List.of(kpiElementWithTrendValues()));

			EpicHygieneData result = calculationService.computeForProject(projectInput);

			assertNotNull(result);
			assertFalse(result.isFallback());
			assertNull(result.getFailureReason());
			assertEquals("project-1", result.getBasicProjectConfigId());
			assertEquals("node-1", result.getProjectNodeId());
			assertEquals("Test Project", result.getProjectName());
			assertEquals("kpi312", result.getKpiId());
			assertEquals("Epic Hygiene", result.getKpiName());
			assertEquals(10, result.getTotalActiveEpics());
			assertEquals(6, result.getConstructionReadyEpics());
			assertEquals(2, result.getAtRiskEpics());
			assertEquals(64.5d, result.getAvgReadinessScore());
			assertEquals(4, result.getMetrics().size());
			assertNotNull(result.getCalculationDate());
		}

		@Test
		@DisplayName("Should pick the Epic Hygiene element out of a multi KPI response")
		void computeForProject_MultipleKpis_PicksConfiguredOne() {
			KpiElement otherKpi = KpiElement.builder().kpiId("kpi999").kpiName("Other").build();
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(Arrays.asList(otherKpi, kpiElementWithTrendValues()));

			EpicHygieneData result = calculationService.computeForProject(projectInput);

			assertEquals("Epic Hygiene", result.getKpiName());
			assertFalse(result.isFallback());
		}

		@Test
		@DisplayName("Should match the KPI id case-insensitively")
		void computeForProject_KpiIdCasingDiffers_StillMatches() {
			KpiElement element = kpiElementWithTrendValues();
			element.setKpiId("KPI312");
			when(kpiClientService.fetchEpicHygieneKpi(projectInput)).thenReturn(List.of(element));

			EpicHygieneData result = calculationService.computeForProject(projectInput);

			assertFalse(result.isFallback());
			assertEquals("KPI312", result.getKpiId());
		}

		@Test
		@DisplayName("Should ignore null entries in the KPI response")
		void computeForProject_ResponseContainsNulls_IgnoresThem() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(Arrays.asList(null, kpiElementWithTrendValues()));

			assertFalse(calculationService.computeForProject(projectInput).isFallback());
		}
	}

	@Nested
	@DisplayName("Fallback behaviour")
	class FallbackBehaviour {

		@Test
		@DisplayName("Should return a flagged fallback record when the KPI is unavailable")
		void computeForProject_KpiUnavailable_ReturnsFallbackRecord() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenThrow(new EpicHygieneKpiUnavailableException("KPI down after 3 attempts"));

			EpicHygieneData result = calculationService.computeForProject(projectInput);

			assertNotNull(result);
			assertTrue(result.isFallback());
			assertEquals("KPI down after 3 attempts", result.getFailureReason());
			assertEquals("project-1", result.getBasicProjectConfigId());
			assertEquals("kpi312", result.getKpiId());
			assertNull(result.getTotalActiveEpics());
			assertNull(result.getAvgReadinessScore());
			assertTrue(result.getMetrics().isEmpty());
			assertNotNull(result.getCalculationDate());
		}

		@Test
		@DisplayName("Should fall back when the configured KPI is missing from the response")
		void computeForProject_ConfiguredKpiMissing_ReturnsFallbackRecord() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(List.of(KpiElement.builder().kpiId("kpi999").build()));

			EpicHygieneData result = calculationService.computeForProject(projectInput);

			assertTrue(result.isFallback());
			assertTrue(result.getFailureReason().contains("kpi312"));
		}

		@Test
		@DisplayName("Should fall back when trendValueList is null")
		void computeForProject_NullTrendValueList_ReturnsFallbackRecord() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(List.of(KpiElement.builder().kpiId("kpi312").build()));

			EpicHygieneData result = calculationService.computeForProject(projectInput);

			assertTrue(result.isFallback());
			assertTrue(result.getFailureReason().contains("trendValueList"));
		}

		@Test
		@DisplayName("Should fall back when trendValueList holds no readable metric")
		void computeForProject_UnreadableTrendValueList_ReturnsFallbackRecord() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(
							List.of(
									KpiElement.builder()
											.kpiId("kpi312")
											.trendValueList(List.of(new DataCount("data", 1)))
											.build()));

			assertTrue(calculationService.computeForProject(projectInput).isFallback());
		}

		@Test
		@DisplayName("Should fall back when trendValueList is an empty list")
		void computeForProject_EmptyTrendValueList_ReturnsFallbackRecord() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(
							List.of(
									KpiElement.builder()
											.kpiId("kpi312")
											.trendValueList(Collections.emptyList())
											.build()));

			assertTrue(calculationService.computeForProject(projectInput).isFallback());
		}

		@Test
		@DisplayName("Should propagate the failure when the fallback is disabled")
		void computeForProject_FallbackDisabled_PropagatesFailure() {
			jobConfig.getCalculationConfig().setFallbackEnabled(false);
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenThrow(new EpicHygieneKpiUnavailableException("KPI down"));

			EpicHygieneKpiUnavailableException exception =
					assertThrows(
							EpicHygieneKpiUnavailableException.class,
							() -> calculationService.computeForProject(projectInput));

			assertEquals("KPI down", exception.getMessage());
		}

		@Test
		@DisplayName("Should propagate a missing KPI when the fallback is disabled")
		void computeForProject_FallbackDisabledAndKpiMissing_PropagatesFailure() {
			jobConfig.getCalculationConfig().setFallbackEnabled(false);
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(List.of(KpiElement.builder().kpiId("kpi999").build()));

			assertThrows(
					EpicHygieneKpiUnavailableException.class,
					() -> calculationService.computeForProject(projectInput));
		}
	}

	@Nested
	@DisplayName("Edge cases")
	class EdgeCases {

		@Test
		@DisplayName("Should reject a null project")
		void computeForProject_NullProject_Throws() {
			assertThrows(NullPointerException.class, () -> calculationService.computeForProject(null));
		}

		@Test
		@DisplayName("Should fall back when the client returns an empty list")
		void computeForProject_EmptyResponse_ReturnsFallbackRecord() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput)).thenReturn(Collections.emptyList());

			assertTrue(calculationService.computeForProject(projectInput).isFallback());
		}

		@Test
		@DisplayName("Should keep partial data when only some labels are published")
		void computeForProject_PartialMetrics_KeepsWhatIsAvailable() {
			when(kpiClientService.fetchEpicHygieneKpi(projectInput))
					.thenReturn(
							List.of(
									KpiElement.builder()
											.kpiId("kpi312")
											.kpiName("Epic Hygiene")
											.trendValueList(
													List.of(
															IterationKpiDataDTO.builder()
																	.label(EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS)
																	.value(3d)
																	.build()))
											.build()));

			EpicHygieneData result = calculationService.computeForProject(projectInput);

			assertFalse(result.isFallback());
			assertEquals(3, result.getTotalActiveEpics());
			assertNull(result.getConstructionReadyEpics());
			assertNull(result.getAtRiskEpics());
			assertNull(result.getAvgReadinessScore());
		}

		@Test
		@DisplayName("Should handle a project whose optional attributes are null")
		void computeForProject_ProjectWithNullName_StillBuildsDocument() {
			ProjectInputDTO minimalProject =
					ProjectInputDTO.builder()
							.nodeId("node-2")
							.hierarchyLevel(5)
							.hierarchyLevelId("project")
							.basicProjectConfigId("project-2")
							.sprints(Collections.emptyList())
							.build();
			when(kpiClientService.fetchEpicHygieneKpi(minimalProject))
					.thenReturn(List.of(kpiElementWithTrendValues()));

			EpicHygieneData result = calculationService.computeForProject(minimalProject);

			assertEquals("project-2", result.getBasicProjectConfigId());
			assertNull(result.getProjectName());
		}
	}
}
