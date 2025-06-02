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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.Iteration;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.service.RallyCommonService;
import org.apache.commons.lang3.tuple.Pair;
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

	@Override
	public Set<SprintDetails> processSprintData(HierarchicalRequirement hierarchicalRequirement,
			ProjectConfFieldMapping projectConfig, String boardId, ObjectId processorId) throws IOException {
		log.info("creating sprint report for the project : {}", projectConfig.getProjectName());
		Iteration iteration = hierarchicalRequirement.getIteration();
		Set<SprintDetails> sprintDetailsSet = new HashSet<>();
		if (iteration != null) {
			sprintDetailsSet = createSprintDetails(iteration, projectConfig, processorId);
		}
		return sprintDetailsSet;
	}

	@Override
	public void processSprintReportData(List<SprintDetails> sprintDetails,
			List<JiraIssueCustomHistory> jiraIssueCustomHistoryList, List<JiraIssue> jiraIssueList) {
		log.info("Processing sprint report data for {} sprints", sprintDetails.size());
		initializeSprintDetails(jiraIssueList, jiraIssueCustomHistoryList, sprintDetails);
	}

	private Set<SprintDetails> createSprintDetails(Iteration iteration, ProjectConfFieldMapping projectConfig, ObjectId processorId) {
		Set<SprintDetails> sprintDetailsSet = new HashSet<>();
		SprintDetails sprintDetails = new SprintDetails();
		// Check if sprintDetails with the same sprintID already exists
		sprintDetails.setOriginalSprintId(iteration.getObjectID());
		String sprintId = sprintDetails.getOriginalSprintId() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR
				+ projectConfig.getProjectBasicConfig().getProjectNodeId();
		SprintDetails existingSprintDetails = sprintRepository.findBySprintID(sprintId);
		if (existingSprintDetails != null) {
			setBasicSprintDetails(iteration, projectConfig, processorId, existingSprintDetails);
			sprintDetailsSet.add(existingSprintDetails);
		} else {
			// Insert new sprintDetails
			sprintDetails.setOriginalSprintId(iteration.getObjectID());
			sprintDetails.setSprintID(sprintId);
			setBasicSprintDetails(iteration, projectConfig, processorId, sprintDetails);
			sprintDetailsSet.add(sprintDetails);
		}

		return sprintDetailsSet;
	}

	private static void initializeSprintDetails(List<JiraIssue> jiraIssueList,
			List<JiraIssueCustomHistory> jiraIssueCustomHistoryList, List<SprintDetails> sprintDetailsList) {

		Map<String, JiraIssue> jiraIssueMap = jiraIssueList.stream()
				.collect(Collectors.toMap(JiraIssue::getNumber, issue -> issue));

		Map<String, Set<JiraIssue>> issuesBySprintName = jiraIssueList.stream()
				.filter(issue -> issue.getSprintName() != null)
				.collect(Collectors.groupingBy(JiraIssue::getSprintName, Collectors.toSet()));

		for (SprintDetails sprintDetails : sprintDetailsList) {
			String sprintName = sprintDetails.getSprintName();

			Set<SprintIssue> totalIssues = convertToSprintIssues(
					issuesBySprintName.getOrDefault(sprintName, new HashSet<>()));

			Pair<Set<SprintIssue>, Set<SprintIssue>> addedAndRemovedIssues = processSprintHistory(
					jiraIssueCustomHistoryList, jiraIssueMap, sprintName);

			Set<SprintIssue> addedIssues = addedAndRemovedIssues.getLeft();
			Set<SprintIssue> removedIssues = addedAndRemovedIssues.getRight();

			setTotalIssues(sprintDetails, totalIssues);

			setAddedAndRemovedIssues(sprintDetails,
					addedIssues.stream().map(SprintIssue::getNumber).collect(Collectors.toSet()), removedIssues);

			addedIssues.removeAll(removedIssues);
			totalIssues.addAll(addedIssues);
			setTotalIssues(sprintDetails, totalIssues);

			separateAndSetIssuesByCompletionStatus(sprintDetails, totalIssues);
		}
	}

	private static void setBasicSprintDetails(Iteration iteration, ProjectConfFieldMapping projectConfig,
			ObjectId processorId, SprintDetails sprintDetails) {
		sprintDetails.setSprintName(iteration.getName());
		sprintDetails.setStartDate(iteration.getStartDate());
		sprintDetails.setEndDate(iteration.getEndDate());
		sprintDetails.setCompleteDate(iteration.getEndDate());
		sprintDetails.setBasicProjectConfigId(projectConfig.getBasicProjectConfigId());
		sprintDetails.setProcessorId(processorId);
		String sprintState = iteration.getState();
		if(sprintState.equalsIgnoreCase("Accepted"))
			sprintDetails.setState(SprintDetails.SPRINT_STATE_CLOSED);
		else if (iteration.getState().equalsIgnoreCase("Committed"))
			sprintDetails.setState(SprintDetails.SPRINT_STATE_ACTIVE);
		else
			sprintDetails.setState(SprintDetails.SPRINT_STATE_FUTURE);
	}

	private static Set<SprintIssue> convertToSprintIssues(Set<JiraIssue> jiraIssues) {
		return jiraIssues.stream().map(jiraIssue -> {
			SprintIssue sprintIssue = new SprintIssue();
			sprintIssue.setNumber(jiraIssue.getNumber());
			sprintIssue.setStatus(jiraIssue.getStatus());
			sprintIssue.setTypeName(jiraIssue.getTypeName());
			sprintIssue.setStoryPoints(jiraIssue.getStoryPoints());
			return sprintIssue;
		}).collect(Collectors.toSet());
	}

	private static Pair<Set<SprintIssue>, Set<SprintIssue>> processSprintHistory(
			List<JiraIssueCustomHistory> historyList, Map<String, JiraIssue> jiraIssueMap, String sprintName) {

		Set<SprintIssue> addedIssues = new HashSet<>();
		Set<SprintIssue> removedIssues = new HashSet<>();

		historyList.stream().filter(history -> history.getSprintUpdationLog() != null)
				.forEach(history -> history.getSprintUpdationLog().forEach(log -> {
					JiraIssue jiraIssue = jiraIssueMap.get(history.getStoryID());
					if (jiraIssue == null)
						return;

					if (log.getChangedTo().equalsIgnoreCase(sprintName)) {
						addedIssues.add(createSprintIssueFromJira(jiraIssue));
					} else if (log.getChangedFrom().equalsIgnoreCase(sprintName)) {
						removedIssues.add(createSprintIssueFromJira(jiraIssue));
					}
				}));

		return Pair.of(addedIssues, removedIssues);
	}

	private static SprintIssue createSprintIssueFromJira(JiraIssue jiraIssue) {
		SprintIssue sprintIssue = new SprintIssue();
		sprintIssue.setNumber(jiraIssue.getNumber());
		sprintIssue.setStatus(jiraIssue.getStatus());
		sprintIssue.setTypeName(jiraIssue.getTypeName());
		sprintIssue.setStoryPoints(jiraIssue.getStoryPoints());
		return sprintIssue;
	}

	private static void setTotalIssues(SprintDetails sprintDetails, Set<SprintIssue> totalIssues) {
	    if (sprintDetails.getSprintID() != null && sprintDetails.getTotalIssues() != null) {
	        sprintDetails.getTotalIssues().addAll(totalIssues);
	    } else {
	        sprintDetails.setTotalIssues(totalIssues);
	    }
	}

	private static void setAddedAndRemovedIssues(SprintDetails sprintDetails, Set<String> addedIssues,
			Set<SprintIssue> removedIssues) {
		if (sprintDetails.getSprintID() != null && sprintDetails.getAddedIssues() != null) {
			sprintDetails.getAddedIssues().addAll(addedIssues);
		} else {
			sprintDetails.setAddedIssues(addedIssues);
		}
		if (sprintDetails.getSprintID() != null && sprintDetails.getPuntedIssues() != null) {
			sprintDetails.getPuntedIssues().addAll(removedIssues);
		} else {
			sprintDetails.setPuntedIssues(removedIssues);
		}
	}

	private static void separateAndSetIssuesByCompletionStatus(SprintDetails sprintDetails,
			Set<SprintIssue> totalIssues) {
		Set<SprintIssue> completedIssues = new HashSet<>();
		Set<SprintIssue> notCompletedIssues = new HashSet<>();
		for (SprintIssue issue : totalIssues) {
			if ("Accepted".equals(issue.getStatus()) || "Completed".equals(issue.getStatus())) {
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
