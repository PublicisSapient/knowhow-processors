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

import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * @author girpatha
 */
@Service
@Slf4j
public class RallyIssueHistoryProcessorImpl implements RallyIssueHistoryProcessor {

	@Autowired
	private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	private JiraIssueCustomHistory getIssueCustomHistory(ProjectConfFieldMapping projectConfig, String issueId) {
		String basicProjectConfigId = projectConfig.getBasicProjectConfigId().toString();
		JiraIssueCustomHistory jiraIssueHistory = jiraIssueCustomHistoryRepository
				.findByStoryIDAndBasicProjectConfigId(issueId, basicProjectConfigId);

		return jiraIssueHistory != null ? jiraIssueHistory : new JiraIssueCustomHistory();
	}

	private void setJiraIssueHistory(JiraIssueCustomHistory jiraIssueHistory, JiraIssue jiraIssue, HierarchicalRequirement hierarchicalRequirement) {

		jiraIssueHistory.setProjectID(jiraIssue.getProjectName());
		jiraIssueHistory.setProjectKey(jiraIssue.getProjectKey());
		jiraIssueHistory.setStoryType(jiraIssue.getTypeName());
		jiraIssueHistory.setAdditionalFilters(jiraIssue.getAdditionalFilters());
		jiraIssueHistory.setUrl(jiraIssue.getUrl());
		jiraIssueHistory.setDescription(jiraIssue.getName());
		processJiraIssueHistory(jiraIssueHistory, jiraIssue, hierarchicalRequirement);

		jiraIssueHistory.setBasicProjectConfigId(jiraIssue.getBasicProjectConfigId());
	}

	private void processJiraIssueHistory(JiraIssueCustomHistory jiraIssueCustomHistory, JiraIssue jiraIssue, HierarchicalRequirement hierarchicalRequirement) {
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
	}

	private void addStoryHistory(JiraIssueCustomHistory jiraIssueCustomHistory, JiraIssue jiraIssue, HierarchicalRequirement hierarchicalRequirement) {

		jiraIssueCustomHistory.setStoryID(jiraIssue.getNumber());
		jiraIssueCustomHistory.setCreatedDate(DateTime.parse(hierarchicalRequirement.getCreationDate()));

		// estimate
		jiraIssueCustomHistory.setEstimate(jiraIssue.getEstimate());
		jiraIssueCustomHistory.setBufferedEstimateTime(jiraIssue.getBufferedEstimateTime());
		if (NormalizedJira.DEFECT_TYPE.getValue().equalsIgnoreCase(jiraIssue.getTypeName())) {
			jiraIssueCustomHistory.setDefectStoryID(jiraIssue.getDefectStoryID());
		}
	}

	@Override
	public JiraIssueCustomHistory convertToJiraIssueHistory(HierarchicalRequirement hierarchicalRequirement, ProjectConfFieldMapping projectConfig, JiraIssue jiraIssue) {
		log.info("Converting issue to JiraIssueHistory for the project : {}", projectConfig.getProjectName());
		String issueNumber = hierarchicalRequirement.getFormattedID();
		JiraIssueCustomHistory jiraIssueHistory = getIssueCustomHistory(projectConfig, issueNumber);
		setJiraIssueHistory(jiraIssueHistory, jiraIssue, hierarchicalRequirement);
		return jiraIssueHistory;
	}
}
