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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.Iteration;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.repository.jira.AssigneeDetailsRepository;

@ExtendWith(MockitoExtension.class)
public class RallyIssueProcessorImplTest {

    @InjectMocks
    private RallyIssueProcessorImpl rallyIssueProcessor;

    @Mock
    private JiraIssueRepository jiraIssueRepository;

    @Mock
    private RallyProcessorConfig rallyProcessorConfig;

    @Mock
    private AssigneeDetailsRepository assigneeDetailsRepository;

    private ProjectConfFieldMapping projectConfig;
    private HierarchicalRequirement hierarchicalRequirement;
    private FieldMapping fieldMapping;
    private ObjectId processorId;
    private String boardId;

    @BeforeEach
    public void setup() {
        projectConfig = new ProjectConfFieldMapping();
        hierarchicalRequirement = new HierarchicalRequirement();
        fieldMapping = new FieldMapping();
        processorId = new ObjectId();
        boardId = "TEST-BOARD-1";

        // Set up ProjectConfFieldMapping
        ObjectId basicProjectConfigId = new ObjectId();
        projectConfig.setBasicProjectConfigId(basicProjectConfigId);
        projectConfig.setFieldMapping(fieldMapping);
        projectConfig.setProjectName("Test Rally Project");
        
        // Set up ProjectToolConfig
        ProjectToolConfig projectToolConfig = new ProjectToolConfig();
        projectToolConfig.setProjectKey("RALLY");
        projectConfig.setProjectToolConfig(projectToolConfig);
        
        // Set up ProjectBasicConfig
        ProjectBasicConfig projectBasicConfig = new ProjectBasicConfig();
        projectBasicConfig.setProjectNodeId("node123");
        projectConfig.setProjectBasicConfig(projectBasicConfig);
        
        // Set up HierarchicalRequirement
        hierarchicalRequirement.setObjectID("12345");
        hierarchicalRequirement.setFormattedID("US1234");
        hierarchicalRequirement.setName("Test User Story");
        hierarchicalRequirement.setScheduleState("Defined");
        hierarchicalRequirement.setPlanEstimate(8.0);
        hierarchicalRequirement.setType("HierarchicalRequirement");
        hierarchicalRequirement.setCreationDate("2025-05-01T10:00:00Z");
        hierarchicalRequirement.setLastUpdateDate("2025-05-20T15:30:00Z");
        
        // Set up FieldMapping
        fieldMapping.setJiradefecttype(Arrays.asList("Defect"));
    }

    @Test
    public void testConvertToJiraIssueNewIssue() throws Exception {
        // Mock repository to return null (new issue)
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify results
        assertNotNull(result);
        assertEquals(processorId, result.getProcessorId());
        assertEquals(hierarchicalRequirement.getScheduleState(), result.getJiraStatus());
        assertEquals(hierarchicalRequirement.getObjectID(), result.getTypeId());
        assertEquals(hierarchicalRequirement.getType(), result.getOriginalType());
        assertEquals(hierarchicalRequirement.getFormattedID(), result.getNumber());
        assertEquals(hierarchicalRequirement.getName(), result.getName());
        assertEquals(hierarchicalRequirement.getScheduleState(), result.getStatus());
        assertEquals(hierarchicalRequirement.getScheduleState(), result.getState());
        assertEquals(String.valueOf(hierarchicalRequirement.getPlanEstimate()), result.getEstimate());
        assertEquals(hierarchicalRequirement.getPlanEstimate(), result.getStoryPoints());
        assertEquals(boardId, result.getBoardId());
        assertEquals(projectConfig.getProjectName(), result.getProjectName());
        assertEquals(projectConfig.getProjectToolConfig().getProjectKey(), result.getProjectKey());
        assertEquals(projectConfig.getBasicProjectConfigId().toString(), result.getBasicProjectConfigId());
    }

    @Test
    public void testConvertToJiraIssueExistingIssue() throws Exception {
        // Create an existing issue
        JiraIssue existingIssue = new JiraIssue();
        existingIssue.setNumber("EXISTING-123");
        existingIssue.setName("Existing Issue");

        // Mock repository to return the existing issue
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(existingIssue);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify results - should update the existing issue
        assertNotNull(result);
        assertEquals(hierarchicalRequirement.getFormattedID(), result.getNumber()); // Should be updated
        assertEquals(hierarchicalRequirement.getName(), result.getName()); // Should be updated
        assertEquals(processorId, result.getProcessorId());
        assertEquals(hierarchicalRequirement.getScheduleState(), result.getJiraStatus());
    }

