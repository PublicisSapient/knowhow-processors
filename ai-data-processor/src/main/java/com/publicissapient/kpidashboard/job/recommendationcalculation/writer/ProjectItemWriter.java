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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.AiDataProcessorConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Batch ItemWriter for persisting recommendation documents.
 */
@Slf4j
@RequiredArgsConstructor
public class ProjectItemWriter implements ItemWriter<RecommendationsActionPlan> {
	
	private final RecommendationRepository recommendationRepository;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;
	
	/**
	 * Writes a chunk of recommendations to the database. Filters out null items,
	 * saves recommendations, and updates execution trace logs.
	 * 
	 * @param chunk
	 *            the chunk of recommendations to persist (must not be null)
	 * @throws IllegalArgumentException
	 *             if chunk is null
	 */
	@Override
	public void write(@NonNull Chunk<? extends RecommendationsActionPlan> chunk) {
		// Filter out nulls
		List<RecommendationsActionPlan> itemsToSave = chunk.getItems().stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		log.info("{} Received chunk items for inserting into database with size: {} recommendations from {} projects",
				AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, itemsToSave.size(), chunk.size());
		
		if (!itemsToSave.isEmpty()) {
			// Save recommendations
			recommendationRepository.saveAll(itemsToSave);
			log.info("{} Successfully saved {} recommendation documents",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, itemsToSave.size());
			
			// Save execution trace logs per project
			itemsToSave.forEach(this::saveProjectExecutionTraceLog);
		}
	}
	
	/**
	 * Creates or updates execution trace log for a project.
	 *
	 * @param recommendation The recommendation containing project metadata
	 */
	private void saveProjectExecutionTraceLog(RecommendationsActionPlan recommendation) {
		String projectId = recommendation.getBasicProjectConfigId();
		processorExecutionTraceLogService.upsertTraceLog(
				AiDataProcessorConstants.RECOMMENDATION_JOB,
				projectId,
				true,
				null);
	}
}
