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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.publicissapient.kpidashboard.common.util.SecuritySanitizationUtil;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.model.jira.BoardDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.RallyToolConfig;
import com.publicissapient.kpidashboard.rally.repository.RallyProcessorRepository;
import com.publicissapient.kpidashboard.rally.util.RallyProcessorUtil;

/**
 * Unit tests for FetchSprintReportImpl class
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FetchSprintReportImplTest {

    @InjectMocks
    private FetchSprintReportImpl fetchSprintReport;

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private RallyProcessorRepository rallyProcessorRepository;

    @Mock
    private RallyProcessorConfig rallyProcessorConfig;

    @Mock
    private ProcessorToolConnectionService processorToolConnectionService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RallyProcessorUtil rallyProcessorUtil;
    
    @Mock
    private RallyCommonService rallyCommonService;

    private ProjectConfFieldMapping projectConfig;
    private Connection connection;
    private RallyToolConfig toolConfig;
    private ProjectBasicConfig basicConfig;
    private String boardId;
    private String sprintId;
    private String sprintName;
    private String sprintState;
    private String mockSprintResponse;
    private ObjectId basicProjectConfigId;

    @Before
    public void setup() throws IOException {
        // Set up basic objects
        boardId = "123";
        sprintId = "456";
        sprintName = "Test Sprint";
        sprintState = "ACTIVE";
        basicProjectConfigId = new ObjectId("5e7c9d7a8c1c4a0001a1b2c5");

        // Set up connection
        connection = new Connection();
        connection.setBaseUrl("https://rally1.rallydev.com");
        connection.setUsername("testuser");
        connection.setPassword(SecuritySanitizationUtil.generateRandomPassword(6));
        connection.setOffline(false);

        // Set up tool config
        toolConfig = new RallyToolConfig();
        toolConfig.setBasicProjectConfigId(basicProjectConfigId.toString());
        toolConfig.setConnection(Optional.of(connection));
        toolConfig.setProjectId("project123");
        toolConfig.setProjectKey("PROJ");
        
        // Set up boards
        List<BoardDetails> boards = new ArrayList<>();
        BoardDetails board = new BoardDetails();
        board.setBoardId(boardId);
        board.setBoardName("Test Board");
        boards.add(board);
        toolConfig.setBoards(boards);

        // Set up basic config
        basicConfig = new ProjectBasicConfig();
        basicConfig.setId(basicProjectConfigId);
        basicConfig.setProjectName("Test Project");
        basicConfig.setProjectNodeId("TEST_NODE_ID");

        // Set up project config
        projectConfig = new ProjectConfFieldMapping();
        projectConfig.setJira(toolConfig);
        projectConfig.setProjectBasicConfig(basicConfig);
        
        // Mock the ProjectToolConfig
        ProjectToolConfig mockProjectToolConfig = new ProjectToolConfig();
        mockProjectToolConfig.setBasicProjectConfigId(basicProjectConfigId);
        mockProjectToolConfig.setToolName("Rally");
        projectConfig.setProjectToolConfig(mockProjectToolConfig);

        // Set up mock responses
        mockSprintResponse = createMockSprintResponse();

        // Mock config values
        when(rallyProcessorConfig.getJiraSprintByBoardUrlApi()).thenReturn("rest/agile/1.0/board/{boardId}/sprint?startAt={startAtIndex}");
        
        // Mock RallyCommonService for sprint data
        when(rallyCommonService.getDataFromClient(any(ProjectConfFieldMapping.class), any(URL.class)))
            .thenReturn(mockSprintResponse);
    }

    @Test
    public void testGetSprints() throws IOException {
        // Mock REST response
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockSprintResponse, HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(), 
                eq(HttpMethod.GET), 
                any(HttpEntity.class), 
                eq(String.class),
                any(Object[].class)))
            .thenReturn(responseEntity);

        // Execute the method
        List<SprintDetails> result = fetchSprintReport.getSprints(projectConfig, boardId);

        // Verify results
        assertNotNull(result);
        assertEquals(1, result.size());
        
        SprintDetails sprintDetails = result.get(0);
        assertEquals(sprintName, sprintDetails.getSprintName());
        assertEquals(sprintState, sprintDetails.getState());
        assertEquals(sprintId, sprintDetails.getOriginalSprintId());
        assertEquals(sprintId + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR + basicConfig.getProjectNodeId(), 
                sprintDetails.getSprintID());
        assertTrue(sprintDetails.getOriginBoardId().contains(boardId));
    }

    private String createMockSprintResponse() {
        JSONObject sprint = new JSONObject();
        sprint.put("id", sprintId);
        sprint.put("name", sprintName);
        sprint.put("state", sprintState);
        sprint.put("startDate", "2023-01-01T00:00:00.000Z");
        sprint.put("endDate", "2023-01-15T00:00:00.000Z");
        sprint.put("completeDate", "2023-01-15T00:00:00.000Z");
        sprint.put("activatedDate", "2023-01-01T00:00:00.000Z");
        sprint.put("goal", "Test Sprint Goal");

        JSONArray values = new JSONArray();
        values.add(sprint);

        JSONObject response = new JSONObject();
        response.put("values", values);
        response.put("isLast", true);

        return response.toJSONString();
    }
}
