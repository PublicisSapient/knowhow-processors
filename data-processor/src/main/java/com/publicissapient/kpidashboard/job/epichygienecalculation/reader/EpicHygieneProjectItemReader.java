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

package com.publicissapient.kpidashboard.job.epichygienecalculation.reader;

import org.springframework.batch.item.ItemReader;

import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.epichygienecalculation.service.EpicHygieneProjectBatchService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Spring Batch {@link ItemReader} returning one project at a time for kpi312 evaluation. */
@Slf4j
@RequiredArgsConstructor
public class EpicHygieneProjectItemReader implements ItemReader<ProjectInputDTO> {

	private final EpicHygieneProjectBatchService projectBatchService;

	@Override
	public ProjectInputDTO read() {
		ProjectInputDTO projectInputDTO = projectBatchService.getNextProjectInputData();

		if (projectInputDTO != null) {
			log.info(
					"{} Reading project {}",
					JobConstants.LOG_PREFIX_EPIC_HYGIENE,
					projectInputDTO.basicProjectConfigId());
		}

		return projectInputDTO;
	}
}
