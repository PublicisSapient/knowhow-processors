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

package com.publicissapient.kpidashboard.job.recommendationcalculation.writer;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Batch ItemWriter for persisting recommendation documents. Handles both PROJECT-level and
 * KPI-level recommendations.
 */
@Slf4j
@RequiredArgsConstructor
public class ProjectItemWriter implements ItemWriter<List<RecommendationsActionPlan>> {

	private final RecommendationRepository recommendationRepository;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	/**
	 * Writes a chunk of recommendation lists to the database. Flattens lists, filters out nulls,
	 * saves all recommendations (PROJECT + KPI level), and updates execution trace logs.
	 *
	 * @param chunk the chunk of recommendation lists to persist (must not be null)
	 * @throws IllegalArgumentException if chunk is null
	 */
	@Override
	public void write(@NonNull Chunk<? extends List<RecommendationsActionPlan>> chunk) {
		// Flatten nested lists and filter out nulls
		List<RecommendationsActionPlan> itemsToSave =
				chunk.getItems().stream()
						.filter(Objects::nonNull)
						.flatMap(List::stream)
						.filter(rec -> rec != null && rec.getBasicProjectConfigId() != null)
						.collect(Collectors.toList());

		if (itemsToSave.isEmpty()) {
			log.debug(
					"{} No recommendations to save in this chunk", JobConstants.LOG_PREFIX_RECOMMENDATION);
			return;
		}

		log.info(
				"{} Saving {} recommendation documents",
				JobConstants.LOG_PREFIX_RECOMMENDATION,
				itemsToSave.size());

		// Save all recommendations (both PROJECT and KPI level)
		recommendationRepository.saveAll(itemsToSave);

		// Update execution trace logs per project (deduplicate by projectId)
		Set<String> seen = new HashSet<>();
		itemsToSave.stream()
				.filter(rec -> seen.add(rec.getBasicProjectConfigId()))
				.forEach(this::saveProjectExecutionTraceLog);
	}

	/**
	 * Creates or updates execution trace log for a project.
	 *
	 * @param recommendation The recommendation containing project metadata
	 */
	private void saveProjectExecutionTraceLog(RecommendationsActionPlan recommendation) {
		String basicProjectConfigId = recommendation.getBasicProjectConfigId();
		processorExecutionTraceLogService.upsertTraceLog(
				JobConstants.JOB_RECOMMENDATION_CALCULATION, basicProjectConfigId, true, null);
	}
}
