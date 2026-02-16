/*
 *  Copyright 2024 Sapient Corporation
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

package com.publicissapient.kpidashboard.job.recommendationcalculation.builder;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationMetadata;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.config.mongo.TTLIndexConfigProperties;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.parser.BatchRecommendationResponseParser;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Builder service responsible for constructing RecommendationsActionPlan. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationActionPlanBuilder {

	public static final String RECOMMENDATION_CALCULATION = "recommendation-calculation";

	private final BatchRecommendationResponseParser recommendationResponseParser;
	private final RecommendationCalculationConfig recommendationCalculationConfig;
	private final TTLIndexConfigProperties ttlIndexConfigProperties;

	/**
	 * Builds PROJECT-level recommendation action plan.
	 *
	 * @param projectInput project input data
	 * @param persona user persona
	 * @param response AI response
	 * @return complete PROJECT-level action plan
	 * @throws Exception if parsing fails
	 */
	public RecommendationsActionPlan buildProjectLevelPlan(
			ProjectInputDTO projectInput, Persona persona, ChatGenerationResponseDTO response)
			throws Exception {

		List<String> allKpiIds = recommendationCalculationConfig.getCalculationConfig().getKpiList();
		RecommendationMetadata metadata =
				RecommendationMetadata.builder().requestedKpis(allKpiIds).persona(persona).build();

		return buildRecommendationPlan(
				projectInput, persona, response, RecommendationLevel.PROJECT_LEVEL, null, metadata);
	}

	/**
	 * Builds KPI-level recommendation action plan.
	 *
	 * @param projectInput project input data
	 * @param persona user persona
	 * @param response AI response
	 * @param kpiId KPI identifier
	 * @return complete KPI-level action plan
	 * @throws Exception if parsing fails
	 */
	public RecommendationsActionPlan buildKpiLevelPlan(
			ProjectInputDTO projectInput,
			Persona persona,
			ChatGenerationResponseDTO response,
			String kpiId)
			throws Exception {

		RecommendationMetadata metadata =
				RecommendationMetadata.builder().requestedKpis(List.of(kpiId)).persona(persona).build();

		return buildRecommendationPlan(
				projectInput, persona, response, RecommendationLevel.KPI_LEVEL, kpiId, metadata);
	}

	/**
	 * Common builder method for all recommendation levels.
	 *
	 * @param projectInput project input data
	 * @param persona user persona
	 * @param response AI response
	 * @param level recommendation level
	 * @param kpiId KPI ID (null for PROJECT level, populated for KPI level)
	 * @param metadata recommendation metadata
	 * @return complete recommendation action plan
	 * @throws Exception if parsing fails
	 */
	private RecommendationsActionPlan buildRecommendationPlan(
			ProjectInputDTO projectInput,
			Persona persona,
			ChatGenerationResponseDTO response,
			RecommendationLevel level,
			String kpiId,
			RecommendationMetadata metadata)
			throws Exception {

		Instant now = Instant.now();

		// Parse and validate AI response
		Recommendation recommendation = recommendationResponseParser.parseRecommendation(response);

		// Build action plan with common fields
		return RecommendationsActionPlan.builder()
				.basicProjectConfigId(projectInput.basicProjectConfigId())
				.projectName(projectInput.name())
				.persona(persona)
				.level(level)
				.kpiId(kpiId)
				.createdAt(now)
				.expiresOn(now.plusSeconds(getTtlExpirationSeconds()))
				.recommendations(recommendation)
				.metadata(metadata)
				.build();
	}

	/**
	 * Calculates TTL expiration duration in seconds from configuration.
	 *
	 * @return TTL expiration time in seconds
	 * @throws IllegalStateException if TTL configuration not found
	 */
	private long getTtlExpirationSeconds() {
		TTLIndexConfigProperties.TTLIndexConfig ttlConfig =
				ttlIndexConfigProperties.getConfigs().get(RECOMMENDATION_CALCULATION);

		if (ttlConfig == null) {
			log.error(
					"{} TTL configuration 'recommendation-calculation' not found in mongo.ttl-index.configs",
					JobConstants.LOG_PREFIX_RECOMMENDATION);
			throw new IllegalStateException(
					"TTL configuration for recommendation-calculation is not configured");
		}

		return ttlConfig.getTimeUnit().toSeconds(ttlConfig.getExpiration());
	}
}
