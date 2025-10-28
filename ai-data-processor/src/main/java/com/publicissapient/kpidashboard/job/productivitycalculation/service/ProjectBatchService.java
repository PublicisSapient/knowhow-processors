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

package com.publicissapient.kpidashboard.job.productivitycalculation.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.generic.BasicModel;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepositoryCustomImpl;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.ProductivityCalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.productivitycalculation.dto.SprintInputDTO;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Service
@RequiredArgsConstructor
public class ProjectBatchService {

	private final ProductivityCalculationConfig productivityCalculationJobConfig;

	private final SprintRepositoryCustomImpl sprintRepositoryCustomImpl;
	private final ProjectBasicConfigRepository projectBasicConfigRepository;

	private ProjectBatchProcessingParameters projectBatchProcessingParameters;

	@Data
	@Builder
	private static class ProjectBatchProcessingParameters {
		private int currentPageNumber;
		private int currentIndex;
		private int numberOfPages;

		private boolean repositoryHasMoreData;
		private boolean shouldStartANewBatchProcess;

		private List<ProjectInputDTO> currentProjectBatch;
	}

	@PostConstruct
	private void initializeBatchProcessingParameters() {
		initializeBatchProcessingParametersForTheNextProcess();
	}

	public ProjectInputDTO getNextProjectInputData() {

		if (this.projectBatchProcessingParameters.shouldStartANewBatchProcess) {
			initializeANewBatchProcess();

			if (batchContainsNoItems()) {
				log.info("No elements could be found for processing after initializing a new batch process");
				//Returning null means there are no more elements to be processed
				return null;
			}
		}

		if (currentProjectBatchIsProcessed()) {
			setNextProjectInputBatchData();

			if (batchContainsNoItems()) {
				log.info("Finished reading all project items");
				//Returning null means there are no more elements to be processed
				return null;
			}
		}

		ProjectInputDTO nextProjectInputDTO = this.projectBatchProcessingParameters.currentProjectBatch
				.get(this.projectBatchProcessingParameters.currentIndex);

		this.projectBatchProcessingParameters.currentIndex++;

		return nextProjectInputDTO;
	}

	private boolean batchContainsNoItems() {
		return CollectionUtils.isEmpty(this.projectBatchProcessingParameters.currentProjectBatch);
	}

	private boolean currentProjectBatchIsProcessed() {
		return this.projectBatchProcessingParameters.currentIndex == this.projectBatchProcessingParameters.currentProjectBatch
				.size();
	}

	private void initializeANewBatchProcess() {
		Page<ProjectBasicConfig> projectBasicConfigPage = projectBasicConfigRepository
				.findAll(PageRequest.of(this.projectBatchProcessingParameters.currentPageNumber,
						productivityCalculationJobConfig.getBatching().getChunkSize()));

		List<SprintDetails> lastCompletedSprints = sprintRepositoryCustomImpl
				.findByBasicProjectConfigIdInOrderByCompletedDateDesc(
						projectBasicConfigPage.stream().map(BasicModel::getId).toList(),
						productivityCalculationJobConfig.getCalculationConfig().getDataPoints().getCount());

		setProjectBatchProcessingParameters(ProjectBatchProcessingParameters.builder().currentPageNumber(0)
				.currentIndex(0).numberOfPages(projectBasicConfigPage.getTotalPages())
				.repositoryHasMoreData(projectBasicConfigPage.hasNext()).shouldStartANewBatchProcess(false)
				.currentProjectBatch(constructProjectInputDTOList(projectBasicConfigPage, lastCompletedSprints))
				.build());
	}

	private void setNextProjectInputBatchData() {
		if (this.projectBatchProcessingParameters.repositoryHasMoreData) {
			this.projectBatchProcessingParameters.currentPageNumber++;

			Page<ProjectBasicConfig> projectBasicConfigPage = projectBasicConfigRepository
					.findAll(PageRequest.of(this.projectBatchProcessingParameters.currentPageNumber,
							productivityCalculationJobConfig.getBatching().getChunkSize()));

			List<SprintDetails> lastCompletedSprints = sprintRepositoryCustomImpl
					.findByBasicProjectConfigIdInOrderByCompletedDateDesc(
							projectBasicConfigPage.stream().map(BasicModel::getId).toList(),
							productivityCalculationJobConfig.getCalculationConfig().getDataPoints().getCount());

			this.projectBatchProcessingParameters.currentProjectBatch = constructProjectInputDTOList(
					projectBasicConfigPage, lastCompletedSprints);
			this.projectBatchProcessingParameters.repositoryHasMoreData = projectBasicConfigPage.hasNext();
			this.projectBatchProcessingParameters.currentIndex = 0;
		} else {
			this.projectBatchProcessingParameters.currentProjectBatch = Collections.emptyList();
		}
	}

	public void initializeBatchProcessingParametersForTheNextProcess() {
		setProjectBatchProcessingParameters(
				ProjectBatchProcessingParameters.builder().currentPageNumber(0).currentIndex(0).numberOfPages(0)
						.repositoryHasMoreData(false).shouldStartANewBatchProcess(true).build());
	}

	private static List<ProjectInputDTO> constructProjectInputDTOList(Page<ProjectBasicConfig> projectBasicConfigPage,
			List<SprintDetails> projectSprintsDetails) {
		Map<ObjectId, List<SprintDetails>> projectObjectIdSprintsMap = projectSprintsDetails.stream()
				.collect(Collectors.groupingBy(SprintDetails::getBasicProjectConfigId));
		return projectBasicConfigPage.stream()
				.filter(projectBasicConfig -> Objects.nonNull(projectBasicConfig.getId())
						&& projectObjectIdSprintsMap.containsKey(projectBasicConfig.getId()))
				.map(projectBasicConfig -> ProjectInputDTO.builder().name(projectBasicConfig.getProjectName())
						.nodeId(projectBasicConfig.getProjectNodeId())
						.hierarchyLabel(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT).hierarchyLevel(5)
						.sprints(projectObjectIdSprintsMap.get(projectBasicConfig.getId()).stream()
								.map(sprintDetails -> SprintInputDTO.builder().hierarchyLevel(6)
										.hierarchyLabel(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT)
										.name(sprintDetails.getSprintName()).nodeId(sprintDetails.getSprintID())
										.build())
								.toList())
						.build())
				.toList();
	}
}
