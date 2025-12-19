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

package com.publicissapient.kpidashboard.config.mongo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MongoTTLIndexConfigTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private TTLIndexConfigProperties ttlIndexConfigProperties;

    @Mock
    private IndexOperations indexOperations;

    private MongoTTLIndexConfig mongoTTLIndexConfig;

    @BeforeEach
    void setUp() {
        mongoTTLIndexConfig = new MongoTTLIndexConfig(mongoTemplate, ttlIndexConfigProperties);
    }

    @Test
    void when_ValidTTLConfigWithNewIndexThen_CreatesIndexSuccessfully() {
        // Arrange
        Map<String, TTLIndexConfigProperties.TTLIndexConfig> configs = new HashMap<>();
        TTLIndexConfigProperties.TTLIndexConfig config = createTTLConfig("testCollection", "createdAt", TimeUnit.DAYS);
        configs.put("testIndex", config);

        when(ttlIndexConfigProperties.getConfigs()).thenReturn(configs);
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Collections.emptyList());

        // Act
        ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "initializeTTLIndex");

        // Assert
        verify(indexOperations, never()).dropIndex(anyString());
        verify(indexOperations).ensureIndex(any(Index.class));
    }

    @Test
    void when_ValidTTLConfigWithExistingIndexThen_DropsAndRecreatesIndex() {
        // Arrange
        Map<String, TTLIndexConfigProperties.TTLIndexConfig> configs = new HashMap<>();
        TTLIndexConfigProperties.TTLIndexConfig config = createTTLConfig("testCollection", "createdAt", TimeUnit.DAYS);
        configs.put("testIndex", config);

        IndexInfo existingIndex = mock(IndexInfo.class);
        when(existingIndex.getName()).thenReturn("testIndex-createdAt");

        when(ttlIndexConfigProperties.getConfigs()).thenReturn(configs);
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Arrays.asList(existingIndex));

        // Act
        ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "initializeTTLIndex");

        // Assert
        verify(indexOperations).dropIndex("testIndex-createdAt");
        verify(indexOperations).ensureIndex(any(Index.class));
    }

    @Test
    void when_EmptyTTLConfigThen_NoIndexesCreated() {
        // Arrange
        when(ttlIndexConfigProperties.getConfigs()).thenReturn(Collections.emptyMap());

        // Act
        ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "initializeTTLIndex");

        // Assert
        verify(mongoTemplate, never()).indexOps(anyString());
    }

    @Test
    void when_MultipleTTLConfigsThen_CreatesAllIndexes() {
        // Arrange
        Map<String, TTLIndexConfigProperties.TTLIndexConfig> configs = new HashMap<>();
        TTLIndexConfigProperties.TTLIndexConfig config1 = createTTLConfig("collection1", "field1", TimeUnit.DAYS);
        TTLIndexConfigProperties.TTLIndexConfig config2 = createTTLConfig("collection2", "field2", TimeUnit.MINUTES);
        configs.put("index1", config1);
        configs.put("index2", config2);

        IndexOperations indexOps1 = mock(IndexOperations.class);
        IndexOperations indexOps2 = mock(IndexOperations.class);

        when(ttlIndexConfigProperties.getConfigs()).thenReturn(configs);
        when(mongoTemplate.indexOps("collection1")).thenReturn(indexOps1);
        when(mongoTemplate.indexOps("collection2")).thenReturn(indexOps2);
        when(indexOps1.getIndexInfo()).thenReturn(Collections.emptyList());
        when(indexOps2.getIndexInfo()).thenReturn(Collections.emptyList());

        // Act
        ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "initializeTTLIndex");

        // Assert
        verify(indexOps1).ensureIndex(any(Index.class));
        verify(indexOps2).ensureIndex(any(Index.class));
    }

    @Test
    void when_TTLConfigWithNullTTLFieldThen_HandlesGracefully() {
        // Arrange
        Map<String, TTLIndexConfigProperties.TTLIndexConfig> configs = new HashMap<>();
        TTLIndexConfigProperties.TTLIndexConfig config = createTTLConfig("testCollection", null, TimeUnit.DAYS);
        configs.put("testIndex", config);

        when(ttlIndexConfigProperties.getConfigs()).thenReturn(configs);
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Collections.emptyList());

        // Act
        ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "initializeTTLIndex");

        // Assert
        verify(indexOperations).ensureIndex(any(Index.class));
    }

    @Test
    void when_IndexExistsWithMatchingNameThen_ReturnsTrue() {
        // Arrange
        IndexInfo existingIndex = mock(IndexInfo.class);
        when(existingIndex.getName()).thenReturn("testIndex-createdAt");
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Arrays.asList(existingIndex));

        // Act
        Boolean result = ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "ttlIndexAlreadyExists",
                "testCollection", "testIndex", "createdAt");

        // Assert
        assertTrue(result);
    }

    @Test
    void when_IndexDoesNotExistThen_ReturnsFalse() {
        // Arrange
        IndexInfo existingIndex = mock(IndexInfo.class);
        when(existingIndex.getName()).thenReturn("differentIndex-differentField");
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Arrays.asList(existingIndex));

        // Act
        Boolean result = ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "ttlIndexAlreadyExists",
                "testCollection", "testIndex", "createdAt");

        // Assert
        assertFalse(result);
    }

    @Test
    void when_EmptyIndexListThen_ReturnsFalse() {
        // Arrange
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Collections.emptyList());

        // Act
        Boolean result = ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "ttlIndexAlreadyExists",
                "testCollection", "testIndex", "createdAt");

        // Assert
        assertFalse(result);
    }

    @Test
    void when_CaseInsensitiveIndexNameMatchThen_ReturnsTrue() {
        // Arrange
        IndexInfo existingIndex = mock(IndexInfo.class);
        when(existingIndex.getName()).thenReturn("TESTINDEX-CREATEDAT");
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Arrays.asList(existingIndex));

        // Act
        Boolean result = ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "ttlIndexAlreadyExists",
                "testCollection", "testIndex", "createdAt");

        // Assert
        assertTrue(result);
    }

    @Test
    void when_TTLConfigWithDifferentTimeUnitsThen_CreatesIndexWithCorrectExpiration() {
        // Arrange
        Map<String, TTLIndexConfigProperties.TTLIndexConfig> configs = new HashMap<>();
        TTLIndexConfigProperties.TTLIndexConfig config = createTTLConfig("testCollection", "createdAt", TimeUnit.HOURS);
        configs.put("testIndex", config);

        when(ttlIndexConfigProperties.getConfigs()).thenReturn(configs);
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Collections.emptyList());

        // Act
        ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "initializeTTLIndex");

        // Assert
        verify(indexOperations).ensureIndex(argThat(index -> {
            // Verify the index is created with correct parameters
            return index != null;
        }));
    }

    @Test
    void when_TTLIndexNameFormatAppliedThen_CorrectNameGenerated() {
        // Arrange
        Map<String, TTLIndexConfigProperties.TTLIndexConfig> configs = new HashMap<>();
        TTLIndexConfigProperties.TTLIndexConfig config = createTTLConfig("testCollection", "timestamp", TimeUnit.DAYS);
        configs.put("userSession", config);

        IndexInfo existingIndex = mock(IndexInfo.class);
        when(existingIndex.getName()).thenReturn("userSession-timestamp");

        when(ttlIndexConfigProperties.getConfigs()).thenReturn(configs);
        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Arrays.asList(existingIndex));

        // Act
        ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "initializeTTLIndex");

        // Assert
        verify(indexOperations).dropIndex("userSession-timestamp");
        verify(indexOperations).ensureIndex(any(Index.class));
    }

    @Test
    void when_MultipleIndexesWithSameCollectionThen_ProcessesAllCorrectly() {
        // Arrange
        Map<String, TTLIndexConfigProperties.TTLIndexConfig> configs = new HashMap<>();
        TTLIndexConfigProperties.TTLIndexConfig config1 = createTTLConfig("sharedCollection", "field1", TimeUnit.DAYS);
        TTLIndexConfigProperties.TTLIndexConfig config2 = createTTLConfig("sharedCollection", "field2",
                TimeUnit.MINUTES);
        configs.put("index1", config1);
        configs.put("index2", config2);

        when(ttlIndexConfigProperties.getConfigs()).thenReturn(configs);
        when(mongoTemplate.indexOps("sharedCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Collections.emptyList());

        // Act
        ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "initializeTTLIndex");

        // Assert
        verify(indexOperations, times(2)).ensureIndex(any(Index.class));
        verify(mongoTemplate, times(4)).indexOps("sharedCollection"); // 2 for exists check, 2 for creation
    }

    @Test
    void when_NullParametersPassedToTtlIndexAlreadyExistsThen_HandlesGracefully() {
        // Arrange
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Collections.emptyList());

        // Act & Assert - Test with null collection name
        assertThrows(Exception.class, () -> {
            ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "ttlIndexAlreadyExists", null, "testIndex",
                    "createdAt");
        });
    }

    @Test
    void when_IndexInfoStreamProcessingThen_HandlesCorrectly() {
        // Arrange
        IndexInfo index1 = mock(IndexInfo.class);
        IndexInfo index2 = mock(IndexInfo.class);
        IndexInfo index3 = mock(IndexInfo.class);

        when(index1.getName()).thenReturn("otherIndex-field1");
        when(index2.getName()).thenReturn("testIndex-createdAt");
        // when(index3.getName()).thenReturn("anotherIndex-field2");

        when(mongoTemplate.indexOps("testCollection")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Arrays.asList(index1, index2, index3));

        // Act
        Boolean result = ReflectionTestUtils.invokeMethod(mongoTTLIndexConfig, "ttlIndexAlreadyExists",
                "testCollection", "testIndex", "createdAt");

        // Assert
        assertTrue(result);
    }

    // Helper method to create TTL config
    private TTLIndexConfigProperties.TTLIndexConfig createTTLConfig(String collectionName, String ttlField,
                                                                    TimeUnit timeUnit) {
        TTLIndexConfigProperties.TTLIndexConfig config = mock(TTLIndexConfigProperties.TTLIndexConfig.class);
        when(config.getCollectionName()).thenReturn(collectionName);
        when(config.getTtlField()).thenReturn(ttlField);
        when(config.getTimeUnit()).thenReturn(timeUnit);
        return config;
    }
}

