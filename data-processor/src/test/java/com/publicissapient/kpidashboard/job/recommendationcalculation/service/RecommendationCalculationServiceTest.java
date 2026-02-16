/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.job.recommendationcalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;
import com.publicissapient.kpidashboard.config.mongo.TTLIndexConfigProperties;
import com.publicissapient.kpidashboard.job.recommendationcalculation.builder.RecommendationActionPlanBuilder;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.CalculationConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.parser.BatchRecommendationResponseParser;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationCalculationService Tests")
class RecommendationCalculationServiceTest {

	@Mock private AiGatewayClient aiGatewayClient;

	@Mock private KpiDataExtractionService kpiDataExtractionService;

	@Mock private PromptService promptService;

	@Mock private BatchRecommendationResponseParser recommendationResponseParser;

	@Mock private RecommendationCalculationConfig recommendationCalculationConfig;

	@Mock private RecommendationActionPlanBuilder recommendationActionPlanBuilder;

	@Mock private TTLIndexConfigProperties ttlIndexConfigProperties;

	@InjectMocks private RecommendationCalculationService recommendationCalculationService;

	private ProjectInputDTO testProjectInput;
	private Map<Pair<String, String>, Object> testKpiData;
	private Recommendation testRecommendation;
	private ChatGenerationResponseDTO testAiResponse;
	private CalculationConfig testCalculationConfig;
	private TTLIndexConfigProperties.TTLIndexConfig ttlConfig;
	private RecommendationsActionPlan projectLevelRecommendation;
	private RecommendationsActionPlan kpiLevelRecommendation1;
	private RecommendationsActionPlan kpiLevelRecommendation2;

