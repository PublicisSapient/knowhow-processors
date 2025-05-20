package com.publicissapient.kpidashboard.rally.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;

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
import com.publicissapient.kpidashboard.rally.helper.AdditionalFilterHelper;
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
    private AdditionalFilterHelper additionalFilterHelper;

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
        assertEquals(hierarchicalRequirement.getFormattedID(), result.getIssueId());
        assertEquals(hierarchicalRequirement.getType(), result.getTypeName());
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
}
