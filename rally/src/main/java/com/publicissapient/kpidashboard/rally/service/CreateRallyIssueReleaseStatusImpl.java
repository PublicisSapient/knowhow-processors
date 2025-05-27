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
package com.publicissapient.kpidashboard.rally.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueReleaseStatus;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectToolConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueReleaseStatusRepository;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.RallyStateResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * @author girpatha
 */
@Slf4j
@Service
public class CreateRallyIssueReleaseStatusImpl implements CreateRallyIssueReleaseStatus {

    @Autowired
    private JiraIssueReleaseStatusRepository jiraIssueReleaseStatusRepository;
    
    @Autowired
    private ProjectBasicConfigRepository projectBasicConfigRepository;
    
    @Autowired
    private ProjectToolConfigRepository projectToolConfigRepository;

    @Override
   public void processAndSaveProjectStatusCategory(String basicProjectConfigId) {
    if (isProjectStatusAlreadySaved(basicProjectConfigId)) {
        log.info("Project status category is already in db for the project: {}", basicProjectConfigId);
        return;
    }

    List<RallyStateResponse.State> listOfProjectStatus = fetchRallyStates(basicProjectConfigId);
    if (CollectionUtils.isEmpty(listOfProjectStatus)) {
        return;
    }

    Map<Long, String> toDosList = new HashMap<>();
    Map<Long, String> inProgressList = new HashMap<>();
    Map<Long, String> closedList = new HashMap<>();

    categorizeProjectStatuses(listOfProjectStatus, toDosList, inProgressList, closedList);
    saveProjectStatusCategory(basicProjectConfigId, toDosList, inProgressList, closedList);
    log.info("Saved Rally project status category for the project: {}", basicProjectConfigId);
}

private boolean isProjectStatusAlreadySaved(String basicProjectConfigId) {
    return jiraIssueReleaseStatusRepository.findByBasicProjectConfigId(basicProjectConfigId) != null;
}

private void categorizeProjectStatuses(List<RallyStateResponse.State> listOfProjectStatus,
                                       Map<Long, String> toDosList,
                                       Map<Long, String> inProgressList,
                                       Map<Long, String> closedList) {
    listOfProjectStatus.forEach(status -> {
        String category = status.getStateCategory() != null ? status.getStateCategory().getName() : "";
        String name = status.getName();
        Long id = extractIdFromRef(status.getRef());

        if (id != null) {
            if (isToDoState(category, name)) {
                toDosList.put(id, name);
            } else if (isClosedState(category, name)) {
                closedList.put(id, name);
            } else {
                inProgressList.put(id, name);
            }
        }
    });
}
    private List<RallyStateResponse.State> fetchRallyStates(String basicProjectConfigId) {
        try {
            ProjectBasicConfig basicConfig = projectBasicConfigRepository.findById(new ObjectId(basicProjectConfigId)).orElse(null);
            if (basicConfig == null) {
                log.error("Project basic config not found for id: {}", basicProjectConfigId);
                return new ArrayList<>();
            }

            List<ProjectToolConfig> toolConfigs = projectToolConfigRepository.findByToolNameAndBasicProjectConfigId(
                RallyConstants.RALLY,
                new ObjectId(basicProjectConfigId)
            );

            if (CollectionUtils.isEmpty(toolConfigs)) {
                log.error("No Rally tool config found for project: {}", basicProjectConfigId);
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error fetching Rally states for project: " + basicProjectConfigId, e);
        }
        return new ArrayList<>();
    }

    private Long extractIdFromRef(String ref) {
        if (ref != null && ref.contains("/")) {
            String[] parts = ref.split("/");
            try {
                return Long.parseLong(parts[parts.length - 1]);
            } catch (NumberFormatException e) {
                log.error("Invalid ID format in ref URL: {}", ref);
            }
        }
        return null;
    }

    private boolean isToDoState(String category, String name) {
        return RallyConstants.TO_DO.equals(category) ||
               "Defined".equals(name) ||
               "Ready".equals(name);
    }

    private boolean isClosedState(String category, String name) {
        return RallyConstants.DONE.equals(category) ||
               "Completed".equals(name) ||
               "Accepted".equals(name);
    }

    private void saveProjectStatusCategory(String projectConfigId, Map<Long, String> toDosList,
            Map<Long, String> inProgressList, Map<Long, String> closedList) {
        JiraIssueReleaseStatus jiraIssueReleaseStatus = new JiraIssueReleaseStatus();
        jiraIssueReleaseStatus.setBasicProjectConfigId(projectConfigId);
        jiraIssueReleaseStatus.setToDoList(toDosList);
        jiraIssueReleaseStatus.setInProgressList(inProgressList);
        jiraIssueReleaseStatus.setClosedList(closedList);
        jiraIssueReleaseStatusRepository.save(jiraIssueReleaseStatus);
    }
}
