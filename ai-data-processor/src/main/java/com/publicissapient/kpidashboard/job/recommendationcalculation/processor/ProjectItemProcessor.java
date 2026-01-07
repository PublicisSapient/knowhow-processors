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

import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.batch.item.ItemProcessor;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.recommendationcalculation.service.RecommendationCalculationService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Batch ItemProcessor for processing project recommendations. Generates both PROJECT-level
 * and KPI-level recommendations.
 */
@Slf4j
@RequiredArgsConstructor
public class ProjectItemProcessor
		implements ItemProcessor<ProjectInputDTO, List<RecommendationsActionPlan>> {

	private final RecommendationCalculationService recommendationCalculationService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	/**
	 * Processes a single project to generate AI recommendations for both PROJECT and KPI levels.
	 * Handles errors gracefully by logging and saving failure trace.
	 *
	 * @param projectInputDTO the project input data (must not be null)
	 * @return List of RecommendationsActionPlan (1 PROJECT + N KPIs) if successful, null if
	 *     processing fails
	 * @throws Exception if fatal error occurs (Spring Batch will handle retry/skip logic)
	 */
	@Override
	public List<RecommendationsActionPlan> process(@Nonnull ProjectInputDTO projectInputDTO)
			throws Exception {
		try {
			log.debug(
					"{} Starting recommendation calculation for project: {} (basicProjectConfigId: {})",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					projectInputDTO.name(),
					projectInputDTO.basicProjectConfigId());

			List<RecommendationsActionPlan> recommendations =
					recommendationCalculationService.calculateRecommendationsForProject(projectInputDTO);

			long kpiRecommendationCount = recommendations.stream()
					.filter(r -> r.getLevel() == RecommendationLevel.KPI_LEVEL)
					.count();
			long projectRecommendationCount = recommendations.size() - kpiRecommendationCount;
			log.info(
					"{} Generated {} recommendation documents for project: {} ({} PROJECT + {} KPIs)",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					recommendations.size(),
					projectInputDTO.name(),
					projectRecommendationCount,
					kpiRecommendationCount);
			return recommendations;
		} catch (Exception e) {
			log.error(
					"{} Failed to process project: {} (basicProjectConfigId: {})",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					projectInputDTO.name(),
					projectInputDTO.basicProjectConfigId(),
					e);

			// Save detailed failure trace log with more context
			String errorMessage =
					String.format(
							"Processing failed for project %s: %s - %s. Root cause: %s",
							projectInputDTO.name(),
							e.getClass().getSimpleName(),
							e.getMessage(),
							ExceptionUtils.getRootCauseMessage(e));
			processorExecutionTraceLogService.upsertTraceLog(
					JobConstants.JOB_RECOMMENDATION_CALCULATION,
					projectInputDTO.basicProjectConfigId(),
					false,
					errorMessage);

			// Return null to skip this projectInputDTO
			return null;
		}
	}
}
