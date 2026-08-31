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

package com.publicissapient.kpidashboard.job.epichygienecalculation.processor;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.batch.item.ItemProcessor;

import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.epichygienecalculation.service.EpicHygieneCalculationService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Batch {@link ItemProcessor} that turns one project into its Epic Hygiene snapshot.
 *
 * <p>Retries and the fallback live in {@link EpicHygieneCalculationService}; anything that still
 * escapes is traced and the item is filtered out ({@code null}) so a single bad project can never
 * fail the whole chunk.
 */
@Slf4j
@RequiredArgsConstructor
public class EpicHygieneProjectItemProcessor
		implements ItemProcessor<ProjectInputDTO, EpicHygieneData> {

	private final EpicHygieneCalculationService calculationService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Override
	public EpicHygieneData process(@Nonnull ProjectInputDTO projectInput) {
		String projectId = projectInput.basicProjectConfigId();
		try {
			log.info("{} Processing project {}", JobConstants.LOG_PREFIX_EPIC_HYGIENE, projectId);
			return calculationService.computeForProject(projectInput);
		} catch (Exception ex) {
			log.error(
					"{} Failed to process project {}: {}",
					JobConstants.LOG_PREFIX_EPIC_HYGIENE,
					projectId,
					ex.getMessage(),
					ex);
			processorExecutionTraceLogService.upsertTraceLog(
					JobConstants.JOB_EPIC_HYGIENE_CALCULATION,
					projectId,
					false,
					String.format(
							"Processing failed for project %s: %s — %s. Root cause: %s",
							projectInput.name(),
							ex.getClass().getSimpleName(),
							ex.getMessage(),
							ExceptionUtils.getRootCauseMessage(ex)));
			return null;
		}
	}
}
