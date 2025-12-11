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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.CalculationConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.parser.BatchRecommendationResponseParser;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationCalculationService Tests")
class RecommendationCalculationServiceTest {

	@Mock
	private AiGatewayClient aiGatewayClient;

	@Mock
	private KpiDataExtractionService kpiDataExtractionService;

	@Mock
	private PromptService promptService;

	@Mock
	private BatchRecommendationResponseParser recommendationResponseParser;

	@Mock
	private RecommendationCalculationConfig recommendationCalculationConfig;

	@Mock
	private TTLIndexConfigProperties ttlIndexConfigProperties;

	@InjectMocks
	private RecommendationCalculationService recommendationCalculationService;

	private ProjectInputDTO testProjectInput;
	private Map<String, Object> testKpiData;
	private Recommendation testRecommendation;
	private ChatGenerationResponseDTO testAiResponse;
	private CalculationConfig testCalculationConfig;
	private TTLIndexConfigProperties.TTLIndexConfig ttlConfig;

	@BeforeEach
	void setUp() {
		testProjectInput = ProjectInputDTO.builder().nodeId("test-project-id").name("Test Project").hierarchyLevel(5)
				.hierarchyLevelId("project").build();

		testKpiData = new HashMap<>();
		testKpiData.put("Velocity", List.of("Sprint 1: 45 SP", "Sprint 2: 50 SP"));

		testRecommendation = Recommendation.builder().title("Improve Velocity")
				.description("Test recommendation description").build();

		testAiResponse = new ChatGenerationResponseDTO("Test AI response");

		testCalculationConfig = new CalculationConfig();
		testCalculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		testCalculationConfig.setKpiList(List.of("kpi14", "kpi17"));

		ttlConfig = new TTLIndexConfigProperties.TTLIndexConfig();
		ttlConfig.setExpiration(30);
		ttlConfig.setTimeUnit(TimeUnit.DAYS);
	}

	@Nested
	@DisplayName("Successful Scenarios")
	class SuccessfulScenarios {

