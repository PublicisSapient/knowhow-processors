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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.Iteration;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.service.RallyCommonService;

@ExtendWith(MockitoExtension.class)
public class SprintDataProcessorImplTest {

    @InjectMocks
    private SprintDataProcessorImpl sprintDataProcessor;

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private RallyCommonService rallyCommonService;

    private HierarchicalRequirement hierarchicalRequirement;
    private ProjectConfFieldMapping projectConfig;
    private String boardId;
    private ObjectId processorId;
    private Iteration iteration;
    private List<HierarchicalRequirement> hierarchicalRequirements;

    @BeforeEach
    public void setup() {
        // Initialize basic objects
        hierarchicalRequirement = new HierarchicalRequirement();
        projectConfig = new ProjectConfFieldMapping();
        boardId = "RALLY-BOARD-123";
        processorId = new ObjectId();
        iteration = new Iteration();
        hierarchicalRequirements = new ArrayList<>();
        
        // Set up ProjectConfFieldMapping
        projectConfig.setProjectName("Test Rally Project");
        ObjectId basicProjectConfigId = new ObjectId();
        projectConfig.setBasicProjectConfigId(basicProjectConfigId);
        
        // Set up ProjectBasicConfig
        ProjectBasicConfig projectBasicConfig = new ProjectBasicConfig();
        projectBasicConfig.setProjectNodeId("node123");
        projectConfig.setProjectBasicConfig(projectBasicConfig);
        
        // Set up Iteration
        iteration.setName("Sprint 1");
        iteration.setStartDate("2025-05-01");
        iteration.setEndDate("2025-05-15");
        iteration.setObjectID("IT1234");
        iteration.setState("Planning");
        
        // Set up HierarchicalRequirement
        hierarchicalRequirement.setObjectID("12345");
        hierarchicalRequirement.setFormattedID("US1234");
        hierarchicalRequirement.setName("Test User Story");
        hierarchicalRequirement.setScheduleState("Defined");
        hierarchicalRequirement.setPlanEstimate(8.0);
        hierarchicalRequirement.setType("HierarchicalRequirement");
        hierarchicalRequirement.setIteration(iteration);
        
        // Add more hierarchical requirements for testing
        HierarchicalRequirement req1 = new HierarchicalRequirement();
        req1.setObjectID("12346");
        req1.setFormattedID("US1235");
        req1.setName("Test User Story 2");
        req1.setScheduleState("Accepted");
        req1.setPlanEstimate(5.0);
        req1.setType("HierarchicalRequirement");
        Iteration it1 = new Iteration();
        it1.setName("Sprint 1");
        it1.setObjectID("IT1234");
        req1.setIteration(it1);
        
        HierarchicalRequirement req2 = new HierarchicalRequirement();
        req2.setObjectID("12347");
        req2.setFormattedID("US1236");
        req2.setName("Test User Story 3");
        req2.setScheduleState("In-Progress");
        req2.setPlanEstimate(3.0);
        req2.setType("HierarchicalRequirement");
        Iteration it2 = new Iteration();
        it2.setName("Sprint 1");
        it2.setObjectID("IT1234");
        req2.setIteration(it2);
        
        hierarchicalRequirements.add(hierarchicalRequirement);
        hierarchicalRequirements.add(req1);
        hierarchicalRequirements.add(req2);
    }

