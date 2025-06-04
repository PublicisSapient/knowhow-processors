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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectHierarchy;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.jira.AssigneeDetails;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.rally.model.CompositeResult;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.Iteration;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.ReadData;

@ExtendWith(MockitoExtension.class)
public class IssueScrumProcessorTest {

    @InjectMocks
    private IssueScrumProcessor issueScrumProcessor;

    @Mock
    private RallyIssueProcessor rallyIssueProcessor;

    @Mock
    private RallyIssueHistoryProcessor rallyIssueHistoryProcessor;

    @Mock
    private RallyIssueAccountHierarchyProcessor rallyIssueAccountHierarchyProcessor;

    @Mock
    private RallyIssueAssigneeProcessor rallyIssueAssigneeProcessor;

    @Mock
    private SprintDataProcessor sprintDataProcessor;

    private ReadData readData;
    private JiraIssue jiraIssue;
    private JiraIssueCustomHistory jiraIssueCustomHistory;
    private Set<SprintDetails> sprintDetails;
    private Set<ProjectHierarchy> projectHierarchies;
    private AssigneeDetails assigneeDetails;
    private ProjectConfFieldMapping projectConfFieldMapping;
    private HierarchicalRequirement hierarchicalRequirement;

    @BeforeEach
    public void setup() {
        // Initialize basic objects
        readData = new ReadData();
        projectConfFieldMapping = new ProjectConfFieldMapping();
        hierarchicalRequirement = new HierarchicalRequirement();
        jiraIssue = new JiraIssue();
        jiraIssueCustomHistory = new JiraIssueCustomHistory();
        sprintDetails = new HashSet<>();
        projectHierarchies = new HashSet<>();
        assigneeDetails = new AssigneeDetails();
        
        // Set up ObjectId for processor
        ObjectId processorId = new ObjectId();
        readData.setProcessorId(processorId);
        
        // Set up board ID
        String boardId = "RALLY-BOARD-123";
        
        // Set up ProjectConfFieldMapping
        projectConfFieldMapping.setProjectName("Test Rally Project");
        ObjectId basicProjectConfigId = new ObjectId();
        projectConfFieldMapping.setBasicProjectConfigId(basicProjectConfigId);
        
        // Set up ProjectToolConfig
        ProjectToolConfig projectToolConfig = new ProjectToolConfig();
        projectToolConfig.setProjectKey("RALLY");
        projectConfFieldMapping.setProjectToolConfig(projectToolConfig);
        
        // Set up ProjectBasicConfig
        ProjectBasicConfig projectBasicConfig = new ProjectBasicConfig();
        projectBasicConfig.setProjectNodeId("node123");
        projectConfFieldMapping.setProjectBasicConfig(projectBasicConfig);
        
        // Set up HierarchicalRequirement
        hierarchicalRequirement.setObjectID("12345");
        hierarchicalRequirement.setFormattedID("US1234");
        hierarchicalRequirement.setName("Test User Story");
        hierarchicalRequirement.setScheduleState("Defined");
        hierarchicalRequirement.setPlanEstimate(8.0);
        hierarchicalRequirement.setType("HierarchicalRequirement");
        hierarchicalRequirement.setCreationDate("2025-05-01T10:00:00Z");
        hierarchicalRequirement.setLastUpdateDate("2025-05-20T15:30:00Z");
        
        // Set up Iteration
        Iteration iteration = new Iteration();
        iteration.setName("Sprint 1");
        iteration.setStartDate("2025-05-01");
        iteration.setEndDate("2025-05-15");
        iteration.setObjectID("IT1234");
        iteration.setState("Planning");
        hierarchicalRequirement.setIteration(iteration);
        
        // Set up JiraIssue
        jiraIssue.setIssueId(hierarchicalRequirement.getFormattedID());
        jiraIssue.setNumber(hierarchicalRequirement.getFormattedID());
        jiraIssue.setName(hierarchicalRequirement.getName());
        jiraIssue.setTypeName(hierarchicalRequirement.getType());
        
        // Set up SprintDetails
        SprintDetails sprintDetail = new SprintDetails();
        sprintDetail.setSprintName("Sprint 1");
        sprintDetail.setSprintID("IT1234"); // Note: it's setSprintID not setSprintId
        sprintDetail.setStartDate("2025-05-01");
        sprintDetail.setEndDate("2025-05-15");
        sprintDetail.setState("Planning");
        sprintDetails.add(sprintDetail);
        
        // Set up ProjectHierarchy
        ProjectHierarchy projectHierarchy = new ProjectHierarchy();
        projectHierarchy.setNodeId("node123");
        projectHierarchy.setNodeName("Test Rally Project");
        projectHierarchies.add(projectHierarchy);
        
        // Set up AssigneeDetails
        assigneeDetails.setBasicProjectConfigId(projectConfFieldMapping.getBasicProjectConfigId().toString());
        Set<Assignee> assignees = new HashSet<>();
        Assignee assignee = new Assignee("john.doe", "John Doe");
        assignees.add(assignee);
        assigneeDetails.setAssignee(assignees);
        
        // Configure ReadData
        readData.setProjectConfFieldMapping(projectConfFieldMapping);
        readData.setHierarchicalRequirement(hierarchicalRequirement);
        readData.setSprintFetch(false);
        readData.setBoardId(boardId);
    }