		@Test
		@DisplayName("Should successfully calculate recommendations for project")
		void calculateRecommendationsForProject_Success() {
			// Arrange
			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(testCalculationConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenReturn(testKpiData);
			when(promptService.getKpiRecommendationPrompt(anyMap(), eq(Persona.ENGINEERING_LEAD)))
					.thenReturn("Test prompt");
			when(aiGatewayClient.generate(any(ChatGenerationRequest.class))).thenReturn(testAiResponse);
			when(recommendationResponseParser.parseRecommendation(testAiResponse))
					.thenReturn(Optional.of(testRecommendation));
			when(ttlIndexConfigProperties.getConfigs()).thenReturn(Map.of("recommendation-calculation", ttlConfig));

			// Act
			RecommendationsActionPlan result = recommendationCalculationService
					.calculateRecommendationsForProject(testProjectInput);

			// Assert
			assertNotNull(result);
			assertEquals(testProjectInput.nodeId(), result.getBasicProjectConfigId());
			assertEquals(testProjectInput.name(), result.getProjectName());
			assertEquals(Persona.ENGINEERING_LEAD, result.getPersona());
			assertEquals(RecommendationLevel.PROJECT_LEVEL, result.getLevel());
			assertNotNull(result.getRecommendations());
			assertEquals(testRecommendation.getTitle(), result.getRecommendations().getTitle());
			assertNotNull(result.getMetadata());
			assertNotNull(result.getCreatedAt());
			assertNotNull(result.getExpiresOn());

			verify(kpiDataExtractionService).fetchKpiDataForProject(testProjectInput);
			verify(promptService).getKpiRecommendationPrompt(testKpiData, Persona.ENGINEERING_LEAD);
			verify(aiGatewayClient).generate(any(ChatGenerationRequest.class));
			verify(recommendationResponseParser).parseRecommendation(testAiResponse);
		}

		@Test
		@DisplayName("Should correctly set TTL expiration from config")
		void calculateRecommendationsForProject_SetsTTLCorrectly() {
			// Arrange
			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(testCalculationConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenReturn(testKpiData);
			when(promptService.getKpiRecommendationPrompt(anyMap(), any())).thenReturn("Test prompt");
			when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
			when(recommendationResponseParser.parseRecommendation(testAiResponse))
					.thenReturn(Optional.of(testRecommendation));
			when(ttlIndexConfigProperties.getConfigs()).thenReturn(Map.of("recommendation-calculation", ttlConfig));

			Instant beforeCall = Instant.now();

			// Act
			RecommendationsActionPlan result = recommendationCalculationService
					.calculateRecommendationsForProject(testProjectInput);

			// Assert
			assertNotNull(result.getExpiresOn());
			long expectedTtlSeconds = 30 * 24 * 60 * 60; // 30 days in seconds
			long actualDiff = result.getExpiresOn().getEpochSecond() - result.getCreatedAt().getEpochSecond();
			assertEquals(expectedTtlSeconds, actualDiff);
		}

		@Test
		@DisplayName("Should build metadata with correct KPI list and persona")
		void calculateRecommendationsForProject_BuildsCorrectMetadata() {
			// Arrange
			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(testCalculationConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenReturn(testKpiData);
			when(promptService.getKpiRecommendationPrompt(anyMap(), any())).thenReturn("Test prompt");
			when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
			when(recommendationResponseParser.parseRecommendation(testAiResponse))
					.thenReturn(Optional.of(testRecommendation));
			when(ttlIndexConfigProperties.getConfigs()).thenReturn(Map.of("recommendation-calculation", ttlConfig));

			// Act
			RecommendationsActionPlan result = recommendationCalculationService
					.calculateRecommendationsForProject(testProjectInput);

			// Assert
			assertNotNull(result.getMetadata());
			assertEquals(Persona.ENGINEERING_LEAD, result.getMetadata().getPersona());
			assertEquals(testCalculationConfig.getKpiList(), result.getMetadata().getRequestedKpis());
		}
	}

	@Nested
	@DisplayName("Exception Scenarios")
	class ExceptionScenarios {

		@Test
		@DisplayName("Should throw IllegalStateException when AI response parsing fails")
		void calculateRecommendationsForProject_ParsingFails_ThrowsIllegalStateException() {
			// Arrange
			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(testCalculationConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenReturn(testKpiData);
			when(promptService.getKpiRecommendationPrompt(anyMap(), any())).thenReturn("Test prompt");
			when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
			when(recommendationResponseParser.parseRecommendation(testAiResponse)).thenReturn(Optional.empty());

			// Act & Assert
			IllegalStateException exception = assertThrows(IllegalStateException.class,
					() -> recommendationCalculationService.calculateRecommendationsForProject(testProjectInput));

			assertTrue(exception.getMessage().contains("Failed to parse AI recommendation"));
			assertTrue(exception.getMessage().contains(testProjectInput.nodeId()));
		}

		@Test
		@DisplayName("Should throw RuntimeException when AI Gateway fails")
		void calculateRecommendationsForProject_AiGatewayFails_ThrowsRuntimeException() {
			// Arrange
			RuntimeException aiException = new RuntimeException("AI Gateway connection failed");
			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(testCalculationConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenReturn(testKpiData);
			when(promptService.getKpiRecommendationPrompt(anyMap(), any())).thenReturn("Test prompt");
			when(aiGatewayClient.generate(any())).thenThrow(aiException);

			// Act & Assert
			RuntimeException exception = assertThrows(RuntimeException.class,
					() -> recommendationCalculationService.calculateRecommendationsForProject(testProjectInput));

			assertEquals("AI Gateway connection failed", exception.getMessage());
		}

		@Test
		@DisplayName("Should throw RuntimeException when KPI extraction fails")
		void calculateRecommendationsForProject_KpiExtractionFails_ThrowsRuntimeException() {
			// Arrange
			RuntimeException kpiException = new RuntimeException("KPI data fetch failed");
			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(testCalculationConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenThrow(kpiException);

			// Act & Assert
			RuntimeException exception = assertThrows(RuntimeException.class,
					() -> recommendationCalculationService.calculateRecommendationsForProject(testProjectInput));

			assertEquals("KPI data fetch failed", exception.getMessage());

			verify(promptService, never()).getKpiRecommendationPrompt(anyMap(), any());
			verify(aiGatewayClient, never()).generate(any());
		}

		@Test
		@DisplayName("Should throw IllegalStateException when TTL config not found")
		void calculateRecommendationsForProject_MissingTTLConfig_ThrowsIllegalStateException() {
			// Arrange
			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(testCalculationConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenReturn(testKpiData);
			when(promptService.getKpiRecommendationPrompt(anyMap(), any())).thenReturn("Test prompt");
			when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
			when(recommendationResponseParser.parseRecommendation(testAiResponse))
					.thenReturn(Optional.of(testRecommendation));
			when(ttlIndexConfigProperties.getConfigs()).thenReturn(new HashMap<>());

			// Act & Assert
			IllegalStateException exception = assertThrows(IllegalStateException.class,
					() -> recommendationCalculationService.calculateRecommendationsForProject(testProjectInput));

			assertTrue(exception.getMessage().contains("TTL configuration"));
		}

		@Test
		@DisplayName("Should throw NullPointerException when project input is null")
		void calculateRecommendationsForProject_NullInput_ThrowsNullPointerException() {
			// Act & Assert
			assertThrows(NullPointerException.class,
					() -> recommendationCalculationService.calculateRecommendationsForProject(null));
		}
	}

	@Nested
	@DisplayName("Integration Scenarios")
	class IntegrationScenarios {

		@Test
		@DisplayName("Should pass correct prompt to AI Gateway")
		void calculateRecommendationsForProject_PassesCorrectPrompt() {
			// Arrange
			String expectedPrompt = "Custom AI prompt for ENGINEERING_LEAD";
			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(testCalculationConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenReturn(testKpiData);
			when(promptService.getKpiRecommendationPrompt(testKpiData, Persona.ENGINEERING_LEAD))
					.thenReturn(expectedPrompt);
			when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
			when(recommendationResponseParser.parseRecommendation(testAiResponse))
					.thenReturn(Optional.of(testRecommendation));
			when(ttlIndexConfigProperties.getConfigs()).thenReturn(Map.of("recommendation-calculation", ttlConfig));

			// Act
			recommendationCalculationService.calculateRecommendationsForProject(testProjectInput);

			// Assert
			verify(promptService).getKpiRecommendationPrompt(testKpiData, Persona.ENGINEERING_LEAD);
			verify(aiGatewayClient).generate(any(ChatGenerationRequest.class));
		}

		@Test
		@DisplayName("Should use enabled persona from configuration")
		void calculateRecommendationsForProject_UsesConfiguredPersona() {
			// Arrange
			CalculationConfig deliveryLeadConfig = new CalculationConfig();
			deliveryLeadConfig.setEnabledPersona(Persona.PROJECT_ADMIN);
			deliveryLeadConfig.setKpiList(List.of("kpi14"));

			when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(deliveryLeadConfig);
			when(kpiDataExtractionService.fetchKpiDataForProject(testProjectInput)).thenReturn(testKpiData);
			when(promptService.getKpiRecommendationPrompt(anyMap(), eq(Persona.PROJECT_ADMIN)))
					.thenReturn("Test prompt");
			when(aiGatewayClient.generate(any())).thenReturn(testAiResponse);
			when(recommendationResponseParser.parseRecommendation(testAiResponse))
					.thenReturn(Optional.of(testRecommendation));
			when(ttlIndexConfigProperties.getConfigs()).thenReturn(Map.of("recommendation-calculation", ttlConfig));

			// Act
			RecommendationsActionPlan result = recommendationCalculationService
					.calculateRecommendationsForProject(testProjectInput);

			// Assert
			assertEquals(Persona.PROJECT_ADMIN, result.getPersona());
			assertEquals(Persona.PROJECT_ADMIN, result.getMetadata().getPersona());
			verify(promptService).getKpiRecommendationPrompt(testKpiData, Persona.PROJECT_ADMIN);
		}
	}
}