    @Test
    public void testConvertToJiraIssueWithNullFieldMapping() throws Exception {
        // Set field mapping to null
        projectConfig.setFieldMapping(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify results - should return null
        assertNull(result);
    }

    @Test
    public void testConvertToJiraIssueWithDefectType() throws Exception {
        // Set up hierarchical requirement as a defect
        hierarchicalRequirement.setType("Defect");

        // Mock repository
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify results - should set defect type
        assertNotNull(result);
        assertEquals(NormalizedJira.DEFECT_TYPE.getValue(), result.getTypeName());
    }

    @Test
    public void testConvertToJiraIssueWithIteration() throws Exception {
        // Set up iteration data
        Iteration iteration = new Iteration();
        iteration.setName("Sprint 1");
        iteration.setStartDate("2025-05-01");
        iteration.setEndDate("2025-05-15");
        iteration.setObjectID("IT1234");
        iteration.setState("Planning");
        hierarchicalRequirement.setIteration(iteration);

        // Mock repository
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify results - should include sprint data
        assertNotNull(result);
        assertEquals(iteration.getName(), result.getSprintName());
        assertEquals(iteration.getStartDate(), result.getSprintBeginDate());
        assertEquals(iteration.getEndDate(), result.getSprintEndDate());
        assertEquals(iteration.getState(), result.getSprintAssetState());
        assertEquals(iteration.getObjectID() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR + 
                    projectConfig.getProjectBasicConfig().getProjectNodeId(), result.getSprintID());
    }
    
    @Test
    public void testConvertToJiraIssueWithNullIteration() throws Exception {
        // Ensure iteration is null
        hierarchicalRequirement.setIteration(null);

        // Mock repository
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify results
        assertNotNull(result);
        assertNull(result.getSprintName(), "Sprint name should be null when iteration is null");
        assertNull(result.getSprintBeginDate(), "Sprint begin date should be null when iteration is null");
        assertNull(result.getSprintEndDate(), "Sprint end date should be null when iteration is null");
        assertNull(result.getSprintAssetState(), "Sprint asset state should be null when iteration is null");
        assertNull(result.getSprintID(), "Sprint ID should be null when iteration is null");
    }
    
    @Test
    public void testProcessJiraIssueDataFields() throws Exception {
        // Mock repository
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify all fields are set correctly
        assertNotNull(result);
        assertEquals(hierarchicalRequirement.getFormattedID(), result.getNumber());
        assertEquals(hierarchicalRequirement.getName(), result.getName());
        assertEquals(hierarchicalRequirement.getScheduleState(), result.getStatus());
        assertEquals(hierarchicalRequirement.getScheduleState(), result.getState());
        assertEquals(String.valueOf(hierarchicalRequirement.getPlanEstimate()), result.getEstimate());
        assertEquals(hierarchicalRequirement.getPlanEstimate(), result.getStoryPoints());
        assertNotNull(result.getChangeDate(), "Change date should not be null");
        assertNotNull(result.getUpdateDate(), "Update date should not be null");
        assertEquals(RallyConstants.FALSE, result.getIsDeleted());
        assertEquals(Arrays.asList("Active"), result.getOwnersState());
        assertEquals(Collections.emptyList(), result.getOwnersChangeDate());
        assertEquals(Collections.emptyList(), result.getOwnersIsDeleted());
        assertNotNull(result.getCreatedDate(), "Created date should not be null");
    }
    
    @Test
    public void testSetProjectSpecificDetails() throws Exception {
        // Mock repository
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify project specific details
        assertNotNull(result);
        assertEquals(projectConfig.getProjectName(), result.getProjectName());
        assertEquals(projectConfig.getProjectToolConfig().getProjectKey(), result.getProjectKey());
        assertEquals(projectConfig.getBasicProjectConfigId().toString(), result.getBasicProjectConfigId());
        assertEquals("", result.getProjectBeginDate());
        assertEquals("", result.getProjectEndDate());
        assertEquals("", result.getProjectChangeDate());
        assertEquals("", result.getProjectState());
        assertEquals("False", result.getProjectIsDeleted());
        assertEquals("", result.getProjectPath());
    }
    
    @Test
    public void testSetDefectIssueTypeWithNonDefectType() throws Exception {
        // Set up hierarchical requirement with non-defect type
        hierarchicalRequirement.setType("Story");

        // Mock repository
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify results - should not change the type name
        assertNotNull(result);
        assertEquals("Story", result.getTypeName());
        assertEquals("Story", result.getOriginalType());
    }
    
    @Test
    public void testConvertToJiraIssueWithEmptyDefectTypeList() throws Exception {
        // Set field mapping with empty defect type list
        fieldMapping.setJiradefecttype(Collections.emptyList());
        
        // Set up hierarchical requirement as a defect
        hierarchicalRequirement.setType("Defect");

        // Mock repository
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);

        // Execute the method
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);

