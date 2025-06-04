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

package com.publicissapient.kpidashboard.rally.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectToolConfigRepository;
import com.publicissapient.kpidashboard.common.repository.connection.ConnectionRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;

@ExtendWith(MockitoExtension.class)
class FetchProjectConfigurationImplTest {

    @Mock
    private FieldMappingRepository fieldMappingRepository;

    @Mock
    private ProjectToolConfigRepository toolRepository;

    @Mock
    private ProjectBasicConfigRepository projectConfigRepository;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private SprintRepository sprintRepository;

    @InjectMocks
    private FetchProjectConfigurationImpl fetchProjectConfiguration;

    private ProjectBasicConfig projectBasicConfig;
    private ProjectToolConfig projectToolConfig;
    private FieldMapping fieldMapping;
    private Connection connection;
    private SprintDetails sprintDetails;
    private ObjectId projectId;
    private ObjectId connectionId;

    @BeforeEach
    void setUp() {
        projectId = new ObjectId();
        connectionId = new ObjectId();

        // Initialize ProjectBasicConfig
        projectBasicConfig = new ProjectBasicConfig();
        projectBasicConfig.setId(projectId);
        projectBasicConfig.setProjectName("Test Project");
        projectBasicConfig.setIsKanban(false);

        // Initialize ProjectToolConfig
        projectToolConfig = new ProjectToolConfig();
        projectToolConfig.setBasicProjectConfigId(projectId);
        projectToolConfig.setToolName(RallyConstants.RALLY);
        projectToolConfig.setConnectionId(connectionId);

        // Initialize FieldMapping
        fieldMapping = new FieldMapping();
        fieldMapping.setBasicProjectConfigId(projectId);

        // Initialize Connection
        connection = new Connection();
        connection.setId(connectionId);
        connection.setBaseUrl("https://rally.example.com");

        // Initialize SprintDetails
        sprintDetails = new SprintDetails();
        sprintDetails.setSprintID("SPRINT-1");
        sprintDetails.setBasicProjectConfigId(projectId);
    }

    @Test
    void testFetchBasicProjConfId() {
        // Mock repository calls
        when(projectConfigRepository.findByKanbanAndProjectOnHold(false, false))
            .thenReturn(Arrays.asList(projectBasicConfig));
        when(toolRepository.findByToolNameAndQueryEnabledAndBasicProjectConfigIdIn(anyString(), anyBoolean(), any()))
            .thenReturn(Arrays.asList(projectToolConfig));

        // Call the method
        List<String> result = fetchProjectConfiguration.fetchBasicProjConfId(RallyConstants.RALLY, true, false);

        // Verify results
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(projectId.toString(), result.get(0));
    }

    @Test
    void testFetchConfigurationBasedOnSprintId() {
        // Mock repository calls
        when(sprintRepository.findBySprintID("SPRINT-1")).thenReturn(sprintDetails);
        when(projectConfigRepository.findById(projectId)).thenReturn(Optional.of(projectBasicConfig));
        when(fieldMappingRepository.findByBasicProjectConfigId(projectId)).thenReturn(fieldMapping);
        when(toolRepository.findByBasicProjectConfigId(projectId)).thenReturn(Arrays.asList(projectToolConfig));
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        // Call the method
        ProjectConfFieldMapping result = fetchProjectConfiguration.fetchConfigurationBasedOnSprintId("SPRINT-1");

        // Verify results
        assertNotNull(result);
        assertEquals(projectId, result.getBasicProjectConfigId());
        assertEquals("Test Project", result.getProjectName());
        assertEquals(false, result.isKanban());
        assertNotNull(result.getProjectBasicConfig());
        assertNotNull(result.getProjectToolConfig());
        assertNotNull(result.getFieldMapping());
    }

    @Test
    void testFetchConfiguration() {
        // Mock repository calls
        when(projectConfigRepository.findById(projectId)).thenReturn(Optional.of(projectBasicConfig));
        when(fieldMappingRepository.findByBasicProjectConfigId(projectId)).thenReturn(fieldMapping);
        when(toolRepository.findByToolNameAndBasicProjectConfigId(RallyConstants.RALLY, projectId))
            .thenReturn(Arrays.asList(projectToolConfig));
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        // Call the method
        ProjectConfFieldMapping result = fetchProjectConfiguration.fetchConfiguration(projectId.toString());

        // Verify results
        assertNotNull(result);
        assertEquals(projectId, result.getBasicProjectConfigId());
        assertEquals("Test Project", result.getProjectName());
        assertEquals(false, result.isKanban());
        assertNotNull(result.getProjectBasicConfig());
        assertNotNull(result.getProjectToolConfig());
        assertNotNull(result.getFieldMapping());
    }

    @Test
    void testFetchConfigurationWithNoToolConfigs() {
        // Mock repository calls with no tool configs
        when(projectConfigRepository.findById(projectId)).thenReturn(Optional.of(projectBasicConfig));
        when(fieldMappingRepository.findByBasicProjectConfigId(projectId)).thenReturn(fieldMapping);
        when(toolRepository.findByToolNameAndBasicProjectConfigId(RallyConstants.RALLY, projectId))
            .thenReturn(Collections.emptyList());

        // Call the method
        ProjectConfFieldMapping result = fetchProjectConfiguration.fetchConfiguration(projectId.toString());

        // Verify results
        assertNull(result);
    }

    @Test
    void testFetchConfigurationBasedOnSprintIdWithNoConnection() {
        // Mock repository calls but return no connection
        when(sprintRepository.findBySprintID("SPRINT-1")).thenReturn(sprintDetails);
        when(projectConfigRepository.findById(projectId)).thenReturn(Optional.of(projectBasicConfig));
        when(fieldMappingRepository.findByBasicProjectConfigId(projectId)).thenReturn(fieldMapping);
        when(toolRepository.findByBasicProjectConfigId(projectId)).thenReturn(Arrays.asList(projectToolConfig));
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        // Call the method
        ProjectConfFieldMapping result = fetchProjectConfiguration.fetchConfigurationBasedOnSprintId("SPRINT-1");

        // Verify results
        assertNotNull(result);
        assertEquals(projectId, result.getBasicProjectConfigId());
        assertNotNull(result.getJira()); // RallyToolConfig should still be created but without connection
    }
}
