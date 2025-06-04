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

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.util.RallyProcessorUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author girpatha
 */
@Service
@Slf4j
public class RallyIssueHistoryProcessorImpl implements RallyIssueHistoryProcessor {

	private static final String ITERATION = "ITERATION";
	private static final String SCHEDULE_STATE = "SCHEDULE STATE";
	private static final String CHANGE_PATTERN = " changed from \\[|] to \\[|]";

	@Autowired
	private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	private JiraIssueCustomHistory getIssueCustomHistory(ProjectConfFieldMapping projectConfig, String issueId) {
		String basicProjectConfigId = projectConfig.getBasicProjectConfigId().toString();
		JiraIssueCustomHistory jiraIssueHistory = jiraIssueCustomHistoryRepository
				.findByStoryIDAndBasicProjectConfigId(issueId, basicProjectConfigId);

		return jiraIssueHistory != null ? jiraIssueHistory : new JiraIssueCustomHistory();
	}

	private void setJiraIssueHistory(JiraIssueCustomHistory jiraIssueHistory, JiraIssue jiraIssue,
			HierarchicalRequirement hierarchicalRequirement) {

		jiraIssueHistory.setProjectID(jiraIssue.getProjectName());
		jiraIssueHistory.setProjectKey(jiraIssue.getProjectKey());
		jiraIssueHistory.setStoryType(jiraIssue.getTypeName());
		jiraIssueHistory.setAdditionalFilters(jiraIssue.getAdditionalFilters());
		jiraIssueHistory.setUrl(jiraIssue.getUrl());
		jiraIssueHistory.setDescription(jiraIssue.getName());
		processJiraIssueHistory(jiraIssueHistory, jiraIssue, hierarchicalRequirement);

		jiraIssueHistory.setBasicProjectConfigId(jiraIssue.getBasicProjectConfigId());
	}

	private void processJiraIssueHistory(JiraIssueCustomHistory jiraIssueCustomHistory, JiraIssue jiraIssue,
			HierarchicalRequirement hierarchicalRequirement) {
		if (null != jiraIssue.getDevicePlatform()) {
			jiraIssueCustomHistory.setDevicePlatform(jiraIssue.getDevicePlatform());
		}
		if (null == jiraIssueCustomHistory.getStoryID()) {
			addStoryHistory(jiraIssueCustomHistory, jiraIssue, hierarchicalRequirement);
		} else {
			if (NormalizedJira.DEFECT_TYPE.getValue().equalsIgnoreCase(jiraIssue.getTypeName())) {
				jiraIssueCustomHistory.setDefectStoryID(jiraIssue.getDefectStoryID());
			}
		}
		setIssueCustomHistoryChangeLog(hierarchicalRequirement, jiraIssueCustomHistory, jiraIssue);
	}

	private void addStoryHistory(JiraIssueCustomHistory jiraIssueCustomHistory, JiraIssue jiraIssue,
			HierarchicalRequirement hierarchicalRequirement) {

		jiraIssueCustomHistory.setStoryID(jiraIssue.getNumber());
		jiraIssueCustomHistory.setCreatedDate(DateTime.parse(hierarchicalRequirement.getCreationDate()));

		// estimate
		jiraIssueCustomHistory.setEstimate(jiraIssue.getEstimate());
		jiraIssueCustomHistory.setBufferedEstimateTime(jiraIssue.getBufferedEstimateTime());
		if (NormalizedJira.DEFECT_TYPE.getValue().equalsIgnoreCase(jiraIssue.getTypeName())) {
			jiraIssueCustomHistory.setDefectStoryID(jiraIssue.getDefectStoryID());
		}

	}

