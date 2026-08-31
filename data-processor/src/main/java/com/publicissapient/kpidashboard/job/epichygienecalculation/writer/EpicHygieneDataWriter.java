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

package com.publicissapient.kpidashboard.job.epichygienecalculation.writer;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.common.repository.jira.EpicHygieneDataRepository;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists an Epic Hygiene chunk into the {@code epic_hygiene_data} collection.
 *
 * <p>Only one snapshot per project is kept: the previous document of a project is removed before
 * the new one is stored, so consumers never have to de-duplicate.
 */
@Slf4j
@RequiredArgsConstructor
public class EpicHygieneDataWriter implements ItemWriter<EpicHygieneData> {

	private final EpicHygieneDataRepository epicHygieneDataRepository;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Override
	public void write(@NonNull Chunk<? extends EpicHygieneData> chunk) {
		// Iterate the chunk instead of Chunk#getItems(): the latter copies into an immutable
		// list and would blow up on a null item.
		List<EpicHygieneData> toSave = new ArrayList<>();
		for (EpicHygieneData item : chunk) {
			if (item != null && StringUtils.isNotBlank(item.getBasicProjectConfigId())) {
				toSave.add(item);
			}
		}

		if (toSave.isEmpty()) {
			log.debug(
					"{} No epic hygiene data to persist in this chunk", JobConstants.LOG_PREFIX_EPIC_HYGIENE);
			return;
		}

		log.info(
				"{} Persisting {} epic hygiene snapshot(s)",
				JobConstants.LOG_PREFIX_EPIC_HYGIENE,
				toSave.size());

		toSave.stream()
				.map(EpicHygieneData::getBasicProjectConfigId)
				.distinct()
				.forEach(epicHygieneDataRepository::deleteByBasicProjectConfigId);

		epicHygieneDataRepository.saveAll(toSave);

		toSave.forEach(
				savedData ->
						processorExecutionTraceLogService.upsertTraceLog(
								JobConstants.JOB_EPIC_HYGIENE_CALCULATION,
								savedData.getBasicProjectConfigId(),
								!savedData.isFallback(),
								savedData.isFallback() ? savedData.getFailureReason() : null));
	}
}
