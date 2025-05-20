/*
 *
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.publicissapient.kpidashboard.rally.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.jira.BoardMetadata;
import com.publicissapient.kpidashboard.common.model.jira.Metadata;
import com.publicissapient.kpidashboard.common.model.jira.MetadataValue;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.jira.BoardMetadataRepository;
import com.publicissapient.kpidashboard.rally.cache.RallyProcessorCacheEvictor;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.RallyAllowedValuesResponse;
import com.publicissapient.kpidashboard.rally.model.RallyTypeDefinitionResponse;
import com.publicissapient.kpidashboard.rally.util.RallyRestClient;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateMetadataImplTest {

    @InjectMocks
    private CreateMetadataImpl createMetadataImpl;

    @Mock
    private BoardMetadataRepository boardMetadataRepository;

    @Mock
    private FieldMappingRepository fieldMappingRepository;

    @Mock
    private RallyProcessorCacheEvictor rallyProcessorCacheEvictor;

    @Mock
    private ProcessorToolConnectionService processorToolConnectionService;

    @Mock
    private RallyRestClient rallyRestClient;

    private ProjectConfFieldMapping projectConfig;

    @BeforeEach
    void setUp() {
        projectConfig = new ProjectConfFieldMapping();
        projectConfig.setBasicProjectConfigId(new ObjectId("64dc99c78d68d676870dfe89"));
        projectConfig.setProjectName("Test Project");

        ProjectToolConfig projectToolConfig = new ProjectToolConfig();
        projectToolConfig.setId(new ObjectId("64dc99c78d68d676870dfe88"));
        projectToolConfig.setOriginalTemplateCode("RALLY");
        projectConfig.setProjectToolConfig(projectToolConfig);

        // Lenient stubbing
        lenient().when(boardMetadataRepository.findByProjectBasicConfigId(any())).thenReturn(null);
        lenient().when(rallyRestClient.getBaseUrl()).thenReturn("http://mock-url");
    }

    @Test
    void testCollectMetadata_WhenMetadataNotPresent() {
        // Execute the method
        createMetadataImpl.collectMetadata(projectConfig, "false");

        // Verify interactions
        verify(boardMetadataRepository, times(1)).deleteByProjectBasicConfigId(projectConfig.getBasicProjectConfigId());
        verify(boardMetadataRepository, times(1)).save(any(BoardMetadata.class));
        verify(fieldMappingRepository, times(1)).save(any(FieldMapping.class));
        verify(rallyProcessorCacheEvictor, times(5)).evictCache(anyString(), anyString());
    }

    @Test
    void testCollectMetadata_WhenMetadataAlreadyPresent() {
        when(boardMetadataRepository.findByProjectBasicConfigId(projectConfig.getBasicProjectConfigId())).thenReturn(new BoardMetadata());

        createMetadataImpl.collectMetadata(projectConfig, "true");

        verify(boardMetadataRepository, never()).deleteByProjectBasicConfigId(any());
        verify(boardMetadataRepository, never()).save(any(BoardMetadata.class));
        verify(fieldMappingRepository, never()).save(any(FieldMapping.class));
        verify(rallyProcessorCacheEvictor, never()).evictCache(anyString(), anyString());
    }

    @Test
    void testFetchTypeDefinitions_Success() throws JsonProcessingException {
        // Setup
        String typesUrl = "http://mock-url/typedefinition";
        RallyTypeDefinitionResponse response = new RallyTypeDefinitionResponse();
        RallyTypeDefinitionResponse.QueryResult queryResult = new RallyTypeDefinitionResponse.QueryResult();

        RallyTypeDefinitionResponse.TypeDefinition type1 = new RallyTypeDefinitionResponse.TypeDefinition();
        type1.setRefObjectName("HierarchicalRequirement");
        RallyTypeDefinitionResponse.TypeDefinition type2 = new RallyTypeDefinitionResponse.TypeDefinition();
        type2.setRefObjectName("Defect");
        RallyTypeDefinitionResponse.TypeDefinition type3 = new RallyTypeDefinitionResponse.TypeDefinition();
        type3.setRefObjectName("Task");
        RallyTypeDefinitionResponse.TypeDefinition type4 = new RallyTypeDefinitionResponse.TypeDefinition();
        type4.setRefObjectName("TestCase");
        RallyTypeDefinitionResponse.TypeDefinition type5 = new RallyTypeDefinitionResponse.TypeDefinition();
        type5.setRefObjectName("DefectSuite");
        RallyTypeDefinitionResponse.TypeDefinition type6 = new RallyTypeDefinitionResponse.TypeDefinition();
        type6.setRefObjectName("Feature");

        queryResult.setResults(Arrays.asList(type1, type2, type3, type4, type5, type6));
        queryResult.setErrors(Collections.emptyList());
        response.setQueryResult(queryResult);

        ResponseEntity<RallyTypeDefinitionResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);
        when(rallyRestClient.get(eq(typesUrl), eq(projectConfig), eq(RallyTypeDefinitionResponse.class)))
                .thenReturn(responseEntity);

        // Execute
        List<MetadataValue> result = createMetadataImpl.fetchTypeDefinitions(projectConfig);

        // Verify
        assertNotNull(result);
        assertEquals(6, result.size());
        assertEquals("HierarchicalRequirement", result.get(0).getKey());
        assertEquals("Defect", result.get(1).getKey());
        verify(rallyRestClient, times(1)).get(eq(typesUrl), eq(projectConfig), eq(RallyTypeDefinitionResponse.class));
    }

    @Test
    void testFetchAllowedValues_Success() throws JsonProcessingException {
        // Setup
        String allowedValuesUrl = "http://mock-url/allowedAttributeValues?attributeName=State";
        RallyAllowedValuesResponse response = new RallyAllowedValuesResponse();
        RallyAllowedValuesResponse.QueryResult queryResult = new RallyAllowedValuesResponse.QueryResult();

        // Create all 9 default state values to match the implementation
        RallyAllowedValuesResponse.AllowedValue value1 = new RallyAllowedValuesResponse.AllowedValue();
        value1.setDisplayName("Defined");
        value1.setStringValue("Defined");
        RallyAllowedValuesResponse.AllowedValue value2 = new RallyAllowedValuesResponse.AllowedValue();
        value2.setDisplayName("In-Progress");
        value2.setStringValue("In Progress");
        RallyAllowedValuesResponse.AllowedValue value3 = new RallyAllowedValuesResponse.AllowedValue();
        value3.setDisplayName("Completed");
        value3.setStringValue("Completed");
        RallyAllowedValuesResponse.AllowedValue value4 = new RallyAllowedValuesResponse.AllowedValue();
        value4.setDisplayName("Accepted");
        value4.setStringValue("Accepted");
        RallyAllowedValuesResponse.AllowedValue value5 = new RallyAllowedValuesResponse.AllowedValue();
        value5.setDisplayName("Backlog");
        value5.setStringValue("Backlog");
        RallyAllowedValuesResponse.AllowedValue value6 = new RallyAllowedValuesResponse.AllowedValue();
        value6.setDisplayName("Ready");
        value6.setStringValue("Ready");
        RallyAllowedValuesResponse.AllowedValue value7 = new RallyAllowedValuesResponse.AllowedValue();
        value7.setDisplayName("InDevelopment");
        value7.setStringValue("In Development");
        RallyAllowedValuesResponse.AllowedValue value8 = new RallyAllowedValuesResponse.AllowedValue();
        value8.setDisplayName("Testing");
        value8.setStringValue("Testing");
        RallyAllowedValuesResponse.AllowedValue value9 = new RallyAllowedValuesResponse.AllowedValue();
        value9.setDisplayName("Done");
        value9.setStringValue("Done");

        queryResult.setResults(Arrays.asList(value1, value2, value3, value4, value5, value6, value7, value8, value9));
        queryResult.setErrors(Collections.emptyList());
        response.setQueryResult(queryResult);

        ResponseEntity<RallyAllowedValuesResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);
        when(rallyRestClient.get(eq(allowedValuesUrl), eq(projectConfig), eq(RallyAllowedValuesResponse.class)))
                .thenReturn(responseEntity);

        // Execute
        List<MetadataValue> result = createMetadataImpl.fetchAllowedValues(projectConfig, "State");

        // Verify
        assertNotNull(result);
        assertEquals(9, result.size());
        assertEquals("Defined", result.get(0).getKey());
        verify(rallyRestClient, times(1)).get(eq(allowedValuesUrl), eq(projectConfig), eq(RallyAllowedValuesResponse.class));
    }

    @Test
    void testEvictCaches() {
        createMetadataImpl.evictCaches();

        verify(rallyProcessorCacheEvictor, times(5)).evictCache(eq(CommonConstant.CACHE_CLEAR_ENDPOINT), anyString());
    }



    @Test
    void testGetMetadataValues_WithValidQueryResult() {
        // Mock a valid QueryResult
        RallyTypeDefinitionResponse.QueryResult queryResult = new RallyTypeDefinitionResponse.QueryResult();
        RallyTypeDefinitionResponse.TypeDefinition result1 = new RallyTypeDefinitionResponse.TypeDefinition();
        result1.setRefObjectName("HierarchicalRequirement");
        RallyTypeDefinitionResponse.TypeDefinition result2 = new RallyTypeDefinitionResponse.TypeDefinition();
        result2.setRefObjectName("Defect");
        RallyTypeDefinitionResponse.TypeDefinition result3 = new RallyTypeDefinitionResponse.TypeDefinition();
        result3.setRefObjectName("InvalidType");

        queryResult.setResults(Arrays.asList(result1, result2, result3));
        queryResult.setErrors(Collections.emptyList());
        queryResult.setWarnings(Collections.emptyList());

        // Call the method
        List<MetadataValue> metadataValues = createMetadataImpl.getMetadataValues(queryResult);

        // Verify the results
        assertNotNull(metadataValues);
        assertEquals(2, metadataValues.size());
        assertEquals("HierarchicalRequirement", metadataValues.get(0).getKey());
        assertEquals("HierarchicalRequirement", metadataValues.get(0).getData());
        assertEquals("Defect", metadataValues.get(1).getKey());
        assertEquals("Defect", metadataValues.get(1).getData());
    }
    @Test
    void testGetMetadataValues_WithValidAllowedValues() {
        // Mock a valid QueryResult
        RallyAllowedValuesResponse.QueryResult queryResult = new RallyAllowedValuesResponse.QueryResult();
        RallyAllowedValuesResponse.AllowedValue result1 = new RallyAllowedValuesResponse.AllowedValue();
        result1.setDisplayName("Defined");
        result1.setStringValue("Defined");
        RallyAllowedValuesResponse.AllowedValue result2 = new RallyAllowedValuesResponse.AllowedValue();
        result2.setDisplayName("In Progress");
        result2.setStringValue("In-Progress");

        queryResult.setResults(Arrays.asList(result1, result2));
        queryResult.setErrors(Collections.emptyList());
        queryResult.setWarnings(Collections.emptyList());

        // Call the method
        List<MetadataValue> metadataValues = createMetadataImpl.getMetadataValues(queryResult);

        // Verify the results
        assertNotNull(metadataValues);
        assertEquals(2, metadataValues.size());
        assertEquals("Defined", metadataValues.get(0).getKey());
        assertEquals("Defined", metadataValues.get(0).getData());
        assertEquals("In Progress", metadataValues.get(1).getKey());
        assertEquals("In-Progress", metadataValues.get(1).getData());
    }

    @Test
    void testGetMetadataValues_WithErrors() {
        // Mock a QueryResult with errors
        RallyAllowedValuesResponse.QueryResult queryResult = new RallyAllowedValuesResponse.QueryResult();
        queryResult.setErrors(Arrays.asList("Error 1", "Error 2"));
        queryResult.setWarnings(Collections.emptyList());
        queryResult.setResults(Collections.emptyList());

        // Call the method
        List<MetadataValue> metadataValues = createMetadataImpl.getMetadataValues(queryResult);

        // Verify the results
        assertNotNull(metadataValues);
        assertEquals(9, metadataValues.size()); // Default state values contain 9 entries
        assertEquals("Defined", metadataValues.get(0).getKey());
        assertEquals("Defined", metadataValues.get(0).getData());
        assertEquals("In-Progress", metadataValues.get(1).getKey());
        assertEquals("In Progress", metadataValues.get(1).getData());
        assertEquals("Done", metadataValues.get(8).getKey());
        assertEquals("Done", metadataValues.get(8).getData());
    }

    @Test
    void testGetMetadataValues_WithWarnings() {
        // Mock a QueryResult with warnings
        RallyAllowedValuesResponse.QueryResult queryResult = new RallyAllowedValuesResponse.QueryResult();
        queryResult.setWarnings(Arrays.asList("Warning 1"));
        queryResult.setErrors(Collections.emptyList());
        queryResult.setResults(Collections.emptyList());

        // Call the method
        List<MetadataValue> metadataValues = createMetadataImpl.getMetadataValues(queryResult);

        // Verify the results
        assertNotNull(metadataValues);
        assertTrue(metadataValues.isEmpty());
    }
    
    @Test
    void testFetchTypeDefinitions_Exception() throws JsonProcessingException {
        // Setup
        String typesUrl = "http://mock-url/typedefinition";
        when(rallyRestClient.get(eq(typesUrl), eq(projectConfig), eq(RallyTypeDefinitionResponse.class)))
                .thenThrow(new JsonProcessingException("Error parsing JSON") {});

        // Execute
        List<MetadataValue> result = createMetadataImpl.fetchTypeDefinitions(projectConfig);

        // Verify
        assertNotNull(result);
        assertEquals(6, result.size()); // Default type definitions
        assertEquals("User Story", result.get(0).getData());
        assertEquals("Defect", result.get(1).getData());
    }

    @Test
    void testFetchAllowedValues_Exception() throws JsonProcessingException {
        // Setup
        String allowedValuesUrl = "http://mock-url/allowedAttributeValues?attributeName=State";
        when(rallyRestClient.get(eq(allowedValuesUrl), eq(projectConfig), eq(RallyAllowedValuesResponse.class)))
                .thenThrow(new JsonProcessingException("Error parsing JSON") {});

        // Execute
        List<MetadataValue> result = createMetadataImpl.fetchAllowedValues(projectConfig, "State");

        // Verify
        assertNotNull(result);
        assertEquals(9, result.size()); // Default state values
        assertEquals("Defined", result.get(0).getKey());
    }

    @Test
    void testFetchTypeDefinitions_NullResponse() throws JsonProcessingException {
        // Setup
        String typesUrl = "http://mock-url/typedefinition";
        when(rallyRestClient.getBaseUrl()).thenReturn("http://mock-url");
        when(rallyRestClient.get(eq(typesUrl), eq(projectConfig), eq(RallyTypeDefinitionResponse.class)))
                .thenReturn(null);

        // Execute
        List<MetadataValue> result = createMetadataImpl.fetchTypeDefinitions(projectConfig);

        // Verify
        assertNotNull(result);
        // The implementation should return default type definitions when response is null
        assertEquals(6, result.size());
    }

    @Test
    void testFetchAllowedValues_NullResponse() throws JsonProcessingException {
        // Setup
        String allowedValuesUrl = "http://mock-url/allowedAttributeValues?attributeName=State";
        when(rallyRestClient.get(eq(allowedValuesUrl), eq(projectConfig), eq(RallyAllowedValuesResponse.class)))
                .thenReturn(null);

        // Execute
        List<MetadataValue> result = createMetadataImpl.fetchAllowedValues(projectConfig, "State");

        // Verify
        assertNotNull(result);
        assertEquals(9, result.size()); // Default state values
    }

    @Test
    void testCreateBoardMetadata() {
        // Setup - Create a project config with field mapping
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setBasicProjectConfigId(projectConfig.getBasicProjectConfigId());
        projectConfig.setFieldMapping(fieldMapping);

        // Execute
        createMetadataImpl.collectMetadata(projectConfig, "false");

        // Verify
        ArgumentCaptor<BoardMetadata> boardMetadataCaptor = ArgumentCaptor.forClass(BoardMetadata.class);
        verify(boardMetadataRepository).save(boardMetadataCaptor.capture());
        
        BoardMetadata savedMetadata = boardMetadataCaptor.getValue();
        assertNotNull(savedMetadata);
        assertEquals(projectConfig.getBasicProjectConfigId(), savedMetadata.getProjectBasicConfigId());
        assertEquals(projectConfig.getProjectToolConfig().getId(), savedMetadata.getProjectToolConfigId());
        assertEquals(projectConfig.getProjectToolConfig().getOriginalTemplateCode(), savedMetadata.getMetadataTemplateCode());
        
        // Verify metadata structure
        List<Metadata> metadataList = savedMetadata.getMetadata();
        assertNotNull(metadataList);
        assertEquals(3, metadataList.size());
        
        // Verify metadata types
        boolean hasIssueType = false;
        boolean hasStatus = false;
        boolean hasWorkflow = false;
        
        for (Metadata metadata : metadataList) {
            switch (metadata.getType()) {
                case "Issue_Type":
                    hasIssueType = true;
                    assertNotNull(metadata.getValue());
                    break;
                case "status":
                    hasStatus = true;
                    assertNotNull(metadata.getValue());
                    break;
                case "workflow":
                    hasWorkflow = true;
                    assertNotNull(metadata.getValue());
                    break;
            }
        }
        
        assertTrue(hasIssueType, "Issue_Type metadata should be present");
        assertTrue(hasStatus, "Status metadata should be present");
        assertTrue(hasWorkflow, "Workflow metadata should be present");
    }

    @Test
    void testMapFieldMapping() {
        // Execute
        createMetadataImpl.collectMetadata(projectConfig, "false");

        // Verify
        ArgumentCaptor<FieldMapping> fieldMappingCaptor = ArgumentCaptor.forClass(FieldMapping.class);
        verify(fieldMappingRepository).save(fieldMappingCaptor.capture());
        
        FieldMapping savedFieldMapping = fieldMappingCaptor.getValue();
        assertNotNull(savedFieldMapping);
        assertEquals(projectConfig.getBasicProjectConfigId(), savedFieldMapping.getBasicProjectConfigId());
        assertEquals(projectConfig.getProjectToolConfig().getId(), savedFieldMapping.getProjectToolConfigId());
        
        // Verify field mapping values
        assertEquals("CustomField", savedFieldMapping.getRootCauseIdentifier());
        assertTrue(savedFieldMapping.getJiradefecttype().contains("Defect"));
        assertArrayEquals(new String[]{"HierarchicalRequirement", "Defect", "Task"}, savedFieldMapping.getJiraIssueTypeNames());
        assertEquals("Defined", savedFieldMapping.getStoryFirstStatus());
        
        // Verify workflow mappings
        assertTrue(savedFieldMapping.getJiraStatusForDevelopmentKPI82().contains("InDevelopment"));
        assertTrue(savedFieldMapping.getJiraStatusForDevelopmentKPI82().contains("In Development"));
        assertTrue(savedFieldMapping.getJiraStatusForQaKPI82().contains("Testing"));
        assertTrue(savedFieldMapping.getJiraDodKPI14().contains("Done"));
        assertTrue(savedFieldMapping.getJiraDodKPI14().contains("Accepted"));
    }
}