	@BeforeEach
	void setUp() {
		testProjectInput =
				ProjectInputDTO.builder()
						.nodeId("test-project-id")
						.name("Test Project")
						.hierarchyLevel(5)
						.hierarchyLevelId("project")
						.build();

		testKpiData = new HashMap<>();
		testKpiData.put(Pair.of("kpi14", "Velocity"), List.of("Sprint 1: 45 SP", "Sprint 2: 50 SP"));
		testKpiData.put(Pair.of("kpi17", "Quality"), List.of("Defects: 5", "Coverage: 80%"));

		testRecommendation =
				Recommendation.builder()
						.title("Improve Velocity")
						.description("Test recommendation description")
						.build();

		testAiResponse = new ChatGenerationResponseDTO("Test AI response");

		testCalculationConfig = new CalculationConfig();
		testCalculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		testCalculationConfig.setKpiList(List.of("kpi14", "kpi17"));

		ttlConfig = new TTLIndexConfigProperties.TTLIndexConfig();
		ttlConfig.setExpiration(30);
		ttlConfig.setTimeUnit(TimeUnit.DAYS);

		// Create mock recommendations for builder mocking
		projectLevelRecommendation =
				RecommendationsActionPlan.builder()
						.basicProjectConfigId("test-project-id")
						.projectName("Test Project")
						.persona(Persona.ENGINEERING_LEAD)
						.level(RecommendationLevel.PROJECT_LEVEL)
						.recommendations(testRecommendation)
						.metadata(
								com.publicissapient.kpidashboard.common.model.recommendation.batch
										.RecommendationMetadata.builder()
										.persona(Persona.ENGINEERING_LEAD)
										.requestedKpis(List.of("kpi14", "kpi17"))
										.build())
						.createdAt(Instant.now())
						.expiresOn(Instant.now().plusSeconds(30 * 24 * 60 * 60))
						.build();

		kpiLevelRecommendation1 =
				RecommendationsActionPlan.builder()
						.basicProjectConfigId("test-project-id")
						.projectName("Test Project")
						.persona(Persona.ENGINEERING_LEAD)
						.level(RecommendationLevel.KPI_LEVEL)
						.kpiId("kpi14")
						.recommendations(testRecommendation)
						.metadata(
								com.publicissapient.kpidashboard.common.model.recommendation.batch
										.RecommendationMetadata.builder()
										.persona(Persona.ENGINEERING_LEAD)
										.requestedKpis(List.of("kpi14"))
										.build())
						.createdAt(Instant.now())
						.expiresOn(Instant.now().plusSeconds(30 * 24 * 60 * 60))
						.build();

		kpiLevelRecommendation2 =
				RecommendationsActionPlan.builder()
						.basicProjectConfigId("test-project-id")
						.projectName("Test Project")
						.persona(Persona.ENGINEERING_LEAD)
						.level(RecommendationLevel.KPI_LEVEL)
						.kpiId("kpi17")
						.recommendations(testRecommendation)
						.metadata(
								com.publicissapient.kpidashboard.common.model.recommendation.batch
										.RecommendationMetadata.builder()
										.persona(Persona.ENGINEERING_LEAD)
										.requestedKpis(List.of("kpi17"))
										.build())
						.createdAt(Instant.now())
						.expiresOn(Instant.now().plusSeconds(30 * 24 * 60 * 60))
						.build();

		@Nested
		@DisplayName("Successful Scenarios")
		class SuccessfulScenarios {

			@Test
			@DisplayName("Should successfully calculate recommendations for project")
			void calculateRecommendationsForProject_Success() throws Exception {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD)))
						.thenReturn("Test prompt");
				when(promptService.getBatchKpiLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD), any()))
						.thenReturn("Test KPI prompt");
				when(aiGatewayClient.generate(any(ChatGenerationRequest.class))).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn((testRecommendation));
				when(ttlIndexConfigProperties.getConfigs())
						.thenReturn(Map.of("recommendation-calculation", ttlConfig));
				when(recommendationActionPlanBuilder.buildProjectLevelPlan(
								eq(testProjectInput), eq(Persona.ENGINEERING_LEAD), eq(testAiResponse)))
						.thenReturn(projectLevelRecommendation);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi14")))
						.thenReturn(kpiLevelRecommendation1);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi17")))
						.thenReturn(kpiLevelRecommendation2);

				// Act
				List<RecommendationsActionPlan> results =
						recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

				// Assert
				assertNotNull(results);
				assertEquals(3, results.size()); // 1 PROJECT + 2 KPI level recommendations

				// Verify PROJECT-level recommendation
				RecommendationsActionPlan projectRecommendation =
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.PROJECT_LEVEL)
								.findFirst()
								.orElseThrow();
				assertEquals(
						testProjectInput.basicProjectConfigId(),
						projectRecommendation.getBasicProjectConfigId());
				assertEquals(testProjectInput.name(), projectRecommendation.getProjectName());
				assertEquals(Persona.ENGINEERING_LEAD, projectRecommendation.getPersona());
				assertEquals(RecommendationLevel.PROJECT_LEVEL, projectRecommendation.getLevel());
				assertNotNull(projectRecommendation.getRecommendations());
				assertEquals(
						testRecommendation.getTitle(), projectRecommendation.getRecommendations().getTitle());
				assertNotNull(projectRecommendation.getMetadata());
				assertNotNull(projectRecommendation.getCreatedAt());
				assertNotNull(projectRecommendation.getExpiresOn());

				// Verify KPI-level recommendations
				List<RecommendationsActionPlan> kpiRecommendations =
						results.stream().filter(r -> r.getLevel() == RecommendationLevel.KPI_LEVEL).toList();
				assertEquals(2, kpiRecommendations.size()); // 2 KPIs configured

				for (RecommendationsActionPlan kpiRec : kpiRecommendations) {
					assertEquals(testProjectInput.basicProjectConfigId(), kpiRec.getBasicProjectConfigId());
					assertEquals(testProjectInput.name(), kpiRec.getProjectName());
					assertEquals(Persona.ENGINEERING_LEAD, kpiRec.getPersona());
					assertEquals(RecommendationLevel.KPI_LEVEL, kpiRec.getLevel());
					assertTrue(List.of("kpi14", "kpi17").contains(kpiRec.getKpiId()));
					assertNotNull(kpiRec.getRecommendations());
					assertEquals(testRecommendation.getTitle(), kpiRec.getRecommendations().getTitle());
					assertNotNull(kpiRec.getMetadata());
					assertNotNull(kpiRec.getCreatedAt());
					assertNotNull(kpiRec.getExpiresOn());
				}

				verify(kpiDataExtractionService).fetchKpiDataForProject(testProjectInput);
				verify(promptService).getBatchProjectLevelPrompt(testKpiData, Persona.ENGINEERING_LEAD);
				verify(promptService, times(2))
						.getBatchKpiLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD), any());
				verify(aiGatewayClient, times(3)).generate(any(ChatGenerationRequest.class));
				verify(recommendationResponseParser, times(3)).parseRecommendation(testAiResponse);
			}

			@Test
			@DisplayName("Should correctly set TTL expiration from config")
			void calculateRecommendationsForProject_SetsTTLCorrectly() throws Exception {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), any())).thenReturn("Test prompt");
				when(promptService.getBatchKpiLevelPrompt(anyMap(), any(), any()))
						.thenReturn("Test KPI prompt");
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn((testRecommendation));
				when(ttlIndexConfigProperties.getConfigs())
						.thenReturn(Map.of("recommendation-calculation", ttlConfig));
				when(recommendationActionPlanBuilder.buildProjectLevelPlan(
								eq(testProjectInput), eq(Persona.ENGINEERING_LEAD), eq(testAiResponse)))
						.thenReturn(projectLevelRecommendation);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi14")))
						.thenReturn(kpiLevelRecommendation1);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi17")))
						.thenReturn(kpiLevelRecommendation2);

				Instant beforeCall = Instant.now();

				// Act
				List<RecommendationsActionPlan> results =
						recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

				// Assert - Check TTL on PROJECT-level recommendation
				RecommendationsActionPlan projectRecommendation =
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.PROJECT_LEVEL)
								.findFirst()
								.orElseThrow();

				assertNotNull(projectRecommendation.getExpiresOn());
				long expectedTtlSeconds = 30 * 24 * 60 * 60; // 30 days in seconds
				long actualDiff =
						projectRecommendation.getExpiresOn().getEpochSecond()
								- projectRecommendation.getCreatedAt().getEpochSecond();
				assertEquals(expectedTtlSeconds, actualDiff);
			}

			@Test
			@DisplayName("Should build metadata with correct KPI list and persona")
			void calculateRecommendationsForProject_BuildsCorrectMetadata() throws Exception {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), any())).thenReturn("Test prompt");
				when(promptService.getBatchKpiLevelPrompt(anyMap(), any(), any()))
						.thenReturn("Test KPI prompt");
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn((testRecommendation));
				when(ttlIndexConfigProperties.getConfigs())
						.thenReturn(Map.of("recommendation-calculation", ttlConfig));
				when(recommendationActionPlanBuilder.buildProjectLevelPlan(
								eq(testProjectInput), eq(Persona.ENGINEERING_LEAD), eq(testAiResponse)))
						.thenReturn(projectLevelRecommendation);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi14")))
						.thenReturn(kpiLevelRecommendation1);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi17")))
						.thenReturn(kpiLevelRecommendation2);

				// Act
				List<RecommendationsActionPlan> results =
						recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

				// Assert - Check metadata on PROJECT-level recommendation
				RecommendationsActionPlan projectRecommendation =
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.PROJECT_LEVEL)
								.findFirst()
								.orElseThrow();

				assertNotNull(projectRecommendation.getMetadata());
				assertEquals(Persona.ENGINEERING_LEAD, projectRecommendation.getMetadata().getPersona());
				assertEquals(
						testCalculationConfig.getKpiList(),
						projectRecommendation.getMetadata().getRequestedKpis());
			}
		}

		@Nested
		@DisplayName("Exception Scenarios")
		class ExceptionScenarios {

			@Test
			@DisplayName("Should throw IllegalStateException when AI response parsing fails")
			void calculateRecommendationsForProject_ParsingFails_ThrowsIllegalStateException()
					throws Exception {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), any())).thenReturn("Test prompt");
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenThrow(new IllegalStateException("Parsing error"));

				// Act & Assert
				IllegalStateException exception =
						assertThrows(
								IllegalStateException.class,
								() ->
										recommendationCalculationService.calculateRecommendationsForProject(
												testProjectInput));

				assertTrue(exception.getMessage().contains("Parsing error"));
			}

			@Test
			@DisplayName("Should throw RuntimeException when AI Gateway fails")
			void calculateRecommendationsForProject_AiGatewayFails_ThrowsRuntimeException() {
				// Arrange
				RuntimeException aiException = new RuntimeException("AI Gateway connection failed");
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), any())).thenReturn("Test prompt");
				when(aiGatewayClient.generate(any())).thenThrow(aiException);

				// Act & Assert
				RuntimeException exception =
						assertThrows(
								RuntimeException.class,
								() ->
										recommendationCalculationService.calculateRecommendationsForProject(
												testProjectInput));

				assertEquals("AI Gateway connection failed", exception.getMessage());
			}

			@Test
			@DisplayName("Should throw RuntimeException when KPI extraction fails")
			void calculateRecommendationsForProject_KpiExtractionFails_ThrowsRuntimeException() {
				// Arrange
				RuntimeException kpiException = new RuntimeException("KPI data fetch failed");
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenThrow(kpiException);

				// Act & Assert
				RuntimeException exception =
						assertThrows(
								RuntimeException.class,
								() ->
										recommendationCalculationService.calculateRecommendationsForProject(
												testProjectInput));

				assertEquals("KPI data fetch failed", exception.getMessage());

				verify(promptService, never()).getBatchProjectLevelPrompt(anyMap(), any());
				verify(aiGatewayClient, never()).generate(any());
			}

			@Test
			@DisplayName("Should throw IllegalStateException when TTL config not found")
			void calculateRecommendationsForProject_MissingTTLConfig_ThrowsIllegalStateException()
					throws Exception {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), any())).thenReturn("Test prompt");
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn((testRecommendation));
				when(ttlIndexConfigProperties.getConfigs()).thenReturn(new HashMap<>());

				// Act & Assert
				IllegalStateException exception =
						assertThrows(
								IllegalStateException.class,
								() ->
										recommendationCalculationService.calculateRecommendationsForProject(
												testProjectInput));

				assertTrue(exception.getMessage().contains("TTL configuration"));
			}

			@Test
			@DisplayName("Should throw NullPointerException when project input is null")
			void calculateRecommendationsForProject_NullInput_ThrowsNullPointerException() {
				// Act & Assert
				assertThrows(
						NullPointerException.class,
						() -> recommendationCalculationService.calculateRecommendationsForProject(null));
			}

			@Test
			@DisplayName("Should throw IllegalStateException when config validation errors exist")
			void calculateRecommendationsForProject_ConfigValidationErrors_ThrowsIllegalStateException() {
				// Arrange
				Set<String> validationErrors = Set.of("Invalid persona", "Missing KPI list");
				when(recommendationCalculationConfig.getConfigValidationErrors())
						.thenReturn(validationErrors);

				// Act & Assert
				IllegalStateException exception =
						assertThrows(
								IllegalStateException.class,
								() ->
										recommendationCalculationService.calculateRecommendationsForProject(
												testProjectInput));

				assertTrue(exception.getMessage().contains("config validation errors"));
				assertTrue(exception.getMessage().contains("Invalid persona"));
				assertTrue(exception.getMessage().contains("Missing KPI list"));

				verify(kpiDataExtractionService, never()).fetchKpiDataForProject(any());
				verify(promptService, never()).getBatchProjectLevelPrompt(anyMap(), any());
				verify(aiGatewayClient, never()).generate(any());
			}

			@Test
			@DisplayName("Should throw IllegalStateException when no KPI data is available")
			void calculateRecommendationsForProject_NoKpiData_ThrowsIllegalStateException() {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(new HashMap<>()); // Empty
				// KPI
				// data

				// Act & Assert
				IllegalStateException exception =
						assertThrows(
								IllegalStateException.class,
								() ->
										recommendationCalculationService.calculateRecommendationsForProject(
												testProjectInput));

				assertTrue(exception.getMessage().contains("No KPI data available"));
				assertTrue(exception.getMessage().contains("test-project-id"));

				verify(promptService, never()).getBatchProjectLevelPrompt(anyMap(), any());
				verify(aiGatewayClient, never()).generate(any());
			}

			@Test
			@DisplayName("Should throw IllegalStateException when project-level prompt generation fails")
			void calculateRecommendationsForProject_ProjectPromptFails_ThrowsIllegalStateException() {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), any()))
						.thenReturn(null); // Null prompt

				// Act & Assert
				IllegalStateException exception =
						assertThrows(
								IllegalStateException.class,
								() ->
										recommendationCalculationService.calculateRecommendationsForProject(
												testProjectInput));

				assertTrue(
						exception.getMessage().contains("Failed to generate PROJECT_LEVEL-level prompt"));

				verify(aiGatewayClient, never()).generate(any());
			}

			@Test
			@DisplayName("Should throw IllegalStateException when project-level prompt is empty")
			void calculateRecommendationsForProject_EmptyProjectPrompt_ThrowsIllegalStateException() {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), any()))
						.thenReturn(""); // Empty prompt

				// Act & Assert
				IllegalStateException exception =
						assertThrows(
								IllegalStateException.class,
								() ->
										recommendationCalculationService.calculateRecommendationsForProject(
												testProjectInput));

				assertTrue(
						exception.getMessage().contains("Failed to generate PROJECT_LEVEL-level prompt"));

				verify(aiGatewayClient, never()).generate(any());
			}

			@Test
			@DisplayName("Should continue processing when individual KPI recommendation fails")
			void calculateRecommendationsForProject_KpiRecommendationFails_ContinuesProcessing()
					throws Exception {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD)))
						.thenReturn("Test prompt");
				when(promptService.getBatchKpiLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD), any()))
						.thenReturn("Test KPI prompt");
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn(testRecommendation);
				when(ttlIndexConfigProperties.getConfigs())
						.thenReturn(Map.of("recommendation-calculation", ttlConfig));

				// Mock builder to fail for kpi14 but succeed for kpi17
				when(recommendationActionPlanBuilder.buildProjectLevelPlan(
								eq(testProjectInput), eq(Persona.ENGINEERING_LEAD), eq(testAiResponse)))
						.thenReturn(projectLevelRecommendation);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi14")))
						.thenThrow(new RuntimeException("KPI 14 processing failed"));
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi17")))
						.thenReturn(kpiLevelRecommendation2);

				// Act
				List<RecommendationsActionPlan> results =
						recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

				// Assert
				assertEquals(2, results.size()); // 1 PROJECT + 1 successful KPI
				assertEquals(
						1,
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.PROJECT_LEVEL)
								.count());
				assertEquals(
						1, results.stream().filter(r -> r.getLevel() == RecommendationLevel.KPI_LEVEL).count());
				assertEquals(
						"kpi17",
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.KPI_LEVEL)
								.findFirst()
								.get()
								.getKpiId());

				verify(aiGatewayClient, times(2)).generate(any()); // 1 project + 1 successful KPI
			}

			@Test
			@DisplayName("Should skip KPIs with no data after filtering")
			void calculateRecommendationsForProject_KpiNoDataAfterFiltering_SkipsKpi() throws Exception {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD)))
						.thenReturn("Test prompt");
				when(promptService.getBatchKpiLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD), any()))
						.thenReturn("Test KPI prompt");
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn(testRecommendation);
				when(ttlIndexConfigProperties.getConfigs())
						.thenReturn(Map.of("recommendation-calculation", ttlConfig));

				// Mock builder to return null for kpi14 (no data) but succeed for kpi17
				when(recommendationActionPlanBuilder.buildProjectLevelPlan(
								eq(testProjectInput), eq(Persona.ENGINEERING_LEAD), eq(testAiResponse)))
						.thenReturn(projectLevelRecommendation);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi14")))
						.thenReturn(null); // No data for kpi14
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi17")))
						.thenReturn(kpiLevelRecommendation2);

				// Act
				List<RecommendationsActionPlan> results =
						recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

				// Assert
				assertEquals(2, results.size()); // 1 PROJECT + 1 KPI with data
				assertEquals(
						1,
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.PROJECT_LEVEL)
								.count());
				assertEquals(
						1, results.stream().filter(r -> r.getLevel() == RecommendationLevel.KPI_LEVEL).count());
				assertEquals(
						"kpi17",
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.KPI_LEVEL)
								.findFirst()
								.get()
								.getKpiId());

				verify(aiGatewayClient, times(2)).generate(any()); // 1 project + 1 KPI with data
			}
		}

		@Nested
		@DisplayName("Integration Scenarios")
		class IntegrationScenarios {

			@Test
			@DisplayName("Should pass correct prompt to AI Gateway")
			void calculateRecommendationsForProject_PassesCorrectPrompt() throws Exception {
				// Arrange
				String expectedPrompt = "Custom AI prompt for ENGINEERING_LEAD";
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(testKpiData, Persona.ENGINEERING_LEAD))
						.thenReturn(expectedPrompt);
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn((testRecommendation));
				when(ttlIndexConfigProperties.getConfigs())
						.thenReturn(Map.of("recommendation-calculation", ttlConfig));
				when(recommendationActionPlanBuilder.buildProjectLevelPlan(
								eq(testProjectInput), eq(Persona.ENGINEERING_LEAD), eq(testAiResponse)))
						.thenReturn(projectLevelRecommendation);

				// Act
				recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

				// Assert
				ArgumentCaptor<ChatGenerationRequest> requestCaptor =
						ArgumentCaptor.forClass(ChatGenerationRequest.class);
				verify(aiGatewayClient).generate(requestCaptor.capture());
				assertEquals(expectedPrompt, requestCaptor.getValue().getPrompt());
			}

			@Test
			@DisplayName("Should use enabled persona from configuration")
			void calculateRecommendationsForProject_UsesConfiguredPersona() throws Exception {
				// Arrange
				CalculationConfig deliveryLeadConfig = new CalculationConfig();
				deliveryLeadConfig.setEnabledPersona(Persona.PROJECT_ADMIN);
				deliveryLeadConfig.setKpiList(List.of("kpi14"));

				when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(deliveryLeadConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), eq(Persona.PROJECT_ADMIN)))
						.thenReturn("Test prompt");
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn((testRecommendation));
				when(ttlIndexConfigProperties.getConfigs())
						.thenReturn(Map.of("recommendation-calculation", ttlConfig));
				// Mock builder for PROJECT_ADMIN persona
				RecommendationsActionPlan projectAdminRecommendation =
						RecommendationsActionPlan.builder()
								.basicProjectConfigId("test-project-id")
								.projectName("Test Project")
								.persona(Persona.PROJECT_ADMIN)
								.level(RecommendationLevel.PROJECT_LEVEL)
								.recommendations(testRecommendation)
								.metadata(
										com.publicissapient.kpidashboard.common.model.recommendation.batch
												.RecommendationMetadata.builder()
												.persona(Persona.PROJECT_ADMIN)
												.requestedKpis(List.of("kpi14"))
												.build())
								.createdAt(Instant.now())
								.expiresOn(Instant.now().plusSeconds(30 * 24 * 60 * 60))
								.build();
				RecommendationsActionPlan kpiAdminRecommendation =
						RecommendationsActionPlan.builder()
								.basicProjectConfigId("test-project-id")
								.projectName("Test Project")
								.persona(Persona.PROJECT_ADMIN)
								.level(RecommendationLevel.KPI_LEVEL)
								.kpiId("kpi14")
								.recommendations(testRecommendation)
								.metadata(
										com.publicissapient.kpidashboard.common.model.recommendation.batch
												.RecommendationMetadata.builder()
												.persona(Persona.PROJECT_ADMIN)
												.requestedKpis(List.of("kpi14"))
												.build())
								.createdAt(Instant.now())
								.expiresOn(Instant.now().plusSeconds(30 * 24 * 60 * 60))
								.build();
				when(recommendationActionPlanBuilder.buildProjectLevelPlan(
								eq(testProjectInput), eq(Persona.PROJECT_ADMIN), eq(testAiResponse)))
						.thenReturn(projectAdminRecommendation);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput), eq(Persona.PROJECT_ADMIN), eq(testAiResponse), eq("kpi14")))
						.thenReturn(kpiAdminRecommendation);

				// Act
				List<RecommendationsActionPlan> results =
						recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

				// Assert
				assertEquals(2, results.size()); // 1 PROJECT + 1 KPI level recommendation
				RecommendationsActionPlan projectRecommendation =
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.PROJECT_LEVEL)
								.findFirst()
								.orElseThrow();
				assertEquals(Persona.PROJECT_ADMIN, projectRecommendation.getPersona());
				assertEquals(Persona.PROJECT_ADMIN, projectRecommendation.getMetadata().getPersona());
				verify(promptService).getBatchProjectLevelPrompt(testKpiData, Persona.PROJECT_ADMIN);
			}

			@Test
			@DisplayName("Should handle builder returning null for some recommendations gracefully")
			void calculateRecommendationsForProject_BuilderReturnsNullForSome_SkipsNulls()
					throws Exception {
				// Arrange
				when(recommendationCalculationConfig.getCalculationConfig())
						.thenReturn(testCalculationConfig);
				when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput))
						.thenReturn(testKpiData);
				when(promptService.getBatchProjectLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD)))
						.thenReturn("Test prompt");
				when(promptService.getBatchKpiLevelPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD), any()))
						.thenReturn("Test KPI prompt");
				when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
				when(recommendationResponseParser.parseRecommendation(testAiResponse))
						.thenReturn(testRecommendation);
				when(ttlIndexConfigProperties.getConfigs())
						.thenReturn(Map.of("recommendation-calculation", ttlConfig));

				// Mock builder to return null for one KPI but succeed for others
				when(recommendationActionPlanBuilder.buildProjectLevelPlan(
								eq(testProjectInput), eq(Persona.ENGINEERING_LEAD), eq(testAiResponse)))
						.thenReturn(projectLevelRecommendation);
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi14")))
						.thenReturn(null); // Builder returns null for kpi14
				when(recommendationActionPlanBuilder.buildKpiLevelPlan(
								eq(testProjectInput),
								eq(Persona.ENGINEERING_LEAD),
								eq(testAiResponse),
								eq("kpi17")))
						.thenReturn(kpiLevelRecommendation2);

				// Act
				List<RecommendationsActionPlan> results =
						recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

				// Assert
				assertEquals(2, results.size()); // 1 PROJECT + 1 valid KPI (null filtered out)
				assertEquals(
						1,
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.PROJECT_LEVEL)
								.count());
				assertEquals(
						1, results.stream().filter(r -> r.getLevel() == RecommendationLevel.KPI_LEVEL).count());
				assertEquals(
						"kpi17",
						results.stream()
								.filter(r -> r.getLevel() == RecommendationLevel.KPI_LEVEL)
								.findFirst()
								.get()
								.getKpiId());

				verify(aiGatewayClient, times(2)).generate(any()); // 1 project + 1 successful KPI
			}
		}
	}
}