    @Test
    public void testProcessWithValidData() throws Exception {
        // Mock all dependencies with specific arguments
        when(rallyIssueProcessor.convertToJiraIssue(
                eq(hierarchicalRequirement),
                eq(projectConfFieldMapping),
                eq(readData.getBoardId()),
                eq(readData.getProcessorId())
        )).thenReturn(jiraIssue);
        
        when(rallyIssueHistoryProcessor.convertToJiraIssueHistory(
                eq(hierarchicalRequirement),
                eq(projectConfFieldMapping),
                eq(jiraIssue)
        )).thenReturn(jiraIssueCustomHistory);
        
        when(sprintDataProcessor.processSprintData(
                eq(hierarchicalRequirement),
                eq(projectConfFieldMapping),
                eq(readData.getBoardId()),
                eq(readData.getProcessorId())
        )).thenReturn(sprintDetails);
        
        when(rallyIssueAccountHierarchyProcessor.createAccountHierarchy(
                eq(jiraIssue),
                eq(projectConfFieldMapping),
                eq(sprintDetails)
        )).thenReturn(projectHierarchies);
        
        when(rallyIssueAssigneeProcessor.createAssigneeDetails(
                eq(projectConfFieldMapping),
                eq(jiraIssue)
        )).thenReturn(assigneeDetails);

        // Execute the method
        CompositeResult result = issueScrumProcessor.process(readData);

        // Verify results
        assertNotNull(result, "Result should not be null");
        assertEquals(jiraIssue, result.getJiraIssue(), "JiraIssue should match");
        assertEquals(jiraIssueCustomHistory, result.getJiraIssueCustomHistory(), "JiraIssueCustomHistory should match");
        assertNull(result.getSprintDetailsSet(), "SprintDetailsSet should be null because boardId is set");
        assertEquals(projectHierarchies, result.getProjectHierarchies(), "ProjectHierarchies should match");
        assertEquals(assigneeDetails, result.getAssigneeDetails(), "AssigneeDetails should match");
        
        // Verify interactions
        verify(rallyIssueProcessor, times(1)).convertToJiraIssue(any(), any(), any(), any());
        verify(rallyIssueHistoryProcessor, times(1)).convertToJiraIssueHistory(any(), any(), any());
        verify(sprintDataProcessor, times(1)).processSprintData(any(), any(), any(), any());
        verify(rallyIssueAccountHierarchyProcessor, times(1)).createAccountHierarchy(any(), any(), any());
        verify(rallyIssueAssigneeProcessor, times(1)).createAssigneeDetails(any(), any());
    }

    @Test
    public void testProcessWithNullJiraIssue() throws Exception {
        // Mock rallyIssueProcessor to return null
        when(rallyIssueProcessor.convertToJiraIssue(any(), any(), any(), any())).thenReturn(null);

        // Execute the method
        CompositeResult result = issueScrumProcessor.process(readData);

        // Verify result is null
        assertNull(result, "Result should be null when JiraIssue is null");
        
        // Verify interactions
        verify(rallyIssueProcessor, times(1)).convertToJiraIssue(any(), any(), any(), any());
        verify(rallyIssueHistoryProcessor, never()).convertToJiraIssueHistory(any(), any(), any());
        verify(sprintDataProcessor, never()).processSprintData(any(), any(), any(), any());
        verify(rallyIssueAccountHierarchyProcessor, never()).createAccountHierarchy(any(), any(), any());
        verify(rallyIssueAssigneeProcessor, never()).createAssigneeDetails(any(), any());
    }

