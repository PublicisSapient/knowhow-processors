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

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.util.RallyProcessorUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author girpatha
 */
@Slf4j
@Service
public class RallyIssueProcessorImpl implements RallyIssueProcessor {

	@Autowired
	private JiraIssueRepository jiraIssueRepository;


	private JiraIssue getJiraIssue(ProjectConfFieldMapping projectConfig, String issueId) {
		String basicProjectConfigId = projectConfig.getBasicProjectConfigId().toString();
		JiraIssue jiraIssue = jiraIssueRepository
				.findByIssueIdAndBasicProjectConfigId(StringEscapeUtils.escapeHtml4(issueId), basicProjectConfigId);

		return jiraIssue != null ? jiraIssue : new JiraIssue();
	}

	private void processJiraIssueData(JiraIssue jiraIssue, HierarchicalRequirement hierarchicalRequirement) {

		jiraIssue.setNumber(hierarchicalRequirement.getFormattedID());
		jiraIssue.setName(hierarchicalRequirement.getName());
		log.debug("Issue : {}", jiraIssue.getNumber());
		String status = hierarchicalRequirement.getScheduleState();
		jiraIssue.setStatus(status);
		jiraIssue.setState(status);
		jiraIssue.setEstimate(String.valueOf(hierarchicalRequirement.getPlanEstimate()));
		jiraIssue.setStoryPoints(hierarchicalRequirement.getPlanEstimate());
		jiraIssue.setChangeDate(RallyProcessorUtil.getFormattedDate(hierarchicalRequirement.getLastUpdateDate()));
		jiraIssue.setUpdateDate(RallyProcessorUtil.getFormattedDate(hierarchicalRequirement.getLastUpdateDate()));
		jiraIssue.setIsDeleted(RallyConstants.FALSE);

		jiraIssue.setOwnersState(Arrays.asList("Active"));

		jiraIssue.setOwnersChangeDate(Collections.<String>emptyList());

		jiraIssue.setOwnersIsDeleted(Collections.<String>emptyList());
		// Created Date
		jiraIssue.setCreatedDate(RallyProcessorUtil.getFormattedDate(hierarchicalRequirement.getCreationDate()));
	}

	private void setProjectSpecificDetails(ProjectConfFieldMapping projectConfig, JiraIssue jiraIssue) {
		String name = projectConfig.getProjectName();
		jiraIssue.setProjectName(name);
		jiraIssue.setProjectKey(projectConfig.getProjectToolConfig().getProjectKey());
		jiraIssue.setBasicProjectConfigId(projectConfig.getBasicProjectConfigId().toString());
		jiraIssue.setProjectBeginDate("");
		jiraIssue.setProjectEndDate("");
		jiraIssue.setProjectChangeDate("");
		jiraIssue.setProjectState("");
		jiraIssue.setProjectIsDeleted("False");
		jiraIssue.setProjectPath("");
	}

	private void setDefectIssueType(JiraIssue jiraIssue, HierarchicalRequirement hierarchicalRequirement, FieldMapping fieldMapping) {
		if (CollectionUtils.isNotEmpty(fieldMapping.getJiradefecttype())
				&& fieldMapping.getJiradefecttype().stream().anyMatch(hierarchicalRequirement.getType()::equalsIgnoreCase)) {
			jiraIssue.setTypeName(NormalizedJira.DEFECT_TYPE.getValue());
		}
	}

	@Override
	public JiraIssue convertToJiraIssue(HierarchicalRequirement hierarchicalRequirement,
			ProjectConfFieldMapping projectConfig, String boardId, ObjectId processorId) {
		JiraIssue jiraIssue = null;
		FieldMapping fieldMapping = projectConfig.getFieldMapping();
		 if (null == fieldMapping) {
		 return jiraIssue;
		 }
		String issueId = RallyProcessorUtil.deodeUTF8String(hierarchicalRequirement.getFormattedID());
		jiraIssue = getJiraIssue(projectConfig, issueId);
		jiraIssue.setProcessorId(processorId);
		jiraIssue.setJiraStatus(hierarchicalRequirement.getScheduleState());
		jiraIssue.setTypeId(hierarchicalRequirement.getObjectID());
		jiraIssue.setIssueId(hierarchicalRequirement.getFormattedID());
		if(hierarchicalRequirement.getType().equalsIgnoreCase("HierarchicalRequirement"))
			jiraIssue.setTypeName(NormalizedJira.USER_STORY_TYPE.getValue());
		else
			jiraIssue.setTypeName(hierarchicalRequirement.getType());
		jiraIssue.setOriginalType(hierarchicalRequirement.getType());
		processJiraIssueData(jiraIssue, hierarchicalRequirement);
		 setDefectIssueType(jiraIssue, hierarchicalRequirement, projectConfig.getFieldMapping());
		setProjectSpecificDetails(projectConfig, jiraIssue);
		if (hierarchicalRequirement.getIteration() != null) {
			jiraIssue.setSprintBeginDate(hierarchicalRequirement.getIteration().getStartDate());
			jiraIssue.setSprintEndDate(hierarchicalRequirement.getIteration().getEndDate());
			jiraIssue.setSprintName(hierarchicalRequirement.getIteration().getName());
			jiraIssue.setSprintID(hierarchicalRequirement.getIteration().getObjectID() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR
					+ projectConfig.getProjectBasicConfig().getProjectNodeId());
			jiraIssue.setSprintAssetState(hierarchicalRequirement.getIteration().getState());
		}
		jiraIssue.setBoardId(boardId);
		return jiraIssue;
	}
}
