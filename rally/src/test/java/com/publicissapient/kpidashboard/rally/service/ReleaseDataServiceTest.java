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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectToolConfigRepository;
import com.publicissapient.kpidashboard.rally.model.ReleaseDataResponse;

@ExtendWith(MockitoExtension.class)
public class ReleaseDataServiceTest {

    @Mock
    private FetchScrumReleaseData fetchScrumReleaseData;
    
    @Mock
    private ProjectBasicConfigRepository projectBasicConfigRepository;
    
    @Mock
    private ProjectToolConfigRepository projectToolConfigRepository;
    
    @InjectMocks
    private ReleaseDataService releaseDataService;
    
    private static final String PROJECT_ID = "5e7c9c0f1d3a2a0001a2b3c4";
    private static final String PROJECT_NAME = "Test Project";
    private static final String TOOL_NAME = "Rally";
    
    private ProjectBasicConfig projectConfig;
    private ProjectToolConfig toolConfig;
    private List<ProjectToolConfig> toolConfigs;
    private ObjectId objectId;
    
    @BeforeEach
    void setUp() {
        objectId = new ObjectId(PROJECT_ID);
        
        // Set up ProjectBasicConfig
        projectConfig = new ProjectBasicConfig();
        projectConfig.setId(objectId);
        projectConfig.setProjectName(PROJECT_NAME);
        
        // Set up ProjectToolConfig
        toolConfig = new ProjectToolConfig();
        toolConfig.setId(new ObjectId());
        toolConfig.setToolName(TOOL_NAME);
        toolConfig.setBasicProjectConfigId(objectId);
        
        toolConfigs = new ArrayList<>();
        toolConfigs.add(toolConfig);
    }
    
    @Test
    @DisplayName("Should return NOT_FOUND when project config does not exist")
    void testFetchAndProcessReleaseData_ProjectNotFound() {
        // Act
        ResponseEntity<ReleaseDataResponse> response = releaseDataService.fetchAndProcessReleaseData(PROJECT_ID);
        
        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().isSuccess());
        assertEquals("Project not found with ID: " + PROJECT_ID, response.getBody().getError());
        
        // Verify interactions
        verify(projectBasicConfigRepository, times(1)).findActiveProjectsById(false,objectId.toString());
        verify(projectToolConfigRepository, times(0)).findByToolNameAndBasicProjectConfigId(any(), any());
    }
    
    @Test
    @DisplayName("Should return NOT_FOUND when tool config does not exist")
    void testFetchAndProcessReleaseData_ToolConfigNotFound() {
        // Arrange
        when(projectBasicConfigRepository.findActiveProjectsById(any(),any())).thenReturn(Optional.of(projectConfig));
        when(projectToolConfigRepository.findByToolNameAndBasicProjectConfigId(TOOL_NAME, objectId))
            .thenReturn(new ArrayList<>());
        
        // Act
        ResponseEntity<ReleaseDataResponse> response = releaseDataService.fetchAndProcessReleaseData(PROJECT_ID);
        
        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().isSuccess());
        assertEquals("Rally tool configuration not found for project: " + PROJECT_ID, response.getBody().getError());
        
        // Verify interactions
        verify(projectBasicConfigRepository, times(1)).findActiveProjectsById(false, objectId.toString());
        verify(projectToolConfigRepository, times(1)).findByToolNameAndBasicProjectConfigId(TOOL_NAME, objectId);
    }
    
    @Test
    @DisplayName("Should return BAD_REQUEST when IllegalArgumentException is thrown")
    void testFetchAndProcessReleaseData_IllegalArgumentException() {
        // Arrange
        String invalidId = "invalid-id";
        
        // Act
        ResponseEntity<ReleaseDataResponse> response = releaseDataService.fetchAndProcessReleaseData(invalidId);
        
        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().isSuccess());
    }
}
