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

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.*;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;
import com.publicissapient.kpidashboard.config.mongo.TTLIndexConfigProperties;
import com.publicissapient.kpidashboard.job.constant.AiDataProcessorConstants;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.recommendationcalculation.parser.BatchRecommendationResponseParser;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Service responsible for orchestrating AI-based recommendation generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationCalculationService {
	
	private final AiGatewayClient aiGatewayClient;
	private final KpiDataExtractionService kpiDataExtractionService;
	private final PromptService promptService;
	private final BatchRecommendationResponseParser recommendationResponseParser;
	private final RecommendationCalculationConfig recommendationCalculationConfig;
	private final TTLIndexConfigProperties ttlIndexConfigProperties;
	

	/**
	 * Calculates recommendations for a given project.
	 * Processes configured persona and returns single recommendation plan.
	 * 
	 * @param projectInput the project input containing hierarchy and sprint information
	 * @return recommendation action plan or null if calculation fails
	 * @throws IllegalStateException if configuration validation errors exist
	 */
	public RecommendationsActionPlan calculateRecommendationsForProject(ProjectInputDTO projectInput) {
		Persona persona = recommendationCalculationConfig.getCalculationConfig().getEnabledPersona();
		long startTime = System.currentTimeMillis();
		
		try {
			log.info("{} Calculating recommendations for project: {} ({}) - Persona: {}", AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION,
					projectInput.name(), projectInput.nodeId(), persona.getDisplayName());
				
			// Delegate KPI data extraction to specialized service
			Map<String, Object> kpiData = kpiDataExtractionService.fetchKpiDataForProject(projectInput);
			
			// Build prompt using PromptService with actual KPI data
			String prompt = promptService.getKpiRecommendationPrompt(kpiData, persona);
			
			ChatGenerationRequest request = ChatGenerationRequest.builder()
					.prompt(prompt)
					.build();
			
			ChatGenerationResponseDTO response = aiGatewayClient.generate(request);
			
			long processingTime = System.currentTimeMillis() - startTime;
			
			return buildRecommendationsActionPlan(
				projectInput, persona, response.content(), processingTime, kpiData.size());
			
		} catch (Exception e) {
			// Error logged and tracked in ProjectItemProcessor wrapper
			// Return null to let Spring Batch skip this failed item
			log.error("{} Error calculating recommendations for project {} persona {}: {}", 
				AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION,
				projectInput.nodeId(), persona.getDisplayName(), e.getMessage(), e);
			throw e;
		}
	}
	
	/**
	 * Builds recommendation action plan from AI response and project metadata.
	 */
	private RecommendationsActionPlan buildRecommendationsActionPlan(
			ProjectInputDTO projectInput,
			Persona persona,
			String aiResponse,
			long processingTime,
			int requestedKpiCount) {
		
		RecommendationsActionPlan plan = new RecommendationsActionPlan();
		plan.setProjectId(projectInput.nodeId()); // Use nodeId as projectId for consistency
		plan.setProjectName(projectInput.name());
		plan.setPersona(persona);
		plan.setLevel(RecommendationLevel.PROJECT_LEVEL);
		plan.setCreatedAt(Instant.now());
		
		// Set TTL expiry date from centralized mongo config
		plan.setExpiresOn(Instant.now().plusSeconds(getTtlExpirationSeconds()));
		
		// Parse AI response using BatchRecommendationResponseParser
		Recommendation recommendation = recommendationResponseParser.parseRecommendation(aiResponse);
		plan.setRecommendations(recommendation);
		
		// Build metadata with configured KPI list
		RecommendationMetadata metadata = new RecommendationMetadata();
		metadata.setRequestedKpis(recommendationCalculationConfig.getCalculationConfig().getKpiList()); // Use configured KPI list from YAML
		metadata.setPersona(persona); // Track which persona was used
		plan.setMetadata(metadata);
		
		return plan;
	}
	
	/**
	 * Calculates TTL expiration in seconds from mongo.ttl-index.configs.recommendation-calculation.
	 * This keeps the TTL logic in the service layer rather than config layer.
	 * 
	 * @return expiration time in seconds
	 */
	private long getTtlExpirationSeconds() {
		TTLIndexConfigProperties.TTLIndexConfig ttlConfig = 
			ttlIndexConfigProperties.getConfigs().get("recommendation-calculation");
		return ttlConfig.getTimeUnit().toSeconds(ttlConfig.getExpiration());
	}
}
