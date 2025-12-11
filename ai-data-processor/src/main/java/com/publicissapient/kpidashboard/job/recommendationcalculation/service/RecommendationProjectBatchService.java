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
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for batching projects during recommendation calculation.
 */
@Slf4j
@Component
@JobScope
@RequiredArgsConstructor
public class RecommendationProjectBatchService {

	private final RecommendationCalculationConfig recommendationCalculationConfig;
	private final ProjectBasicConfigRepository projectBasicConfigRepository;
	private final HierarchyLevelServiceImpl hierarchyLevelServiceImpl;

	private ProjectBatchProcessingParameters processingParameters;

	@Builder
	private static class ProjectBatchProcessingParameters {
		private int currentPageNumber;
		private int currentIndex;
		private int numberOfPages;
		private boolean repositoryHasMoreData;
		private boolean shouldStartANewBatchProcess;
		private List<ProjectInputDTO> currentProjectBatch;
	}

	/**
	 * Retrieves the next project input data for processing.
	 */
	public ProjectInputDTO getNextProjectInputData() {
		if (this.processingParameters.shouldStartANewBatchProcess) {
			initializeANewBatchProcess();

			if (batchContainsNoItems()) {
				log.info("{} No elements found after initializing new batch process",
						JobConstants.LOG_PREFIX_RECOMMENDATION);
				return null;
			}
		}

		if (currentProjectBatchIsProcessed()) {
			setNextProjectInputBatchData();

			if (batchContainsNoItems()) {
				log.info("{} Finished reading all project items", JobConstants.LOG_PREFIX_RECOMMENDATION);
				return null;
			}
		}

		ProjectInputDTO nextProjectInputDTO = this.processingParameters.currentProjectBatch
				.get(this.processingParameters.currentIndex);
		this.processingParameters.currentIndex++;
		return nextProjectInputDTO;
	}

	/**
	 * Resets batch processing parameters for the next job execution.
	 */
	public void initializeBatchProcessingParametersForTheNextProcess() {
		this.processingParameters = ProjectBatchProcessingParameters.builder().currentPageNumber(0).currentIndex(0)
				.numberOfPages(0).repositoryHasMoreData(false).shouldStartANewBatchProcess(true).build();
	}

	@PostConstruct
	private void initializeBatchProcessingParameters() {
		initializeBatchProcessingParametersForTheNextProcess();
	}

	private boolean batchContainsNoItems() {
		return CollectionUtils.isEmpty(this.processingParameters.currentProjectBatch);
	}

	private boolean currentProjectBatchIsProcessed() {
		return this.processingParameters.currentIndex == this.processingParameters.currentProjectBatch.size();
	}

	private void initializeANewBatchProcess() {
		Page<ProjectBasicConfig> projectPage = getNextProjectPage();
		HierarchyLevel projectHierarchyLevel = hierarchyLevelServiceImpl.getProjectHierarchyLevel();

		this.processingParameters = ProjectBatchProcessingParameters.builder().currentPageNumber(0).currentIndex(0)
				.numberOfPages(projectPage.getTotalPages()).repositoryHasMoreData(projectPage.hasNext())
				.shouldStartANewBatchProcess(false)
				.currentProjectBatch(constructProjectInputDTOList(projectPage, projectHierarchyLevel)).build();
	}

	private void setNextProjectInputBatchData() {
		if (this.processingParameters.repositoryHasMoreData) {
			this.processingParameters.currentPageNumber++;

			Page<ProjectBasicConfig> projectPage = getNextProjectPage();
			HierarchyLevel projectHierarchyLevel = hierarchyLevelServiceImpl.getProjectHierarchyLevel();

			this.processingParameters.currentProjectBatch = constructProjectInputDTOList(projectPage,
					projectHierarchyLevel);
			this.processingParameters.repositoryHasMoreData = projectPage.hasNext();
			this.processingParameters.currentIndex = 0;
		} else {
			this.processingParameters.currentProjectBatch = Collections.emptyList();
		}
	}

	private Page<ProjectBasicConfig> getNextProjectPage() {
		return projectBasicConfigRepository.findByKanbanAndProjectOnHold(false, false,
				PageRequest.of(this.processingParameters.currentPageNumber,
						recommendationCalculationConfig.getBatching().getChunkSize()));
	}

	private List<ProjectInputDTO> constructProjectInputDTOList(Page<ProjectBasicConfig> projectPage,
			HierarchyLevel projectHierarchyLevel) {
		return projectPage.stream().filter(project -> project.getId() != null)
				.map(project -> ProjectInputDTO.builder().name(project.getProjectDisplayName())
						.nodeId(String.valueOf(project.getId())).hierarchyLevel(projectHierarchyLevel.getLevel())
						.hierarchyLevelId(projectHierarchyLevel.getHierarchyLevelId()).sprints(Collections.emptyList())
						.build())
				.toList();
	}
}