    @Test
    public void testProcessSprintDataWithNewSprint() throws IOException {
        // Mock rallyCommonService to return hierarchicalRequirements
        when(rallyCommonService.getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class)))
                .thenReturn(hierarchicalRequirements);
        
        // Mock sprintRepository to return null (no existing sprint)
        String sprintId = iteration.getObjectID() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR + 
                projectConfig.getProjectBasicConfig().getProjectNodeId();
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(null);
        
        // Execute the method
        Set<SprintDetails> result = sprintDataProcessor.processSprintData(hierarchicalRequirement, projectConfig, boardId, processorId);
        
        // Verify results
        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "Should return one sprint details");
        
        SprintDetails sprintDetails = result.iterator().next();
        assertEquals("Sprint 1", sprintDetails.getSprintName(), "Sprint name should match");
        assertEquals(sprintId, sprintDetails.getSprintID(), "Sprint ID should match");
        assertEquals("2025-05-01", sprintDetails.getStartDate(), "Start date should match");
        assertEquals("2025-05-15", sprintDetails.getEndDate(), "End date should match");
        assertEquals("closed", sprintDetails.getState(), "State should be closed");
        assertEquals(projectConfig.getBasicProjectConfigId(), sprintDetails.getBasicProjectConfigId(), "BasicProjectConfigId should match");
        assertEquals(processorId, sprintDetails.getProcessorId(), "ProcessorId should match");
        
        // Verify total issues
        assertNotNull(sprintDetails.getTotalIssues(), "Total issues should not be null");
        assertEquals(3, sprintDetails.getTotalIssues().size(), "Should have 3 total issues");
        
        // Verify completed issues
        assertNotNull(sprintDetails.getCompletedIssues(), "Completed issues should not be null");
        assertEquals(1, sprintDetails.getCompletedIssues().size(), "Should have 1 completed issue");
        
        // Verify not completed issues
        assertNotNull(sprintDetails.getNotCompletedIssues(), "Not completed issues should not be null");
        assertEquals(2, sprintDetails.getNotCompletedIssues().size(), "Should have 2 not completed issues");
        
        // Verify interactions
        verify(rallyCommonService, times(1)).getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class));
        verify(sprintRepository, times(1)).findBySprintID(anyString());
    }
    
    @Test
    public void testProcessSprintDataWithExistingSprint() throws IOException {
        // Mock rallyCommonService to return hierarchicalRequirements
        when(rallyCommonService.getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class)))
                .thenReturn(hierarchicalRequirements);
        
        // Mock sprintRepository to return an existing sprint
        String sprintId = iteration.getObjectID() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR + 
                projectConfig.getProjectBasicConfig().getProjectNodeId();
        
        SprintDetails existingSprintDetails = new SprintDetails();
        existingSprintDetails.setSprintID(sprintId);
        existingSprintDetails.setSprintName("Existing Sprint 1");
        existingSprintDetails.setStartDate("2025-05-01");
        existingSprintDetails.setEndDate("2025-05-15");
        existingSprintDetails.setState("active");
        
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(existingSprintDetails);
        
        // Execute the method
        Set<SprintDetails> result = sprintDataProcessor.processSprintData(hierarchicalRequirement, projectConfig, boardId, processorId);
        
        // Verify results
        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "Should return one sprint details");
        
        SprintDetails sprintDetails = result.iterator().next();
        assertEquals("Sprint 1", sprintDetails.getSprintName(), "Sprint name should be updated");
        assertEquals(sprintId, sprintDetails.getSprintID(), "Sprint ID should match");
        assertEquals("2025-05-01", sprintDetails.getStartDate(), "Start date should match");
        assertEquals("2025-05-15", sprintDetails.getEndDate(), "End date should match");
        assertEquals("closed", sprintDetails.getState(), "State should be closed");
        
        // Verify total issues
        assertNotNull(sprintDetails.getTotalIssues(), "Total issues should not be null");
        assertEquals(3, sprintDetails.getTotalIssues().size(), "Should have 3 total issues");
        
        // Verify interactions
        verify(rallyCommonService, times(1)).getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class));
        verify(sprintRepository, times(1)).findBySprintID(anyString());
    }
    
    @Test
    public void testProcessSprintDataWithNullIteration() throws IOException {
        // Set iteration to null
        hierarchicalRequirement.setIteration(null);
        
        // Execute the method
        Set<SprintDetails> result = sprintDataProcessor.processSprintData(hierarchicalRequirement, projectConfig, boardId, processorId);
        
        // Verify results
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Result should be empty when iteration is null");
        
        // Verify interactions
        verify(rallyCommonService, times(0)).getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class));
        verify(sprintRepository, times(0)).findBySprintID(anyString());
    }
    
    @Test
    public void testProcessSprintDataWithEmptyHierarchicalRequirements() throws IOException {
        // Mock rallyCommonService to return empty list
        when(rallyCommonService.getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class)))
                .thenReturn(new ArrayList<>());
        
        // Mock sprintRepository to return null (no existing sprint)
        String sprintId = iteration.getObjectID() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR + 
                projectConfig.getProjectBasicConfig().getProjectNodeId();
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(null);
        
        // Execute the method
        Set<SprintDetails> result = sprintDataProcessor.processSprintData(hierarchicalRequirement, projectConfig, boardId, processorId);
        
        // Verify results
        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "Should return one sprint details");
        
        SprintDetails sprintDetails = result.iterator().next();
        assertEquals("Sprint 1", sprintDetails.getSprintName(), "Sprint name should match");
        assertEquals(sprintId, sprintDetails.getSprintID(), "Sprint ID should match");
        
        // Verify total issues
        assertNotNull(sprintDetails.getTotalIssues(), "Total issues should not be null");
        assertEquals(0, sprintDetails.getTotalIssues().size(), "Should have 0 total issues");
        
        // Verify completed issues
        assertNotNull(sprintDetails.getCompletedIssues(), "Completed issues should not be null");
        assertEquals(0, sprintDetails.getCompletedIssues().size(), "Should have 0 completed issues");
        
        // Verify not completed issues
        assertNotNull(sprintDetails.getNotCompletedIssues(), "Not completed issues should not be null");
        assertEquals(0, sprintDetails.getNotCompletedIssues().size(), "Should have 0 not completed issues");
        
        // Verify interactions
        verify(rallyCommonService, times(1)).getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class));
        verify(sprintRepository, times(1)).findBySprintID(anyString());
    }
    
    @Test
    public void testProcessSprintDataWithMixedCompletionStatus() throws IOException {
        // Create a list with mixed completion status
        List<HierarchicalRequirement> mixedRequirements = new ArrayList<>();
        
        // Add 3 Accepted requirements
        for (int i = 0; i < 3; i++) {
            HierarchicalRequirement req = new HierarchicalRequirement();
            req.setObjectID("123" + i);
            req.setFormattedID("US123" + i);
            req.setName("Accepted Story " + i);
            req.setScheduleState("Accepted");
            req.setPlanEstimate(3.0);
            req.setType("HierarchicalRequirement");
            Iteration it = new Iteration();
            it.setName("Sprint 1");
            it.setObjectID("IT1234");
            req.setIteration(it);
            mixedRequirements.add(req);
        }
        
        // Add 2 In-Progress requirements
        for (int i = 0; i < 2; i++) {
            HierarchicalRequirement req = new HierarchicalRequirement();
            req.setObjectID("124" + i);
            req.setFormattedID("US124" + i);
            req.setName("In-Progress Story " + i);
            req.setScheduleState("In-Progress");
            req.setPlanEstimate(2.0);
            req.setType("HierarchicalRequirement");
            Iteration it = new Iteration();
            it.setName("Sprint 1");
            it.setObjectID("IT1234");
            req.setIteration(it);
            mixedRequirements.add(req);
        }
        
        // Add 1 Defined requirement
        HierarchicalRequirement req = new HierarchicalRequirement();
        req.setObjectID("1250");
        req.setFormattedID("US1250");
        req.setName("Defined Story");
        req.setScheduleState("Defined");
        req.setPlanEstimate(5.0);
        req.setType("HierarchicalRequirement");
        Iteration it = new Iteration();
        it.setName("Sprint 1");
        it.setObjectID("IT1234");
        req.setIteration(it);
        mixedRequirements.add(req);
        
        // Mock rallyCommonService to return mixed requirements
        when(rallyCommonService.getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class)))
                .thenReturn(mixedRequirements);
        
        // Mock sprintRepository to return null (no existing sprint)
        String sprintId = iteration.getObjectID() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR + 
                projectConfig.getProjectBasicConfig().getProjectNodeId();
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(null);
        
        // Execute the method
        Set<SprintDetails> result = sprintDataProcessor.processSprintData(hierarchicalRequirement, projectConfig, boardId, processorId);
        
        // Verify results
        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "Should return one sprint details");
        
        SprintDetails sprintDetails = result.iterator().next();
        
        // Verify total issues
        assertNotNull(sprintDetails.getTotalIssues(), "Total issues should not be null");
        assertEquals(6, sprintDetails.getTotalIssues().size(), "Should have 6 total issues");
        
        // Verify completed issues
        assertNotNull(sprintDetails.getCompletedIssues(), "Completed issues should not be null");
        assertEquals(3, sprintDetails.getCompletedIssues().size(), "Should have 3 completed issues");
        
        // Verify not completed issues
        assertNotNull(sprintDetails.getNotCompletedIssues(), "Not completed issues should not be null");
        assertEquals(3, sprintDetails.getNotCompletedIssues().size(), "Should have 3 not completed issues");
        
        // Verify story points
        double totalStoryPoints = sprintDetails.getTotalIssues().stream()
                .mapToDouble(SprintIssue::getStoryPoints)
                .sum();
        assertEquals(18.0, totalStoryPoints, "Total story points should be 18.0");
        
        double completedStoryPoints = sprintDetails.getCompletedIssues().stream()
                .mapToDouble(SprintIssue::getStoryPoints)
                .sum();
        assertEquals(9.0, completedStoryPoints, "Completed story points should be 9.0");
        
        // Verify interactions
        verify(rallyCommonService, times(1)).getHierarchicalRequirementsByIteration(any(Iteration.class), any(HierarchicalRequirement.class));
        verify(sprintRepository, times(1)).findBySprintID(anyString());
    }
}
