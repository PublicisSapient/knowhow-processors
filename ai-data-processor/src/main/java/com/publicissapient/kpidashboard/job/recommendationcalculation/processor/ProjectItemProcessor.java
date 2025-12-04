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

package com.publicissapient.kpidashboard.job.recommendationcalculation.processor;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.AiDataProcessorConstants;
import com.publicissapient.kpidashboard.job.recommendationcalculation.service.RecommendationCalculationService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

/**
 * Spring Batch ItemProcessor for processing project recommendations.
 */
@Slf4j
@RequiredArgsConstructor
public class ProjectItemProcessor implements ItemProcessor<ProjectInputDTO, RecommendationsActionPlan> {

	private final RecommendationCalculationService recommendationCalculationService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Override
	public RecommendationsActionPlan process(@Nonnull ProjectInputDTO projectInputDTO) throws Exception {
		try {
			log.debug("{} Starting recommendation calculation for project with nodeId: {}",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInputDTO.nodeId());

			RecommendationsActionPlan recommendation = recommendationCalculationService
					.calculateRecommendationsForProject(projectInputDTO);

			if (recommendation == null) {
				log.warn("{} No recommendation generated for project: {}",
						AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInputDTO.name());
				return null;
			}

			log.debug("{} Generated recommendation plan for project: {} with persona: {}",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInputDTO.name(),
					recommendation.getMetadata().getPersona());
			return recommendation;
		} catch (Exception e) {
			log.error("{} Failed to process project: {} (nodeId: {})",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInputDTO.name(),
					projectInputDTO.nodeId(), e);

			// Save failure trace log
			String errorMessage = String.format("Processing failed: %s - %s", e.getClass().getSimpleName(),
					e.getMessage());
			processorExecutionTraceLogService.upsertTraceLog(AiDataProcessorConstants.RECOMMENDATION_JOB,
					projectInputDTO.nodeId(), false, errorMessage);

			// Return null to skip this projectInputDTO
			return null;
		}
	}
}
