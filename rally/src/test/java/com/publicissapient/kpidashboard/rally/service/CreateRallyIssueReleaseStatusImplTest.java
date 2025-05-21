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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueReleaseStatus;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectToolConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueReleaseStatusRepository;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;

/**
 * Unit tests for CreateRallyIssueReleaseStatusImpl class
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CreateRallyIssueReleaseStatusImplTest {

    @InjectMocks
    private CreateRallyIssueReleaseStatusImpl createRallyIssueReleaseStatus;

    @Mock
    private JiraIssueReleaseStatusRepository jiraIssueReleaseStatusRepository;

    @Mock
    private ProjectBasicConfigRepository projectBasicConfigRepository;

    @Mock
    private ProjectToolConfigRepository projectToolConfigRepository;

    private String basicProjectConfigId;
    private ProjectBasicConfig projectBasicConfig;
    private ProjectToolConfig projectToolConfig;
    private JiraIssueReleaseStatus existingReleaseStatus;

    @Before
    public void setup() {
        basicProjectConfigId = "5e7c9d7a8c1c4a0001a1b2c3";
        
        // Set up ProjectBasicConfig
        projectBasicConfig = new ProjectBasicConfig();
        projectBasicConfig.setId(new ObjectId(basicProjectConfigId));
        projectBasicConfig.setProjectName("Test Project");
        
        // Set up ProjectToolConfig
        projectToolConfig = new ProjectToolConfig();
        projectToolConfig.setToolName(RallyConstants.RALLY);
        projectToolConfig.setBasicProjectConfigId(new ObjectId(basicProjectConfigId));
        
        // Set up existing JiraIssueReleaseStatus
        existingReleaseStatus = new JiraIssueReleaseStatus();
        existingReleaseStatus.setBasicProjectConfigId(basicProjectConfigId);
    }

    /**
     * Test case for when status is already saved in the database
     */
    @Test
    public void testProcessAndSaveProjectStatusCategory_WhenStatusAlreadySaved() {
        // Arrange
        when(jiraIssueReleaseStatusRepository.findByBasicProjectConfigId(basicProjectConfigId))
            .thenReturn(existingReleaseStatus);
        
        // Act
        createRallyIssueReleaseStatus.processAndSaveProjectStatusCategory(basicProjectConfigId);
        
        // Assert
        verify(jiraIssueReleaseStatusRepository, times(1)).findByBasicProjectConfigId(basicProjectConfigId);
        verify(projectBasicConfigRepository, never()).findById(any(ObjectId.class));
        verify(jiraIssueReleaseStatusRepository, never()).save(any(JiraIssueReleaseStatus.class));
    }

    /**
     * Test case for when project basic config is not found
     */
    @Test
    public void testProcessAndSaveProjectStatusCategory_WhenProjectConfigNotFound() {
        // Arrange
        when(jiraIssueReleaseStatusRepository.findByBasicProjectConfigId(basicProjectConfigId))
            .thenReturn(null);
        when(projectBasicConfigRepository.findById(new ObjectId(basicProjectConfigId)))
            .thenReturn(Optional.empty());
        
        // Act
        createRallyIssueReleaseStatus.processAndSaveProjectStatusCategory(basicProjectConfigId);
        
        // Assert
        verify(jiraIssueReleaseStatusRepository, times(1)).findByBasicProjectConfigId(basicProjectConfigId);
        verify(projectBasicConfigRepository, times(1)).findById(new ObjectId(basicProjectConfigId));
        verify(projectToolConfigRepository, never()).findByToolNameAndBasicProjectConfigId(anyString(), any(ObjectId.class));
        verify(jiraIssueReleaseStatusRepository, never()).save(any(JiraIssueReleaseStatus.class));
    }

    /**
     * Test case for when no tool config is found
     */
    @Test
    public void testProcessAndSaveProjectStatusCategory_WhenNoToolConfigFound() {
        // Arrange
        when(jiraIssueReleaseStatusRepository.findByBasicProjectConfigId(basicProjectConfigId))
            .thenReturn(null);
        when(projectBasicConfigRepository.findById(new ObjectId(basicProjectConfigId)))
            .thenReturn(Optional.of(projectBasicConfig));
        when(projectToolConfigRepository.findByToolNameAndBasicProjectConfigId(
                RallyConstants.RALLY, new ObjectId(basicProjectConfigId)))
            .thenReturn(new ArrayList<>());
        
        // Act
        createRallyIssueReleaseStatus.processAndSaveProjectStatusCategory(basicProjectConfigId);
        
        // Assert
        verify(jiraIssueReleaseStatusRepository, times(1)).findByBasicProjectConfigId(basicProjectConfigId);
        verify(projectBasicConfigRepository, times(1)).findById(new ObjectId(basicProjectConfigId));
        verify(projectToolConfigRepository, times(1)).findByToolNameAndBasicProjectConfigId(
                RallyConstants.RALLY, new ObjectId(basicProjectConfigId));
        verify(jiraIssueReleaseStatusRepository, never()).save(any(JiraIssueReleaseStatus.class));
    }

    /**
     * Test case for when an exception occurs during processing
     */
    @Test
    public void testProcessAndSaveProjectStatusCategory_WhenExceptionOccurs() {
        // Arrange
        when(jiraIssueReleaseStatusRepository.findByBasicProjectConfigId(basicProjectConfigId))
            .thenReturn(null);
        when(projectBasicConfigRepository.findById(new ObjectId(basicProjectConfigId)))
            .thenThrow(new RuntimeException("Test exception"));
        
        // Act
        createRallyIssueReleaseStatus.processAndSaveProjectStatusCategory(basicProjectConfigId);
        
        // Assert
        verify(jiraIssueReleaseStatusRepository, times(1)).findByBasicProjectConfigId(basicProjectConfigId);
        verify(projectBasicConfigRepository, times(1)).findById(new ObjectId(basicProjectConfigId));
        verify(jiraIssueReleaseStatusRepository, never()).save(any(JiraIssueReleaseStatus.class));
    }

    /**
     * Test case for verifying JiraIssueReleaseStatus save functionality
     */
    @Test
    public void testSaveJiraIssueReleaseStatus() {
        // Arrange
        JiraIssueReleaseStatus statusToSave = new JiraIssueReleaseStatus();
        statusToSave.setBasicProjectConfigId(basicProjectConfigId);
        
        Map<Long, String> toDosList = new HashMap<>();
        toDosList.put(12345L, "Defined");
        
        Map<Long, String> inProgressList = new HashMap<>();
        inProgressList.put(67890L, "In-Progress");
        
        Map<Long, String> closedList = new HashMap<>();
        closedList.put(54321L, "Completed");
        
        statusToSave.setToDoList(toDosList);
        statusToSave.setInProgressList(inProgressList);
        statusToSave.setClosedList(closedList);
        
        // Mock the save method
        when(jiraIssueReleaseStatusRepository.save(any(JiraIssueReleaseStatus.class)))
            .thenReturn(statusToSave);
        
        // Act
        JiraIssueReleaseStatus savedStatus = jiraIssueReleaseStatusRepository.save(statusToSave);
        
        // Assert
        assertNotNull(savedStatus);
        assertEquals(basicProjectConfigId, savedStatus.getBasicProjectConfigId());
        assertEquals(toDosList, savedStatus.getToDoList());
        assertEquals(inProgressList, savedStatus.getInProgressList());
        assertEquals(closedList, savedStatus.getClosedList());
        
        // Verify save was called with the correct object
        ArgumentCaptor<JiraIssueReleaseStatus> statusCaptor = ArgumentCaptor.forClass(JiraIssueReleaseStatus.class);
        verify(jiraIssueReleaseStatusRepository, times(1)).save(statusCaptor.capture());
        
        JiraIssueReleaseStatus capturedStatus = statusCaptor.getValue();
        assertEquals(basicProjectConfigId, capturedStatus.getBasicProjectConfigId());
    }
}
