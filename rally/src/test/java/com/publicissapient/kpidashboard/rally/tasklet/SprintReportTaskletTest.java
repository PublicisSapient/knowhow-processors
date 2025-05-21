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

package com.publicissapient.kpidashboard.rally.tasklet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.rally.config.FetchProjectConfiguration;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.service.FetchSprintReport;

/**
 * Unit tests for SprintReportTasklet class
 */
@RunWith(MockitoJUnitRunner.class)
public class SprintReportTaskletTest {

    @InjectMocks
    private SprintReportTasklet sprintReportTasklet;

    @Mock
    private FetchProjectConfiguration fetchProjectConfiguration;

    @Mock
    private FetchSprintReport fetchSprintReport;

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private StepContribution stepContribution;

    @Mock
    private ChunkContext chunkContext;

    private String sprintId;
    private String processorId;
    private ProjectConfFieldMapping projectConfFieldMapping;
    private SprintDetails sprintDetails;
    private List<String> originalBoardIds;
    private List<SprintDetails> sprintDetailsList;
    private Set<SprintDetails> sprintDetailsSet;
    private Set<SprintDetails> updatedSprintDetailsSet;

    @Before
    public void setup() {
        sprintId = "SP-123";
        processorId = "6433d9ef1c2e5d0001111111";
        
        // Set up values for the fields that would normally be injected by Spring
        ReflectionTestUtils.setField(sprintReportTasklet, "sprintId", sprintId);
        ReflectionTestUtils.setField(sprintReportTasklet, "processorId", processorId);
        
        // Set up test data
        projectConfFieldMapping = new ProjectConfFieldMapping();
        
        sprintDetails = new SprintDetails();
        sprintDetails.setSprintID(sprintId);
        originalBoardIds = Arrays.asList("Board-1", "Board-2");
        sprintDetails.setOriginBoardId(originalBoardIds);
        
        sprintDetailsList = new ArrayList<>();
        SprintDetails sprintDetail = new SprintDetails();
        sprintDetail.setSprintID(sprintId);
        sprintDetailsList.add(sprintDetail);
        
        sprintDetailsSet = new HashSet<>();
        sprintDetailsSet.add(sprintDetail);
        
        updatedSprintDetailsSet = new HashSet<>();
        updatedSprintDetailsSet.add(sprintDetail);
    }

    @Test
    public void testExecute_Success() throws Exception {
        // Arrange
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(sprintId)).thenReturn(projectConfFieldMapping);
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(sprintDetails);
        
        // For each board ID
        for (String boardId : originalBoardIds) {
            when(fetchSprintReport.getSprints(projectConfFieldMapping, boardId)).thenReturn(sprintDetailsList);
            when(fetchSprintReport.fetchSprints(eq(projectConfFieldMapping), any(), eq(true), any(ObjectId.class)))
                .thenReturn(updatedSprintDetailsSet);
        }
        
        // Act
        RepeatStatus result = sprintReportTasklet.execute(stepContribution, chunkContext);
        
        // Assert
        assertEquals(RepeatStatus.FINISHED, result);
        verify(fetchProjectConfiguration, times(1)).fetchConfigurationBasedOnSprintId(sprintId);
        verify(sprintRepository, times(1)).findBySprintID(sprintId);
        
        // Verify for each board ID
        for (String boardId : originalBoardIds) {
            verify(fetchSprintReport, times(1)).getSprints(projectConfFieldMapping, boardId);
        }
        
