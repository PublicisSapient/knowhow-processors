/*
 *  Copyright 2024 <Sapient Corporation>
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

package com.publicissapient.kpidashboard.job.productivitycalculation.writer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.model.productivity.calculation.Productivity;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.AiDataProcessorConstants;
import com.publicissapient.kpidashboard.job.productivitycalculation.service.ProductivityCalculationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ProjectItemWriter implements ItemWriter<Productivity> {

	private final ProductivityCalculationService productivityCalculationService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Override
	public void write(@NonNull Chunk<? extends Productivity> chunk) {
		// Filter out nulls
		List<Productivity> itemsToSave = chunk.getItems().stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		log.info("{} Received chunk items for inserting into database with size: {} from {} projects",
				AiDataProcessorConstants.LOG_PREFIX_PRODUCTIVITY, itemsToSave.size(), chunk.size());

		if (!itemsToSave.isEmpty()) {
			// Save productivity data
			productivityCalculationService.saveAll(itemsToSave);
			log.info("{} Successfully saved {} productivity documents",
					AiDataProcessorConstants.LOG_PREFIX_PRODUCTIVITY, itemsToSave.size());

			// Save execution trace logs per project
			itemsToSave.forEach(this::saveProjectExecutionTraceLog);
		}
	}

	/**
	 * Creates or updates execution trace log for a project following the standard pattern.
	 *
	 * @param productivity The productivity containing project metadata
	 */
	private void saveProjectExecutionTraceLog(Productivity productivity) {
		String projectId = productivity.getHierarchyEntityNodeId();
		processorExecutionTraceLogService.upsertTraceLog(
				AiDataProcessorConstants.PRODUCTIVITY_JOB,
				projectId,
				true,
				null);
	}
}
