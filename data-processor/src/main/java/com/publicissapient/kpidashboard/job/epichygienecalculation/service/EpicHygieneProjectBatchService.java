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

package com.publicissapient.kpidashboard.job.epichygienecalculation.service;

import java.util.Collections;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelServiceImpl;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Supplies eligible projects one at a time to the epic-hygiene reader. Projects are streamed page
 * by page (page size == chunk size) so a large installation never loads every project at once.
 *
 * <p>Epic Hygiene is a Scrum/Jira KPI, therefore Kanban and on-hold projects are excluded.
 */
@Slf4j
@Component
@JobScope
@RequiredArgsConstructor
public class EpicHygieneProjectBatchService {

	private final EpicHygieneCalculationJobConfig jobConfig;
	private final ProjectBasicConfigRepository projectBasicConfigRepository;
	private final HierarchyLevelServiceImpl hierarchyLevelService;

	private ProjectBatchProcessingParameters processingParameters;

	@Builder
	private static class ProjectBatchProcessingParameters {
		private int currentPageNumber;
		private int currentIndex;
		private boolean repositoryHasMoreData;
		private boolean shouldStartANewBatchProcess;
		private List<ProjectInputDTO> currentProjectBatch;
	}

	/** Returns the next project or {@code null} once every project has been read. */
	public ProjectInputDTO getNextProjectInputData() {
		if (processingParameters.shouldStartANewBatchProcess) {
			initializeANewBatchProcess();

			if (batchContainsNoItems()) {
				log.info(
						"{} No eligible project found for epic hygiene pre-compute",
						JobConstants.LOG_PREFIX_EPIC_HYGIENE);
				return null;
			}
		}

		if (currentProjectBatchIsProcessed()) {
			setNextProjectInputBatchData();

			if (batchContainsNoItems()) {
				log.info("{} Finished reading all project items", JobConstants.LOG_PREFIX_EPIC_HYGIENE);
				return null;
			}
		}

		ProjectInputDTO nextProjectInputDTO =
				processingParameters.currentProjectBatch.get(processingParameters.currentIndex);
		processingParameters.currentIndex++;
		return nextProjectInputDTO;
	}

	/** Resets batch state — called after the job completes so the next run starts fresh. */
	public void initializeBatchProcessingParametersForTheNextProcess() {
		processingParameters =
				ProjectBatchProcessingParameters.builder()
						.currentPageNumber(0)
						.currentIndex(0)
						.repositoryHasMoreData(false)
						.shouldStartANewBatchProcess(true)
						.currentProjectBatch(Collections.emptyList())
						.build();
	}

	@PostConstruct
	private void initializeBatchProcessingParameters() {
		initializeBatchProcessingParametersForTheNextProcess();
	}

	private boolean batchContainsNoItems() {
		return CollectionUtils.isEmpty(processingParameters.currentProjectBatch);
	}

	private boolean currentProjectBatchIsProcessed() {
		return processingParameters.currentIndex >= processingParameters.currentProjectBatch.size();
	}

	private void initializeANewBatchProcess() {
		Page<ProjectBasicConfig> projectPage = getNextProjectPage();
		HierarchyLevel projectHierarchyLevel = hierarchyLevelService.getProjectHierarchyLevel();

		processingParameters =
				ProjectBatchProcessingParameters.builder()
						.currentPageNumber(0)
						.currentIndex(0)
						.repositoryHasMoreData(projectPage.hasNext())
						.shouldStartANewBatchProcess(false)
						.currentProjectBatch(constructProjectInputDTOList(projectPage, projectHierarchyLevel))
						.build();

		log.info(
				"{} Loaded {} project(s) in the first page for epic hygiene pre-compute",
				JobConstants.LOG_PREFIX_EPIC_HYGIENE,
				processingParameters.currentProjectBatch.size());
	}

	private void setNextProjectInputBatchData() {
		if (processingParameters.repositoryHasMoreData) {
			processingParameters.currentPageNumber++;

			Page<ProjectBasicConfig> projectPage = getNextProjectPage();
			HierarchyLevel projectHierarchyLevel = hierarchyLevelService.getProjectHierarchyLevel();

			processingParameters.currentProjectBatch =
					constructProjectInputDTOList(projectPage, projectHierarchyLevel);
			processingParameters.repositoryHasMoreData = projectPage.hasNext();
			processingParameters.currentIndex = 0;
		} else {
			// Reset the cursor as well, otherwise a further read would index past the empty batch.
			processingParameters.currentProjectBatch = Collections.emptyList();
			processingParameters.currentIndex = 0;
		}
	}

	private Page<ProjectBasicConfig> getNextProjectPage() {
		return projectBasicConfigRepository.findByKanbanAndProjectOnHold(
				false,
				false,
				PageRequest.of(
						processingParameters.currentPageNumber, jobConfig.getBatching().getChunkSize()));
	}

	private List<ProjectInputDTO> constructProjectInputDTOList(
			Page<ProjectBasicConfig> projectPage, HierarchyLevel projectHierarchyLevel) {
		return projectPage.stream()
				.filter(project -> project.getId() != null && project.getProjectNodeId() != null)
				.map(
						project ->
								ProjectInputDTO.builder()
										.name(project.getProjectDisplayName())
										.nodeId(project.getProjectNodeId())
										.basicProjectConfigId(String.valueOf(project.getId()))
										.hierarchyLevel(projectHierarchyLevel.getLevel())
										.hierarchyLevelId(projectHierarchyLevel.getHierarchyLevelId())
										.sprints(Collections.emptyList())
										.build())
				.toList();
	}
}