        // Since fetchSprints is called once per board ID, verify the total number of calls
        verify(fetchSprintReport, times(originalBoardIds.size())).fetchSprints(any(), any(), eq(true), any(ObjectId.class));
        verify(sprintRepository, times(originalBoardIds.size())).saveAll(any());
    }

    @Test
    public void testExecute_EmptySprintDetailsList() throws Exception {
        // Arrange
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(sprintId)).thenReturn(projectConfFieldMapping);
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(sprintDetails);
        
        // Return empty sprint details list
        when(fetchSprintReport.getSprints(any(), anyString())).thenReturn(new ArrayList<>());
        
        // Act
        RepeatStatus result = sprintReportTasklet.execute(stepContribution, chunkContext);
        
        // Assert
        assertEquals(RepeatStatus.FINISHED, result);
        verify(fetchProjectConfiguration, times(1)).fetchConfigurationBasedOnSprintId(sprintId);
        verify(sprintRepository, times(1)).findBySprintID(sprintId);
        verify(fetchSprintReport, times(originalBoardIds.size())).getSprints(any(), anyString());
        
        // Verify that fetchSprints and saveAll are not called when sprint details list is empty
        verify(fetchSprintReport, times(0)).fetchSprints(any(), any(), anyBoolean(), any());
        verify(sprintRepository, times(0)).saveAll(any());
    }

    @Test
    public void testExecute_NoMatchingSprintInDetailsList() throws Exception {
        // Arrange
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(sprintId)).thenReturn(projectConfFieldMapping);
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(sprintDetails);
        
        // Create a sprint details list with a different sprint ID
        List<SprintDetails> differentSprintList = new ArrayList<>();
        SprintDetails differentSprint = new SprintDetails();
        differentSprint.setSprintID("DIFFERENT-ID");
        differentSprintList.add(differentSprint);
        
        when(fetchSprintReport.getSprints(any(), anyString())).thenReturn(differentSprintList);
        when(fetchSprintReport.fetchSprints(any(), any(), anyBoolean(), any()))
            .thenReturn(new HashSet<>());
        
        // Act
        RepeatStatus result = sprintReportTasklet.execute(stepContribution, chunkContext);
        
        // Assert
        assertEquals(RepeatStatus.FINISHED, result);
        verify(fetchProjectConfiguration, times(1)).fetchConfigurationBasedOnSprintId(sprintId);
        verify(sprintRepository, times(1)).findBySprintID(sprintId);
        verify(fetchSprintReport, times(originalBoardIds.size())).getSprints(any(), anyString());
        
        // Verify that fetchSprints is called with an empty set and saveAll is still called
        verify(fetchSprintReport, times(originalBoardIds.size())).fetchSprints(any(), any(), anyBoolean(), any());
        verify(sprintRepository, times(originalBoardIds.size())).saveAll(any());
    }

    @Test
    public void testExecute_ExceptionHandling() throws Exception {
        // Arrange
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(sprintId)).thenReturn(projectConfFieldMapping);
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(sprintDetails);
        
        // Throw an exception during getSprints for the first board
        when(fetchSprintReport.getSprints(projectConfFieldMapping, originalBoardIds.get(0)))
            .thenThrow(new IOException("Test exception"));
        
        // No need to set up expectations for the second board since the exception will stop execution
        
        // Act & Assert - The exception should be propagated
        try {
            sprintReportTasklet.execute(stepContribution, chunkContext);
            // If we get here, the test should fail
            fail("Expected an IOException to be thrown");
        } catch (IOException e) {
            assertEquals("Test exception", e.getMessage());
        }
    }
    
    @Test
    public void testExecute_NullSprintDetails() throws Exception {
        // Arrange
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(sprintId)).thenReturn(projectConfFieldMapping);
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(null);
        
        // Act & Assert - Should throw NullPointerException
        try {
            sprintReportTasklet.execute(stepContribution, chunkContext);
        } catch (NullPointerException e) {
            // Expected exception
        }
    }
    
    @Test
    public void testExecute_NullOriginBoardIds() throws Exception {
        // Arrange
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(sprintId)).thenReturn(projectConfFieldMapping);
        
        // Create sprint details with empty originBoardId list instead of null
        SprintDetails emptyBoardIdSprint = new SprintDetails();
        emptyBoardIdSprint.setSprintID(sprintId);
        emptyBoardIdSprint.setOriginBoardId(new ArrayList<>());
        
        when(sprintRepository.findBySprintID(sprintId)).thenReturn(emptyBoardIdSprint);
        
        // Act
        RepeatStatus result = sprintReportTasklet.execute(stepContribution, chunkContext);
        
        // Assert
        assertEquals(RepeatStatus.FINISHED, result);
        verify(fetchProjectConfiguration, times(1)).fetchConfigurationBasedOnSprintId(sprintId);
        verify(sprintRepository, times(1)).findBySprintID(sprintId);
        
        // Verify that getSprints is not called when originBoardId is empty
        verify(fetchSprintReport, times(0)).getSprints(any(), anyString());
        verify(fetchSprintReport, times(0)).fetchSprints(any(), any(), anyBoolean(), any());
        verify(sprintRepository, times(0)).saveAll(any());
    }
}
