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

import java.util.ArrayList;
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

import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.generic.BasicModel;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepositoryCustomImpl;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelServiceImpl;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.ProductivityCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.shared.dto.SprintInputDTO;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectBatchService {

	private final ProductivityCalculationConfig productivityCalculationJobConfig;

	private final SprintRepositoryCustomImpl sprintRepositoryCustomImpl;
	private final ProjectBasicConfigRepository projectBasicConfigRepository;

	private final HierarchyLevelServiceImpl hierarchyLevelServiceImpl;

	private ProjectBatchProcessingParameters projectBatchProcessingParameters;

	@Builder
	private static class ProjectBatchProcessingParameters {
		private int currentPageNumber;
		private int currentIndex;
		private int numberOfPages;

		private boolean repositoryHasMoreData;
		private boolean shouldStartANewBatchProcess;

		private List<ProjectInputDTO> currentProjectBatch;
	}

	@Builder
	private record ProjectBatchInputParameters(Page<ProjectBasicConfig> projectBasicConfigPage,
			List<SprintDetails> lastCompletedSprints, HierarchyLevel projectHierarchyLevel,
			HierarchyLevel sprintHierarchyLevel) {
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
				// Returning null means there are no more elements to be processed
				return null;
			}
		}

		if (currentProjectBatchIsProcessed()) {
			setNextProjectInputBatchData();

			if (batchContainsNoItems()) {
				log.info("Finished reading all project items");
				// Returning null means there are no more elements to be processed
				return null;
			}
		}

		ProjectInputDTO nextProjectInputDTO = this.projectBatchProcessingParameters.currentProjectBatch
				.get(this.projectBatchProcessingParameters.currentIndex);
		this.projectBatchProcessingParameters.currentIndex++;
		return nextProjectInputDTO;
	}

	public void initializeBatchProcessingParametersForTheNextProcess() {
		this.projectBatchProcessingParameters = ProjectBatchProcessingParameters.builder().currentPageNumber(0)
				.currentIndex(0).numberOfPages(0).repositoryHasMoreData(false).shouldStartANewBatchProcess(true)
				.build();
	}

	private boolean batchContainsNoItems() {
		return CollectionUtils.isEmpty(this.projectBatchProcessingParameters.currentProjectBatch);
	}

	private boolean currentProjectBatchIsProcessed() {
		return this.projectBatchProcessingParameters.currentIndex == this.projectBatchProcessingParameters.currentProjectBatch
				.size();
	}

	private void initializeANewBatchProcess() {
		ProjectBatchInputParameters projectBatchInputParameters = getNextProjectBatchInputParameters();

		this.projectBatchProcessingParameters = ProjectBatchProcessingParameters.builder().currentPageNumber(0)
				.currentIndex(0).numberOfPages(projectBatchInputParameters.projectBasicConfigPage().getTotalPages())
				.repositoryHasMoreData(projectBatchInputParameters.projectBasicConfigPage().hasNext())
				.shouldStartANewBatchProcess(false)
				.currentProjectBatch(constructProjectInputDTOList(projectBatchInputParameters.projectBasicConfigPage(),
						projectBatchInputParameters.lastCompletedSprints(),
						projectBatchInputParameters.projectHierarchyLevel(),
						projectBatchInputParameters.sprintHierarchyLevel()))
				.build();
	}

	private void setNextProjectInputBatchData() {
		if (this.projectBatchProcessingParameters.repositoryHasMoreData) {
			this.projectBatchProcessingParameters.currentPageNumber++;

			ProjectBatchInputParameters projectBatchInputParameters = getNextProjectBatchInputParameters();

			this.projectBatchProcessingParameters.currentProjectBatch = constructProjectInputDTOList(
					projectBatchInputParameters.projectBasicConfigPage(),
					projectBatchInputParameters.lastCompletedSprints(),
					projectBatchInputParameters.projectHierarchyLevel(),
					projectBatchInputParameters.sprintHierarchyLevel());
			this.projectBatchProcessingParameters.repositoryHasMoreData = projectBatchInputParameters
					.projectBasicConfigPage().hasNext();
			this.projectBatchProcessingParameters.currentIndex = 0;
		} else {
			this.projectBatchProcessingParameters.currentProjectBatch = Collections.emptyList();
		}
	}

	private ProjectBatchInputParameters getNextProjectBatchInputParameters() {
		Page<ProjectBasicConfig> projectBasicConfigPage = projectBasicConfigRepository
				.findAll(PageRequest.of(this.projectBatchProcessingParameters.currentPageNumber,
						productivityCalculationJobConfig.getBatching().getChunkSize()));

		List<SprintDetails> lastCompletedSprints = sprintRepositoryCustomImpl
				.findByBasicProjectConfigIdInOrderByCompletedDateDesc(
						projectBasicConfigPage.stream().map(BasicModel::getId).toList(),
						productivityCalculationJobConfig.getCalculationConfig().getDataPoints().getCount());

		List<SprintDetails> sprintDetailsReversed = new ArrayList<>();

		for (int sprintIndex = lastCompletedSprints.size() - 1; sprintIndex > -1; sprintIndex--) {
			sprintDetailsReversed.add(lastCompletedSprints.get(sprintIndex));
		}

		HierarchyLevel projectHierarchyLevel = this.hierarchyLevelServiceImpl.getProjectHierarchyLevel();
		HierarchyLevel sprintHierarchyLevel = this.hierarchyLevelServiceImpl.getSprintHierarchyLevel();
		return ProjectBatchInputParameters.builder().projectBasicConfigPage(projectBasicConfigPage)
				.lastCompletedSprints(sprintDetailsReversed).projectHierarchyLevel(projectHierarchyLevel)
				.sprintHierarchyLevel(sprintHierarchyLevel).build();
	}

	private static List<ProjectInputDTO> constructProjectInputDTOList(Page<ProjectBasicConfig> projectBasicConfigPage,
			List<SprintDetails> projectSprintsDetails, HierarchyLevel projectHierarchyLevel,
			HierarchyLevel sprintHierarchyLevel) {
		Map<ObjectId, List<SprintDetails>> projectObjectIdSprintsMap = projectSprintsDetails.stream()
				.collect(Collectors.groupingBy(SprintDetails::getBasicProjectConfigId));
		return projectBasicConfigPage.stream().filter(projectBasicConfig -> Objects.nonNull(projectBasicConfig.getId()))
				.map(projectBasicConfig -> {
					ProjectInputDTO.ProjectInputDTOBuilder projectInputDTOBuilder = ProjectInputDTO.builder()
							.name(projectBasicConfig.getProjectName()).nodeId(projectBasicConfig.getProjectNodeId())
							.hierarchyLevelId(projectHierarchyLevel.getHierarchyLevelId())
							.hierarchyLevel(projectHierarchyLevel.getLevel());
					if (projectBasicConfig.isKanban()) {
						projectInputDTOBuilder.deliveryMethodology(ProjectDeliveryMethodology.KANBAN)
								.sprints(List.of());
					} else {
						projectInputDTOBuilder.deliveryMethodology(ProjectDeliveryMethodology.SCRUM)
								.sprints(projectObjectIdSprintsMap.get(projectBasicConfig.getId()).stream()
										.map(sprintDetails -> SprintInputDTO.builder()
												.hierarchyLevel(sprintHierarchyLevel.getLevel())
												.hierarchyLevelId(sprintHierarchyLevel.getHierarchyLevelId())
												.name(sprintDetails.getSprintName()).nodeId(sprintDetails.getSprintID())
												.build())
										.toList());
					}
					return projectInputDTOBuilder.build();
				}).toList();
	}
}
