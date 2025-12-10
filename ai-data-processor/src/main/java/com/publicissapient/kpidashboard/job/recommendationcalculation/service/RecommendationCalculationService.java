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

import java.time.Instant;
import java.util.Map;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationMetadata;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;
import com.publicissapient.kpidashboard.config.mongo.TTLIndexConfigProperties;
import com.publicissapient.kpidashboard.job.constant.AiDataProcessorConstants;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.parser.BatchRecommendationResponseParser;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for orchestrating AI-based recommendation generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationCalculationService {
	public static final String RECOMMENDATION_CALCULATION = "recommendation-calculation";
	private final AiGatewayClient aiGatewayClient;
	private final KpiDataExtractionService kpiDataExtractionService;
	private final PromptService promptService;
	private final BatchRecommendationResponseParser recommendationResponseParser;
	private final RecommendationCalculationConfig recommendationCalculationConfig;
	private final TTLIndexConfigProperties ttlIndexConfigProperties;

	/**
	 * Calculates AI-generated recommendations for a given project. Orchestrates KPI
	 * data extraction, prompt building, AI generation, and validation.
	 * 
	 * @param projectInput
	 *            the project input containing hierarchy and sprint information
	 *            (must not be null)
	 * @return recommendation action plan with validated AI recommendations
	 * @throws IllegalStateException
	 *             if AI response parsing or validation fails
	 */
	public RecommendationsActionPlan calculateRecommendationsForProject(@NonNull ProjectInputDTO projectInput) {
		Persona persona = recommendationCalculationConfig.getCalculationConfig().getEnabledPersona();

		try {
			log.info("{} Calculating recommendations for project: {} ({}) - Persona: {}",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInput.name(), projectInput.nodeId(),
					persona.getDisplayName());

			// Delegate KPI data extraction to specialized service
			Map<String, Object> kpiData = kpiDataExtractionService.fetchKpiDataForProject(projectInput);

			// Build prompt using PromptService with actual KPI data
			String prompt = promptService.getKpiRecommendationPrompt(kpiData, persona);

			ChatGenerationRequest request = ChatGenerationRequest.builder().prompt(prompt).build();

			ChatGenerationResponseDTO response = aiGatewayClient.generate(request);

			return buildRecommendationsActionPlan(projectInput, persona, response);
		} catch (IllegalStateException e) {
			// Parsing or validation failures - rethrow as-is
			log.error("{} Validation error for project {} persona {}: {}",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInput.nodeId(), persona.getDisplayName(),
					e.getMessage(), e);
			throw e;
		} catch (RuntimeException e) {
			// API call failures, network issues - wrap with context
			log.error("{} Runtime error calculating recommendations for project {} persona {}: {}",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInput.nodeId(), persona.getDisplayName(),
					e.getMessage(), e);
			throw new IllegalStateException("Failed to calculate recommendations for project: " + projectInput.nodeId(),
					e);
		}
	}

	/**
	 * Builds recommendation action plan from AI response and project metadata.
	 * Parses AI response, validates using RecommendationValidator, and constructs
	 * complete plan.
	 * 
	 * @param projectInput
	 *            the project input data
	 * @param persona
	 *            the persona used for recommendations
	 * @param response
	 *            the AI response DTO
	 * @return complete recommendation action plan with metadata
	 * @throws IllegalStateException
	 *             if parsing or validation fails
	 */
	private RecommendationsActionPlan buildRecommendationsActionPlan(ProjectInputDTO projectInput, Persona persona,
			ChatGenerationResponseDTO response) {

		Instant now = Instant.now();

		// Parse and validate AI response
		Recommendation recommendation = recommendationResponseParser.parseRecommendation(response)
				.orElseThrow(() -> new IllegalStateException(
						"Failed to parse AI recommendation for project: " + projectInput.nodeId())); // Build metadata
		RecommendationMetadata metadata = RecommendationMetadata.builder()
				.requestedKpis(recommendationCalculationConfig.getCalculationConfig().getKpiList()).persona(persona)
				.build();

		// Build plan using builder
		return RecommendationsActionPlan.builder().basicProjectConfigId(projectInput.nodeId()).projectName(projectInput.name())
				.persona(persona).level(RecommendationLevel.PROJECT_LEVEL).createdAt(now)
				.expiresOn(now.plusSeconds(getTtlExpirationSeconds())).recommendations(recommendation)
				.metadata(metadata).build();
	}

	/**
	 * Calculates TTL expiration duration in seconds. Reads from
	 * mongo.ttl-index.configs.recommendation-calculation configuration.
	 * 
	 * @return TTL expiration time in seconds
	 * @throws IllegalStateException
	 *             if TTL configuration not found
	 */
	private long getTtlExpirationSeconds() {
		TTLIndexConfigProperties.TTLIndexConfig ttlConfig = ttlIndexConfigProperties.getConfigs()
				.get(RECOMMENDATION_CALCULATION);

		if (ttlConfig == null) {
			log.error("{} TTL configuration 'recommendation-calculation' not found in mongo.ttl-index.configs",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION);
			throw new IllegalStateException("TTL configuration for recommendation-calculation is not configured");
		}

		return ttlConfig.getTimeUnit().toSeconds(ttlConfig.getExpiration());
	}
}
