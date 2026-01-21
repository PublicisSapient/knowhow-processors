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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.recommendationcalculation.builder.RecommendationActionPlanBuilder;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for orchestrating AI-based recommendation generation. Coordinates data
 * extraction, prompt building, AI generation, and document persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationCalculationService {

	private final AiGatewayClient aiGatewayClient;
	private final KpiDataExtractionService kpiDataExtractionService;
	private final PromptService promptService;
	private final RecommendationActionPlanBuilder recommendationActionPlanBuilder;
	private final RecommendationCalculationConfig recommendationCalculationConfig;

	/**
	 * Calculates AI-generated recommendations for both PROJECT and KPI levels. Orchestrates KPI data
	 * extraction (once), prompt building, AI generation for each level.
	 *
	 * @param projectInput the project input containing hierarchy and sprint information (must not be
	 *     null)
	 * @return List of recommendation action plans (1 PROJECT + N KPI level documents)
	 * @throws IllegalStateException if AI response parsing or validation fails or if configuration is
	 *     invalid
	 */
	public List<RecommendationsActionPlan> calculateRecommendationsForProject(
			@NonNull ProjectInputDTO projectInput) throws Exception {
		if (CollectionUtils.isNotEmpty(recommendationCalculationConfig.getConfigValidationErrors())) {
			throw new IllegalStateException(
					String.format(
							"The following config validation errors occurred: %s",
							String.join(
									CommonConstant.COMMA,
									recommendationCalculationConfig.getConfigValidationErrors())));
		}

		Persona persona = recommendationCalculationConfig.getCalculationConfig().getEnabledPersona();
		List<String> configuredKpiIds =
				recommendationCalculationConfig.getCalculationConfig().getKpiList();

		log.info(
				"{} Calculating recommendations for project: {} ({}) - Persona: {}, KPIs: {}",
				JobConstants.LOG_PREFIX_RECOMMENDATION,
				projectInput.name(),
				projectInput.basicProjectConfigId(),
				persona.getDisplayName(),
				configuredKpiIds);

		// Fetch KPI data once
		Map<Pair<String, String>, Object> allKpiData =
				kpiDataExtractionService.fetchKpiDataForProject(projectInput);

		if (MapUtils.isEmpty(allKpiData)) {
			throw new IllegalStateException(
					String.format(
							"No KPI data available for project: %s (%s) - cannot generate recommendations",
							projectInput.name(), projectInput.basicProjectConfigId()));
		}

		List<RecommendationsActionPlan> allRecommendations = new ArrayList<>();

		// 1. Generate PROJECT_LEVEL recommendation
		RecommendationsActionPlan projectLevelRecommendation =
				calculateProjectLevelRecommendation(projectInput, persona, allKpiData);
		allRecommendations.add(projectLevelRecommendation);

		// 2. Generate KPI_LEVEL recommendations for each KPI
		int successfulKpiCount = 0;
		if (CollectionUtils.isNotEmpty(configuredKpiIds)) {
			Map<String, Pair<String, String>> kpiIdToKeyMap =
					allKpiData.keySet().stream().collect(Collectors.toMap(Pair::getLeft, p -> p));

			for (String kpiId : configuredKpiIds) {
				try {
					log.debug(
							"{} Processing {}-level recommendation for KPI: {} in project: {} ({})",
							JobConstants.LOG_PREFIX_RECOMMENDATION,
							RecommendationLevel.KPI_LEVEL,
							kpiId,
							projectInput.name(),
							projectInput.basicProjectConfigId());

					RecommendationsActionPlan kpiLevelRecommendation =
							calculateRecommendationForKpi(
									projectInput, persona, allKpiData, kpiIdToKeyMap, kpiId);
					if (kpiLevelRecommendation != null) {
						allRecommendations.add(kpiLevelRecommendation);
						successfulKpiCount++;
						log.info(
								"{} Successfully generated {}-level recommendation for KPI: {} in project: {} ({})",
								JobConstants.LOG_PREFIX_RECOMMENDATION,
								RecommendationLevel.KPI_LEVEL,
								kpiId,
								projectInput.name(),
								projectInput.basicProjectConfigId());
					}
				} catch (Exception e) {
					// Log KPI_LEVEL failure but continue with other KPIs
					log.warn(
							"{} Failed to generate {}-level recommendation for KPI: {} in project: {} ({}). Will continue with other KPIs. Error: {}",
							JobConstants.LOG_PREFIX_RECOMMENDATION,
							RecommendationLevel.KPI_LEVEL,
							kpiId,
							projectInput.name(),
							projectInput.basicProjectConfigId(),
							e.getMessage(),
							e);
				}
			}
		}

		int skippedKpiCount = CollectionUtils.size(configuredKpiIds) - successfulKpiCount;
		log.info(
				"{} Generated {} total recommendations for project {} (1 PROJECT + {} KPIs, {} skipped due to no data)",
				JobConstants.LOG_PREFIX_RECOMMENDATION,
				allRecommendations.size(),
				projectInput.name(),
				successfulKpiCount,
				skippedKpiCount);

		return allRecommendations;
	}

	/**
	 * Calculates PROJECT-level recommendation using all KPI data.
	 *
	 * @param projectInput the project input data
	 * @param persona the persona for recommendations
	 * @param allKpiData map of all KPI data
	 * @return PROJECT-level recommendation action plan
	 * @throws Exception if generation fails
	 */
	private RecommendationsActionPlan calculateProjectLevelRecommendation(
			ProjectInputDTO projectInput, Persona persona, Map<Pair<String, String>, Object> allKpiData)
			throws Exception {

		// Build prompt using all KPI data
		String prompt = promptService.getBatchProjectLevelPrompt(allKpiData, persona);

		if (StringUtils.isBlank(prompt)) {
			throw new IllegalStateException(
					String.format(
							"Failed to generate %s-level prompt for project: %s",
							RecommendationLevel.PROJECT_LEVEL, projectInput.basicProjectConfigId()));
		}

		ChatGenerationResponseDTO response =
				generateAiRecommendation(
						prompt,
						String.format(
								"%s-level for project: %s",
								RecommendationLevel.PROJECT_LEVEL, projectInput.basicProjectConfigId()));

		return recommendationActionPlanBuilder.buildProjectLevelPlan(projectInput, persona, response);
	}

	/**
	 * Calculates KPI-level recommendation for a single KPI.
	 *
	 * @param projectInput the project input data
	 * @param persona the persona for recommendations
	 * @param allKpiData map of all KPI data with Pair<kpiId, kpiName> as keys
	 * @param kpiIdToKeyMap pre-built map from kpiId to Pair<kpiId, kpiName> for O(1) lookup
	 * @param kpiId the specific KPI ID to generate recommendations for
	 * @return KPI-level recommendation action plan
	 * @throws Exception if generation fails
	 */
	private RecommendationsActionPlan calculateRecommendationForKpi(
			ProjectInputDTO projectInput,
			Persona persona,
			Map<Pair<String, String>, Object> allKpiData,
			Map<String, Pair<String, String>> kpiIdToKeyMap,
			String kpiId)
			throws Exception {

		if (!kpiIdToKeyMap.containsKey(kpiId)) {
			log.warn(
					"{} KPI ID {} not found in fetched KPI data for project: {} ({})",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					kpiId,
					projectInput.name(),
					projectInput.basicProjectConfigId());
			return null;
		}

		Map<Pair<String, String>, Object> singleKpiData =
				kpiDataExtractionService.filterKpiDataWithIndex(allKpiData, kpiIdToKeyMap, kpiId);

		// Gracefully skip KPIs with no data (prevents unnecessary AI Gateway calls)
		if (MapUtils.isEmpty(singleKpiData)) {
			log.warn(
					"{} Skipping {}-level recommendation for KPI: {} in project: {} ({}) - no data available",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					RecommendationLevel.KPI_LEVEL,
					kpiId,
					projectInput.name(),
					projectInput.basicProjectConfigId());
			return null; // Return null to signal no recommendation for this KPI
		}

		// Build KPI-specific prompt
		String prompt = promptService.getBatchKpiLevelPrompt(singleKpiData, persona, kpiId);

		if (StringUtils.isBlank(prompt)) {
			throw new IllegalStateException(
					String.format(
							"Failed to generate %s-level prompt for KPI: %s in project: %s (%s)",
							RecommendationLevel.KPI_LEVEL,
							kpiId,
							projectInput.name(),
							projectInput.basicProjectConfigId()));
		}

		ChatGenerationResponseDTO response =
				generateAiRecommendation(
						prompt,
						String.format(
								"%s-level for project: %s, KPI: %s",
								RecommendationLevel.KPI_LEVEL, projectInput.basicProjectConfigId(), kpiId));

		return recommendationActionPlanBuilder.buildKpiLevelPlan(
				projectInput, persona, response, kpiId);
	}

	/**
	 * Common method to generate AI recommendation from prompt. Encapsulates AI Gateway interaction
	 * and error handling.
	 *
	 * @param prompt the prompt to send to AI
	 * @param errorContext context for error messages
	 * @return AI response DTO
	 * @throws IllegalStateException if AI returns null response
	 */
	private ChatGenerationResponseDTO generateAiRecommendation(String prompt, String errorContext) {
		ChatGenerationRequest request = ChatGenerationRequest.builder().prompt(prompt).build();
		ChatGenerationResponseDTO response = aiGatewayClient.generate(request);

		if (response == null) {
			throw new IllegalStateException("AI Gateway returned null response for " + errorContext);
		}

		return response;
	}
}
