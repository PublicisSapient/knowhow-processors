/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.rally.processor;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.Iteration;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.service.RallyCommonService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;

import lombok.extern.slf4j.Slf4j;

/**
 * @author girpatha
 */
@Slf4j
@Service
public class SprintDataProcessorImpl implements SprintDataProcessor {

	@Autowired
	private SprintRepository sprintRepository;

	@Autowired
	private RallyCommonService rallyCommonService;

	@Override
	public Set<SprintDetails> processSprintData(HierarchicalRequirement hierarchicalRequirement, ProjectConfFieldMapping projectConfig, String boardId,
												ObjectId processorId) throws IOException {
		log.info("creating sprint report for the project : {}", projectConfig.getProjectName());
		Iteration iteration = hierarchicalRequirement.getIteration();
		List<HierarchicalRequirement> hierarchicalRequirements = rallyCommonService.getHierarchicalRequirementsByIteration(iteration,hierarchicalRequirement);
		Set<SprintDetails> sprintDetailsSet = new HashSet<>();
		if(iteration!=null) {
			sprintDetailsSet = createSprintDetails(hierarchicalRequirements,iteration, projectConfig, processorId);
		}
		return sprintDetailsSet;
	}

	private Set<SprintDetails> createSprintDetails(List<HierarchicalRequirement> hierarchicalRequirements,Iteration iteration, ProjectConfFieldMapping projectConfig, ObjectId processorId) {
	    Set<SprintDetails> sprintDetailsSet = new HashSet<>();
	    SprintDetails sprintDetails = new SprintDetails();
		// Check if sprintDetails with the same sprintID already exists
		sprintDetails.setOriginalSprintId(iteration.getObjectID());
		String sprintId = sprintDetails.getOriginalSprintId() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR +
				projectConfig.getProjectBasicConfig().getProjectNodeId();
		SprintDetails existingSprintDetails = sprintRepository.findBySprintID(sprintId);
		if (existingSprintDetails != null) {
			// Update the existing sprintDetails
			initializeSprintDetails(hierarchicalRequirements,iteration, projectConfig, processorId, existingSprintDetails);
			sprintDetailsSet.add(existingSprintDetails);
		} else {
			// Insert new sprintDetails
			sprintDetails.setOriginalSprintId(iteration.getObjectID());
			sprintDetails.setSprintID(sprintId);
			initializeSprintDetails(hierarchicalRequirements,iteration, projectConfig, processorId, sprintDetails);
			sprintDetailsSet.add(sprintDetails);
	    }

	    return sprintDetailsSet;
	}

	private static void initializeSprintDetails(List<HierarchicalRequirement> hierarchicalRequirements, Iteration iteration,
	        ProjectConfFieldMapping projectConfig, ObjectId processorId, SprintDetails sprintDetails) {
	    setBasicSprintDetails(iteration, projectConfig, processorId, sprintDetails);
	    Set<SprintIssue> totalIssues = createSprintIssues(hierarchicalRequirements, iteration);
	    setTotalIssues(sprintDetails, totalIssues);
	    separateAndSetIssuesByCompletionStatus(sprintDetails, totalIssues);
	}

	private static void setBasicSprintDetails(Iteration iteration, ProjectConfFieldMapping projectConfig, ObjectId processorId,
	        SprintDetails sprintDetails) {
	    sprintDetails.setSprintName(iteration.getName());
	    sprintDetails.setStartDate(iteration.getStartDate());
	    sprintDetails.setEndDate(iteration.getEndDate());
	    sprintDetails.setCompleteDate(iteration.getEndDate());
	    sprintDetails.setBasicProjectConfigId(projectConfig.getBasicProjectConfigId());
	    sprintDetails.setProcessorId(processorId);
	    sprintDetails.setState("closed");
	}

	private static Set<SprintIssue> createSprintIssues(List<HierarchicalRequirement> hierarchicalRequirements, Iteration iteration) {
	    Set<SprintIssue> totalIssues = new HashSet<>();
	    for (HierarchicalRequirement requirement : hierarchicalRequirements) {
	        if (requirement.getIteration() != null && iteration.getName().equals(requirement.getIteration().getName())) {
	            SprintIssue sprintIssue = new SprintIssue();
	            sprintIssue.setNumber(requirement.getFormattedID());
	            sprintIssue.setStatus(requirement.getScheduleState());
	            sprintIssue.setTypeName(requirement.getType());
	            sprintIssue.setStoryPoints(requirement.getPlanEstimate());
	            totalIssues.add(sprintIssue);
	        }
	    }
	    return totalIssues;
	}

	private static void setTotalIssues(SprintDetails sprintDetails, Set<SprintIssue> totalIssues) {
	    if (sprintDetails.getSprintID() != null && sprintDetails.getTotalIssues() != null) {
	        sprintDetails.getTotalIssues().addAll(totalIssues);
	    } else {
	        sprintDetails.setTotalIssues(totalIssues);
	    }
	}

	private static void separateAndSetIssuesByCompletionStatus(SprintDetails sprintDetails, Set<SprintIssue> totalIssues) {
	    Set<SprintIssue> completedIssues = new HashSet<>();
	    Set<SprintIssue> notCompletedIssues = new HashSet<>();
	    for (SprintIssue issue : totalIssues) {
	        if ("Accepted".equals(issue.getStatus())) {
	            completedIssues.add(issue);
	        } else {
	            notCompletedIssues.add(issue);
	        }
	    }
	    if (sprintDetails.getSprintID() != null && sprintDetails.getCompletedIssues() != null) {
	        sprintDetails.getCompletedIssues().addAll(completedIssues);
	    } else {
	        sprintDetails.setCompletedIssues(completedIssues);
	    }
	    if (sprintDetails.getSprintID() != null && sprintDetails.getNotCompletedIssues() != null) {
	        sprintDetails.getNotCompletedIssues().addAll(notCompletedIssues);
	    } else {
	        sprintDetails.setNotCompletedIssues(notCompletedIssues);
	    }
	}
}