	public void setIssueCustomHistoryChangeLog(HierarchicalRequirement hierarchicalRequirement,
			JiraIssueCustomHistory jiraIssueCustomHistory, JiraIssue jiraIssue) {
		List<Pair<String, String>> changeLog = (List<Pair<String, String>>) hierarchicalRequirement
				.getAdditionalProperties().get(RallyConstants.HIERARCHY_REVISION_HISTORY);

		List<JiraHistoryChangeLog> sprintUpdationLog = new ArrayList<>();
		List<JiraHistoryChangeLog> statusUpdationLog = new ArrayList<>();

		if (changeLog != null) {
			processChangeLogs(changeLog, sprintUpdationLog, statusUpdationLog);
		}

		createFirstEntryOfChangeLog(sprintUpdationLog, jiraIssue);
		createFirstEntryOfChangeLog(statusUpdationLog, jiraIssue);

		jiraIssueCustomHistory.setSprintUpdationLog(sprintUpdationLog);
		jiraIssueCustomHistory.setStatusUpdationLog(statusUpdationLog);
	}

	private void processChangeLogs(List<Pair<String, String>> changeLog, List<JiraHistoryChangeLog> sprintUpdationLog,
			List<JiraHistoryChangeLog> statusUpdationLog) {

		changeLog.forEach(pair -> {
			String[] changeDescription = pair.getRight().split(CHANGE_PATTERN);
			if (changeDescription.length > 2) {
				String fieldName = changeDescription[0].trim();
				String oldValue = changeDescription[1].trim();
				String newValue = changeDescription[2].trim();

				if (fieldName.equalsIgnoreCase(ITERATION)) {
					addHistoryChangeLog(pair.getLeft(), oldValue, newValue, sprintUpdationLog);
				} else if (fieldName.equalsIgnoreCase(SCHEDULE_STATE)) {
					addHistoryChangeLog(pair.getLeft(), oldValue, newValue, statusUpdationLog);
				}
			}
		});
	}

	private void addHistoryChangeLog(String dateTimeString, String oldValue, String newValue,
			List<JiraHistoryChangeLog> changeLogList) {
		LocalDateTime changedDateTime = DateUtil.convertingStringToLocalDateTime(dateTimeString,
				DateUtil.TIME_FORMAT_WITH_SEC_ZONE);

		JiraHistoryChangeLog historyChangeLog = new JiraHistoryChangeLog();
		historyChangeLog.setUpdatedOn(changedDateTime);
		historyChangeLog.setChangedFrom(oldValue);
		historyChangeLog.setChangedTo(newValue);

		changeLogList.add(historyChangeLog);
	}

	private void createFirstEntryOfChangeLog(List<JiraHistoryChangeLog> fieldChangeLog, JiraIssue issue) {
		if (null != issue.getCreatedDate()
				&& (fieldChangeLog.isEmpty() || !fieldChangeLog.get(0).getChangedFrom().isEmpty())) {
			JiraHistoryChangeLog firstChangeLog = fieldChangeLog.stream()
					.min(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn)).orElse(null);
			if (firstChangeLog != null && firstChangeLog.getChangedFrom() != null) {
				JiraHistoryChangeLog firstEntry = new JiraHistoryChangeLog();
				firstEntry.setChangedFrom("");
                firstEntry.setChangedTo(firstChangeLog.getChangedFrom());
				firstEntry.setUpdatedOn(LocalDateTime.parse(RallyProcessorUtil
						.getFormattedDate(RallyProcessorUtil.deodeUTF8String(issue.getCreatedDate()))));

				fieldChangeLog.add(0, firstEntry);
			}
		}
	}

	@Override
	public JiraIssueCustomHistory convertToJiraIssueHistory(HierarchicalRequirement hierarchicalRequirement,
			ProjectConfFieldMapping projectConfig, JiraIssue jiraIssue) {
		log.info("Converting issue to JiraIssueHistory for the project : {}", projectConfig.getProjectName());
		String issueNumber = hierarchicalRequirement.getFormattedID();
		JiraIssueCustomHistory jiraIssueHistory = getIssueCustomHistory(projectConfig, issueNumber);
		setJiraIssueHistory(jiraIssueHistory, jiraIssue, hierarchicalRequirement);
		return jiraIssueHistory;
	}
}
