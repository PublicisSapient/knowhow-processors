/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.client.customapi.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;

@ExtendWith(MockitoExtension.class)
class TrendValuesListDeserializerTest {

    @Mock
    private JsonParser jsonParser;

    @Mock
    private DeserializationContext deserializationContext;

    private TrendValuesListDeserializer deserializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        deserializer = new TrendValuesListDeserializer();
        objectMapper = new ObjectMapper();
    }

    @Test
    void when_EmptyListProvidedThen_ReturnsNewObject() throws IOException {
        // Arrange
        List<Object> emptyList = new ArrayList<>();
        when(jsonParser.readValueAs(List.class)).thenReturn(emptyList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertEquals(Object.class, result.getClass());
    }

    @Test
    void when_NullListProvidedThen_ReturnsNewObject() throws IOException {
        // Arrange
        when(jsonParser.readValueAs(List.class)).thenReturn(null);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertEquals(Object.class, result.getClass());
    }

    @Test
    void when_ListWithFilterKeyProvidedThen_ReturnsDataCountGroupList() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithFilter = new LinkedHashMap<>();
        mapWithFilter.put("filter", "testFilter");
        mapWithFilter.put("value", createNestedDataCountList());

        List<Object> inputList = Arrays.asList(mapWithFilter);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertTrue(resultList.get(0) instanceof DataCountGroup);
    }

    @Test
    void when_ListWithFilter1KeyProvidedThen_ReturnsDataCountGroupList() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithFilter1 = new LinkedHashMap<>();
        mapWithFilter1.put("filter1", "testFilter1");
        mapWithFilter1.put("value", createNestedDataCountList());

        List<Object> inputList = Arrays.asList(mapWithFilter1);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertTrue(resultList.get(0) instanceof DataCountGroup);
    }

    @Test
    void when_ListWithFilter2KeyProvidedThen_ReturnsDataCountGroupList() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithFilter2 = new LinkedHashMap<>();
        mapWithFilter2.put("filter2", "testFilter2");
        mapWithFilter2.put("value", createNestedDataCountList());

        List<Object> inputList = Arrays.asList(mapWithFilter2);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertTrue(resultList.get(0) instanceof DataCountGroup);
    }

    @Test
    void when_ListWithoutFilterKeysProvidedThen_ReturnsDataCountList() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithoutFilter = new LinkedHashMap<>();
        mapWithoutFilter.put("data", "testData");
        mapWithoutFilter.put("value", createNestedDataCountList());

        List<Object> inputList = Arrays.asList(mapWithoutFilter);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertTrue(resultList.get(0) instanceof DataCount);
    }

    @Test
    void when_DataCountGroupWithEmptyValueThen_ReturnsGroupWithEmptyValue() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithFilter = new LinkedHashMap<>();
        mapWithFilter.put("filter", "testFilter");
        mapWithFilter.put("value", new ArrayList<>());

        List<Object> inputList = Arrays.asList(mapWithFilter);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertTrue(resultList.get(0) instanceof DataCountGroup);
    }

    @Test
    void when_DataCountWithEmptyValueThen_ReturnsCountWithEmptyValue() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithoutFilter = new LinkedHashMap<>();
        mapWithoutFilter.put("data", "testData");
        mapWithoutFilter.put("value", new ArrayList<>());

        List<Object> inputList = Arrays.asList(mapWithoutFilter);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertTrue(resultList.get(0) instanceof DataCount);
    }

    @Test
    void when_DataCountGroupWithNullNestedValuesThen_HandlesGracefully() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithFilter = new LinkedHashMap<>();
        mapWithFilter.put("filter", "testFilter");
        mapWithFilter.put("value", null);

        List<Object> inputList = Arrays.asList(mapWithFilter);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertTrue(resultList.get(0) instanceof DataCountGroup);
    }

    @Test
    void when_InvalidJsonStructureThen_ThrowsException() throws IOException {
        // Arrange
        when(jsonParser.readValueAs(List.class)).thenThrow(new IOException("Invalid JSON"));

        // Act & Assert
        assertThrows(IOException.class, () -> {
            deserializer.deserialize(jsonParser, deserializationContext);
        });
    }

    @Test
    void when_LargeNestedStructureThen_ProcessesSuccessfully() throws IOException {
        // Arrange
        List<LinkedHashMap<String, Object>> nestedDataCounts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            LinkedHashMap<String, Object> nestedMap = new LinkedHashMap<>();
            nestedMap.put("data", "nested" + i);
            nestedMap.put("value", createDeeplyNestedStructure(3));
            nestedDataCounts.add(nestedMap);
        }

        LinkedHashMap<String, Object> mapWithFilter = new LinkedHashMap<>();
        mapWithFilter.put("filter", "testFilter");
        mapWithFilter.put("value", nestedDataCounts);

        List<Object> inputList = List.of(mapWithFilter);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertInstanceOf(List.class, result);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertInstanceOf(DataCountGroup.class, resultList.get(0));
    }

    @Test
    void when_MultipleFilterTypesInSameListThen_ProcessesAsDataCountGroup() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithFilter = new LinkedHashMap<>();
        mapWithFilter.put("filter", "testFilter");
        mapWithFilter.put("filter1", "testFilter1");
        mapWithFilter.put("value", createNestedDataCountList());

        List<Object> inputList = List.of(mapWithFilter);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertInstanceOf(List.class, result);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertInstanceOf(DataCountGroup.class, resultList.get(0));
    }

    @Test
    void when_DataCountWithNullValueThen_HandlesGracefully() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> mapWithoutFilter = new LinkedHashMap<>();
        mapWithoutFilter.put("data", "testData");
        mapWithoutFilter.put("value", null);

        List<Object> inputList = Arrays.asList(mapWithoutFilter);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertInstanceOf(List.class, result);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertInstanceOf(DataCount.class, resultList.get(0));
    }

    @Test
    void when_EmptyLinkedHashMapThen_ProcessesCorrectly() throws IOException {
        // Arrange
        LinkedHashMap<String, Object> emptyMap = new LinkedHashMap<>();
        List<Object> inputList = List.of(emptyMap);
        when(jsonParser.readValueAs(List.class)).thenReturn(inputList);

        // Act
        Object result = deserializer.deserialize(jsonParser, deserializationContext);

        // Assert
        assertNotNull(result);
        assertInstanceOf(List.class, result);
        List<?> resultList = (List<?>) result;
        assertFalse(resultList.isEmpty());
        assertInstanceOf(DataCount.class, resultList.get(0));
    }

    // Helper methods
    private List<LinkedHashMap<String, Object>> createNestedDataCountList() {
        List<LinkedHashMap<String, Object>> nestedList = new ArrayList<>();
        LinkedHashMap<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("data", "nestedData");
        nestedMap.put("value", createSimpleDataCountList());
        nestedList.add(nestedMap);
        return nestedList;
    }

    private List<LinkedHashMap<String, Object>> createSimpleDataCountList() {
        List<LinkedHashMap<String, Object>> simpleList = new ArrayList<>();
        LinkedHashMap<String, Object> simpleMap = new LinkedHashMap<>();
        simpleMap.put("data", "simpleData");
        simpleMap.put("count", 10);
        simpleList.add(simpleMap);
        return simpleList;
    }

    private List<LinkedHashMap<String, Object>> createDeeplyNestedStructure(int depth) {
        if (depth <= 0) {
            return createSimpleDataCountList();
        }

        List<LinkedHashMap<String, Object>> deepList = new ArrayList<>();
        LinkedHashMap<String, Object> deepMap = new LinkedHashMap<>();
        deepMap.put("data", "deep" + depth);
        deepMap.put("value", createDeeplyNestedStructure(depth - 1));
        deepList.add(deepMap);
        return deepList;
    }
}

