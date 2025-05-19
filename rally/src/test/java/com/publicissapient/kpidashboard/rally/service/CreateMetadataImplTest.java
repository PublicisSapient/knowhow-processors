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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
      MockitoAnnotations.openMocks(this);
      projectConfig = new ProjectConfFieldMapping();
      projectConfig.setBasicProjectConfigId(new ObjectId("64dc99c78d68d676870dfe89"));
      projectConfig.setProjectName("Test Project");

      var projectToolConfig = new ProjectToolConfig();
      projectToolConfig.setId(new ObjectId("64dc99c78d68d676870dfe88"));
      projectConfig.setProjectToolConfig(projectToolConfig);

      // Lenient stubbing
      lenient().when(boardMetadataRepository.findByProjectBasicConfigId(any())).thenReturn(null);
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
        String typesUrl = "http://mock-url/typedefinition";
        when(rallyRestClient.getBaseUrl()).thenReturn("http://mock-url");
        when(rallyRestClient.get(eq(typesUrl), eq(projectConfig), eq(RallyTypeDefinitionResponse.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        List<MetadataValue> result = createMetadataImpl.fetchTypeDefinitions(projectConfig);

        assertNotNull(result);
        verify(rallyRestClient, times(1)).get(eq(typesUrl), eq(projectConfig), eq(RallyTypeDefinitionResponse.class));
    }

    @Test
    void testFetchAllowedValues_Success() throws JsonProcessingException {
        String allowedValuesUrl = "http://mock-url/allowedAttributeValues?attributeName=State";
        when(rallyRestClient.getBaseUrl()).thenReturn("http://mock-url");
        when(rallyRestClient.get(eq(allowedValuesUrl), eq(projectConfig), eq(RallyAllowedValuesResponse.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        List<MetadataValue> result = createMetadataImpl.fetchAllowedValues(projectConfig, "State");

        assertNotNull(result);
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
}