    @Test
    public void testProcessWithSprintFetch() throws Exception {
        // Set sprintFetch to true
        readData.setSprintFetch(true);
        
        // Mock dependencies
        when(rallyIssueProcessor.convertToJiraIssue(any(), any(), any(), any())).thenReturn(jiraIssue);
        when(rallyIssueHistoryProcessor.convertToJiraIssueHistory(any(), any(), any())).thenReturn(jiraIssueCustomHistory);

        // Execute the method
        CompositeResult result = issueScrumProcessor.process(readData);

        // Verify results
        assertNotNull(result, "Result should not be null");
        assertEquals(jiraIssue, result.getJiraIssue(), "JiraIssue should match");
        assertEquals(jiraIssueCustomHistory, result.getJiraIssueCustomHistory(), "JiraIssueCustomHistory should match");
        assertNull(result.getSprintDetailsSet(), "SprintDetailsSet should be null when sprintFetch is true");
        assertNull(result.getProjectHierarchies(), "ProjectHierarchies should be null when sprintFetch is true");
        assertNull(result.getAssigneeDetails(), "AssigneeDetails should be null when sprintFetch is true");
        
        // Verify interactions
        verify(rallyIssueProcessor, times(1)).convertToJiraIssue(any(), any(), any(), any());
        verify(rallyIssueHistoryProcessor, times(1)).convertToJiraIssueHistory(any(), any(), any());
        verify(sprintDataProcessor, never()).processSprintData(any(), any(), any(), any());
        verify(rallyIssueAccountHierarchyProcessor, never()).createAccountHierarchy(any(), any(), any());
        verify(rallyIssueAssigneeProcessor, never()).createAssigneeDetails(any(), any());
    }

    @Test
    public void testProcessWithEmptyBoardId() throws Exception {
        // Set board ID to empty
        readData.setBoardId("");
        
        // Mock dependencies
        when(rallyIssueProcessor.convertToJiraIssue(any(), any(), any(), any())).thenReturn(jiraIssue);
        when(rallyIssueHistoryProcessor.convertToJiraIssueHistory(any(), any(), any())).thenReturn(jiraIssueCustomHistory);
        when(sprintDataProcessor.processSprintData(any(), any(), any(), any())).thenReturn(sprintDetails);
        when(rallyIssueAccountHierarchyProcessor.createAccountHierarchy(any(), any(), any())).thenReturn(projectHierarchies);
        when(rallyIssueAssigneeProcessor.createAssigneeDetails(any(), any())).thenReturn(assigneeDetails);

        // Execute the method
        CompositeResult result = issueScrumProcessor.process(readData);

        // Verify results
        assertNotNull(result, "Result should not be null");
        assertEquals(jiraIssue, result.getJiraIssue(), "JiraIssue should match");
        assertEquals(jiraIssueCustomHistory, result.getJiraIssueCustomHistory(), "JiraIssueCustomHistory should match");
        assertEquals(sprintDetails, result.getSprintDetailsSet(), "SprintDetailsSet should match when boardId is empty");
        assertEquals(projectHierarchies, result.getProjectHierarchies(), "ProjectHierarchies should match");
        assertEquals(assigneeDetails, result.getAssigneeDetails(), "AssigneeDetails should match");
        
        // Verify interactions
        verify(rallyIssueProcessor, times(1)).convertToJiraIssue(any(), any(), any(), any());
        verify(rallyIssueHistoryProcessor, times(1)).convertToJiraIssueHistory(any(), any(), any());
        verify(sprintDataProcessor, times(1)).processSprintData(any(), any(), any(), any());
        verify(rallyIssueAccountHierarchyProcessor, times(1)).createAccountHierarchy(any(), any(), any());
        verify(rallyIssueAssigneeProcessor, times(1)).createAssigneeDetails(any(), any());
    }
    
    @Test
    public void testProcessWithIOException() throws Exception {
        // Mock dependencies
        when(rallyIssueProcessor.convertToJiraIssue(any(), any(), any(), any())).thenReturn(jiraIssue);
        when(rallyIssueHistoryProcessor.convertToJiraIssueHistory(any(), any(), any())).thenReturn(jiraIssueCustomHistory);
        when(sprintDataProcessor.processSprintData(any(), any(), any(), any())).thenThrow(new IOException("Test IO Exception"));

        // Execute the method and expect an IOException
        Exception exception = assertThrows(IOException.class, () -> {
            issueScrumProcessor.process(readData);
        });
        
        // Verify exception message
        assertEquals("Test IO Exception", exception.getMessage());
        
        // Verify interactions
        verify(rallyIssueProcessor, times(1)).convertToJiraIssue(any(), any(), any(), any());
        verify(rallyIssueHistoryProcessor, times(1)).convertToJiraIssueHistory(any(), any(), any());
        verify(sprintDataProcessor, times(1)).processSprintData(any(), any(), any(), any());
        verify(rallyIssueAccountHierarchyProcessor, never()).createAccountHierarchy(any(), any(), any());
        verify(rallyIssueAssigneeProcessor, never()).createAssigneeDetails(any(), any());
    }
}
