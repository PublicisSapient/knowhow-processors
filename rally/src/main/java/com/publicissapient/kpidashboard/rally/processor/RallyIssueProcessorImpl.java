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
import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author girpatha
 */
@Slf4j
@Service
public class RallyIssueProcessorImpl implements RallyIssueProcessor {

    public static final String FORMATTED_ID = "FormattedID";
    @Autowired
    private JiraIssueRepository jiraIssueRepository;

    @Autowired
    private RallyProcessorConfig rallyProcessorConfig;

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
        jiraIssue.setIssueId(hierarchicalRequirement.getObjectID());
        if (hierarchicalRequirement.getType().equalsIgnoreCase("HierarchicalRequirement"))
            jiraIssue.setTypeName(NormalizedJira.USER_STORY_TYPE.getValue());
        else
            jiraIssue.setTypeName(hierarchicalRequirement.getType());
        jiraIssue.setOriginalType(hierarchicalRequirement.getType());
        processJiraIssueData(jiraIssue, hierarchicalRequirement);
        setDefectIssueType(jiraIssue, hierarchicalRequirement, projectConfig.getFieldMapping());
        setProjectSpecificDetails(projectConfig, jiraIssue);
        setStoryLinkWithDefect(hierarchicalRequirement, jiraIssue);
        
        if (hierarchicalRequirement.getIteration() != null) {
            jiraIssue.setSprintBeginDate(hierarchicalRequirement.getIteration().getStartDate());
            jiraIssue.setSprintEndDate(hierarchicalRequirement.getIteration().getEndDate());
            jiraIssue.setSprintName(hierarchicalRequirement.getIteration().getName());
            jiraIssue.setSprintID(hierarchicalRequirement.getIteration().getObjectID() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR
                    + projectConfig.getProjectBasicConfig().getProjectNodeId());
            jiraIssue.setSprintAssetState(hierarchicalRequirement.getIteration().getState());
        }
        setURL(hierarchicalRequirement.getObjectID(), jiraIssue);
        jiraIssue.setBoardId(boardId);
        return jiraIssue;
    }

    private void setURL(String ticketNumber, JiraIssue jiraIssue) {
        String baseUrl = rallyProcessorConfig.getRallyUserStoryBaseUrl();
        if (baseUrl != null) {
            jiraIssue.setUrl(baseUrl + ticketNumber);
        } else {
            // Set a default URL or just the ticket number if base URL is not available
            jiraIssue.setUrl("https://rally.example.com/" + ticketNumber);
        }
    }


    /**
     * Sets the story links for defects in the JiraIssue
     *
     * @param hierarchicalRequirement The Rally hierarchical requirement
     * @param jiraIssue               The JiraIssue to update
     */
    private void setStoryLinkWithDefect(HierarchicalRequirement hierarchicalRequirement, JiraIssue jiraIssue) {
        // For defect type issues, we need to find the stories they are linked to
        if (NormalizedJira.DEFECT_TYPE.getValue().equalsIgnoreCase(jiraIssue.getTypeName()) ||
                NormalizedJira.TEST_TYPE.getValue().equalsIgnoreCase(jiraIssue.getTypeName())) {

            Set<String> defectStorySet = new HashSet<>();

            // First check if there's a Requirement field directly in the hierarchicalRequirement object
            // This would be populated if we fetched the defect with the Requirement field
            setDefectStory(hierarchicalRequirement, defectStorySet);

            // Also check if there's a reference to parent stories in the defect's additional properties
            if (hierarchicalRequirement.getAdditionalProperties() != null) {
                // Look for Requirement or Parent field that might contain story references
                for (String key : Arrays.asList("Requirement", "Parent", "WorkProduct", "Requirement.FormattedID")) {
                    if (hierarchicalRequirement.getAdditionalProperties().containsKey(key)) {
                        Object requirementObj = hierarchicalRequirement.getAdditionalProperties().get(key);

                        // Handle different formats of story references
                        if (requirementObj instanceof String) {
                            // Direct story ID
                            defectStorySet.add(requirementObj.toString());
                        } else if (requirementObj instanceof List<?>) {
                            // List of story references
                            List<?> requirements = (List<?>) requirementObj;
                            for (Object req : requirements) {
                                if (req instanceof Map) {
                                    Map<?, ?> reqMap = (Map<?, ?>) req;
                                    if (reqMap.containsKey(FORMATTED_ID)) {
                                        String storyId = reqMap.get(FORMATTED_ID).toString();
                                        defectStorySet.add(storyId);
                                    }
                                } else if (req instanceof String) {
                                    defectStorySet.add(req.toString());
                                }
                            }
                        } else if (requirementObj instanceof Map) {
                            // Map containing story reference
                            Map<?, ?> reqMap = (Map<?, ?>) requirementObj;
                            if (reqMap.containsKey(FORMATTED_ID)) {
                                String storyId = reqMap.get(FORMATTED_ID).toString();
                                defectStorySet.add(storyId);
                            }
                        }
                    }
                }
            }

            // If we found any linked stories, set them in the JiraIssue
            if (!defectStorySet.isEmpty()) {
                log.debug("Found {} linked stories for defect {}: {}", defectStorySet.size(),
                        hierarchicalRequirement.getFormattedID(), defectStorySet);
                jiraIssue.setDefectStoryID(defectStorySet);
            }
        }
    }

    private static void setDefectStory(HierarchicalRequirement hierarchicalRequirement, Set<String> defectStorySet) {
        if (hierarchicalRequirement.getRequirement() != null) {
            HierarchicalRequirement requirement = hierarchicalRequirement.getRequirement();
            if (requirement.getFormattedID() != null) {
                defectStorySet.add(requirement.getFormattedID());
                log.debug("Found direct Requirement link for defect {}: {}",
                        hierarchicalRequirement.getFormattedID(), requirement.getFormattedID());
            }
        }
    }
}