        // Verify results - should not change the type name since the defect type list is empty
        assertNotNull(result);
        assertEquals("Defect", result.getTypeName());
        assertEquals("Defect", result.getOriginalType());
    }
    
    @Test
    public void testSetStoryLinkWithDefectDirectRequirement() {
        // Create a JiraIssue with defect type
        JiraIssue jiraIssue = new JiraIssue();
        jiraIssue.setTypeName(NormalizedJira.DEFECT_TYPE.getValue());
        
        // Create a hierarchical requirement with a direct requirement link
        HierarchicalRequirement defect = new HierarchicalRequirement();
        defect.setFormattedID("DE123");
        defect.setType("Defect"); // Set the type to avoid NullPointerException
        
        // Create a story requirement and link it to the defect
        HierarchicalRequirement story = new HierarchicalRequirement();
        story.setFormattedID("US456");
        defect.setRequirement(story);
        
        // Execute the method by calling convertToJiraIssue which will call setStoryLinkWithDefect internally
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(defect, projectConfig, boardId, processorId);
        
        // Verify the defect story ID is set correctly
        assertNotNull(result.getDefectStoryID());
        assertEquals(1, result.getDefectStoryID().size());
        assertTrue(result.getDefectStoryID().contains("US456"));
    }
    
    @Test
    public void testSetStoryLinkWithDefectAdditionalPropertiesString() {
        // Create a JiraIssue with defect type
        JiraIssue jiraIssue = new JiraIssue();
        jiraIssue.setTypeName(NormalizedJira.DEFECT_TYPE.getValue());
        
        // Create a hierarchical requirement with additional properties containing a string requirement
        HierarchicalRequirement defect = new HierarchicalRequirement();
        defect.setFormattedID("DE123");
        defect.setType("Defect");
        
        // Add additional properties with a string requirement
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("Requirement", "US789");
        defect.setAdditionalProperties(additionalProperties);
        
        // Execute the method by calling convertToJiraIssue which will call setStoryLinkWithDefect internally
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(defect, projectConfig, boardId, processorId);
        
        // Verify the defect story ID is set correctly
        assertNotNull(result.getDefectStoryID());
        assertEquals(1, result.getDefectStoryID().size());
        assertTrue(result.getDefectStoryID().contains("US789"));
    }
    
    @Test
    public void testSetStoryLinkWithDefectAdditionalPropertiesList() {
        // Create a JiraIssue with defect type
        JiraIssue jiraIssue = new JiraIssue();
        jiraIssue.setTypeName(NormalizedJira.DEFECT_TYPE.getValue());
        
        // Create a hierarchical requirement with additional properties containing a list of requirements
        HierarchicalRequirement defect = new HierarchicalRequirement();
        defect.setFormattedID("DE123");
        defect.setType("Defect");
        
        // Create a map for a requirement in the list
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("FormattedID", "US101");
        
        // Create a list with both a map and a string requirement
        List<Object> reqList = Arrays.asList(reqMap, "US102");
        
        // Add additional properties with a list of requirements
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("Requirement", reqList);
        defect.setAdditionalProperties(additionalProperties);
        
        // Execute the method by calling convertToJiraIssue which will call setStoryLinkWithDefect internally
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(defect, projectConfig, boardId, processorId);
        
        // Verify the defect story ID is set correctly
        assertNotNull(result.getDefectStoryID());
        assertEquals(2, result.getDefectStoryID().size());
        assertTrue(result.getDefectStoryID().contains("US101"));
        assertTrue(result.getDefectStoryID().contains("US102"));
    }
    
    @Test
    public void testSetStoryLinkWithDefectAdditionalPropertiesMap() {
        // Create a JiraIssue with defect type
        JiraIssue jiraIssue = new JiraIssue();
        jiraIssue.setTypeName(NormalizedJira.DEFECT_TYPE.getValue());
        
        // Create a hierarchical requirement with additional properties containing a map requirement
        HierarchicalRequirement defect = new HierarchicalRequirement();
        defect.setFormattedID("DE123");
        defect.setType("Defect");
        
        // Create a map for a requirement
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("FormattedID", "US303");
        
        // Add additional properties with a map requirement
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("WorkProduct", reqMap);
        defect.setAdditionalProperties(additionalProperties);
        
        // Execute the method by calling convertToJiraIssue which will call setStoryLinkWithDefect internally
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(defect, projectConfig, boardId, processorId);
        
        // Verify the defect story ID is set correctly
        assertNotNull(result.getDefectStoryID());
        assertEquals(1, result.getDefectStoryID().size());
        assertTrue(result.getDefectStoryID().contains("US303"));
    }
    
    @Test
    public void testSetStoryLinkWithTestType() {
        // Create a JiraIssue with test type
        JiraIssue jiraIssue = new JiraIssue();
        jiraIssue.setTypeName(NormalizedJira.TEST_TYPE.getValue());
        
        // Create a hierarchical requirement with a direct requirement link
        HierarchicalRequirement testCase = new HierarchicalRequirement();
        testCase.setFormattedID("TC123");
        testCase.setType("Test");
        
        // Create a story requirement and link it to the test case
        HierarchicalRequirement story = new HierarchicalRequirement();
        story.setFormattedID("US456");
        testCase.setRequirement(story);
        
        // Execute the method by calling convertToJiraIssue which will call setStoryLinkWithDefect internally
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(testCase, projectConfig, boardId, processorId);
        
        // Verify the defect story ID is set correctly
        assertNotNull(result.getDefectStoryID());
        assertEquals(1, result.getDefectStoryID().size());
        assertTrue(result.getDefectStoryID().contains("US456"));
    }
    
    @Test
    public void testSetStoryLinkWithNonDefectType() {
        // Create a JiraIssue with story type (not defect or test)
        JiraIssue jiraIssue = new JiraIssue();
        jiraIssue.setTypeName("Story");
        
        // Create a hierarchical requirement with a direct requirement link
        HierarchicalRequirement story = new HierarchicalRequirement();
        story.setFormattedID("US123");
        story.setType("HierarchicalRequirement");
        
        // Create a requirement and link it to the story (this shouldn't be used)
        HierarchicalRequirement parentStory = new HierarchicalRequirement();
        parentStory.setFormattedID("US456");
        story.setRequirement(parentStory);
        
        // Execute the method by calling convertToJiraIssue which will call setStoryLinkWithDefect internally
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(story, projectConfig, boardId, processorId);
        
        // Verify the defect story ID is not set for non-defect types
        assertNull(result.getDefectStoryID());
    }
    
    @Test
    public void testGetJiraIssueExistingIssue() {
        // Create an existing JiraIssue
        JiraIssue existingIssue = new JiraIssue();
        existingIssue.setNumber("US123");
        existingIssue.setName("Existing Issue");
        
        // Mock repository to return the existing issue
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString()))
            .thenReturn(existingIssue);
        
        // Execute the method by calling convertToJiraIssue which will call getJiraIssue internally
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(hierarchicalRequirement, projectConfig, boardId, processorId);
        
        // Verify the existing issue was returned and updated
        assertNotNull(result);
        // The number will be updated to match the hierarchicalRequirement's FormattedID (US1234)
        assertEquals(hierarchicalRequirement.getFormattedID(), result.getNumber());
        assertEquals(hierarchicalRequirement.getName(), result.getName());
    }
    
    @Test
    public void testMultipleStoryLinks() {
        // Create a JiraIssue with defect type
        JiraIssue jiraIssue = new JiraIssue();
        jiraIssue.setTypeName(NormalizedJira.DEFECT_TYPE.getValue());
        
        // Create a hierarchical requirement with multiple story links
        HierarchicalRequirement defect = new HierarchicalRequirement();
        defect.setFormattedID("DE123");
        defect.setType("Defect");
        
        // Create a direct requirement link
        HierarchicalRequirement directStory = new HierarchicalRequirement();
        directStory.setFormattedID("US456");
        defect.setRequirement(directStory);
        
        // Create additional properties with multiple story links
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("Parent", "US789");
        
        // Create a map for another requirement
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("FormattedID", "US101");
        additionalProperties.put("Requirement.FormattedID", reqMap);
        
        defect.setAdditionalProperties(additionalProperties);
        
        // Execute the method by calling convertToJiraIssue which will call setStoryLinkWithDefect internally
        when(jiraIssueRepository.findByIssueIdAndBasicProjectConfigId(anyString(), anyString())).thenReturn(null);
        JiraIssue result = rallyIssueProcessor.convertToJiraIssue(defect, projectConfig, boardId, processorId);
        
        // Verify all story links are set correctly
        assertNotNull(result.getDefectStoryID());
        assertEquals(3, result.getDefectStoryID().size());
        assertTrue(result.getDefectStoryID().contains("US456"));
        assertTrue(result.getDefectStoryID().contains("US789"));
        assertTrue(result.getDefectStoryID().contains("US101"));
    }
}
