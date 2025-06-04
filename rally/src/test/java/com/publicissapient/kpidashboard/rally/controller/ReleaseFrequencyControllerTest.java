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
package com.publicissapient.kpidashboard.rally.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.rally.model.ReleaseDataResponse;
import com.publicissapient.kpidashboard.rally.service.ReleaseDataService;

@ExtendWith(MockitoExtension.class)
public class ReleaseFrequencyControllerTest {

    @Mock
    private ReleaseDataService releaseDataService;
    
    @InjectMocks
    private ReleaseFrequencyController releaseFrequencyController;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    private static final String PROJECT_ID = "5e7c9c0f1d3a2a0001a2b3c4";
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(releaseFrequencyController).build();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    @DisplayName("Should return success response when fetchReleaseData is called with valid project ID")
    void testFetchReleaseData_Success() throws Exception {
        // Arrange
        ReleaseDataResponse successResponse = ReleaseDataResponse.success("Successfully fetched data");
        ResponseEntity<ReleaseDataResponse> responseEntity = ResponseEntity.ok(successResponse);
        
        when(releaseDataService.fetchAndProcessReleaseData(PROJECT_ID)).thenReturn(responseEntity);
        
        // Act & Assert
        mockMvc.perform(get("/rally/release/fetch-data")
                .param("projectId", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(successResponse)));
        
        // Verify
        verify(releaseDataService).fetchAndProcessReleaseData(PROJECT_ID);
    }
    
    @Test
    @DisplayName("Should return error response when fetchReleaseData is called with invalid project ID")
    void testFetchReleaseData_Error() throws Exception {
        // Arrange
        ReleaseDataResponse errorResponse = ReleaseDataResponse.error("Project not found");
        ResponseEntity<ReleaseDataResponse> responseEntity = 
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        
        when(releaseDataService.fetchAndProcessReleaseData(PROJECT_ID)).thenReturn(responseEntity);
        
        // Act & Assert
        mockMvc.perform(get("/rally/release/fetch-data")
                .param("projectId", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().json(objectMapper.writeValueAsString(errorResponse)));
        
        // Verify
        verify(releaseDataService).fetchAndProcessReleaseData(PROJECT_ID);
    }
    
    @Test
    @DisplayName("Should return bad request when projectId parameter is missing")
    void testFetchReleaseData_MissingParameter() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/rally/release/fetch-data")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
