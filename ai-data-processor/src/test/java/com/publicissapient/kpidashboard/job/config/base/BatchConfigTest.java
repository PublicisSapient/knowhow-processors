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

package com.publicissapient.kpidashboard.job.config.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchConfigTest {

    private BatchConfig batchConfig;

    @BeforeEach
    void setUp() {
        batchConfig = new BatchConfig();
    }

    @Test
    void when_ValidPositiveChunkSizeThen_NoValidationErrors() {
        // Arrange
        batchConfig.setChunkSize(100);

        // Act
        batchConfig.validateConfiguration();

        // Assert
        assertTrue(batchConfig.getConfigValidationErrors().isEmpty());
    }

    @Test
    void when_ZeroChunkSizeThen_ValidationErrorAdded() {
        // Arrange
        batchConfig.setChunkSize(0);

        // Act
        batchConfig.validateConfiguration();

        // Assert
        Set<String> errors = batchConfig.getConfigValidationErrors();
        assertFalse(errors.isEmpty());
        assertEquals(1, errors.size());
        assertTrue(errors.contains("The chunk size must be a positive integer. Received chunk size 0"));
    }

    @Test
    void when_NegativeChunkSizeThen_ValidationErrorAdded() {
        // Arrange
        batchConfig.setChunkSize(-5);

        // Act
        batchConfig.validateConfiguration();

        // Assert
        Set<String> errors = batchConfig.getConfigValidationErrors();
        assertFalse(errors.isEmpty());
        assertEquals(1, errors.size());
        assertTrue(errors.contains("The chunk size must be a positive integer. Received chunk size -5"));
    }

    @Test
    void when_ValidationErrorsExistThen_ReturnsUnmodifiableSet() {
        // Arrange
        batchConfig.setChunkSize(-1);
        batchConfig.validateConfiguration();

        // Act
        Set<String> errors = batchConfig.getConfigValidationErrors();

        // Assert
        assertThrows(UnsupportedOperationException.class, () -> {
            errors.add("Should not be able to modify");
        });
    }

    @Test
    void when_NoValidationErrorsThen_ReturnsEmptyUnmodifiableSet() {
        // Arrange
        batchConfig.setChunkSize(50);
        batchConfig.validateConfiguration();

        // Act
        Set<String> errors = batchConfig.getConfigValidationErrors();

        // Assert
        assertTrue(errors.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> {
            errors.add("Should not be able to modify");
        });
    }

    @Test
    void when_MultipleValidationCallsThen_ErrorsAccumulate() {
        // Arrange
        batchConfig.setChunkSize(-1);

        // Act
        batchConfig.validateConfiguration();
        batchConfig.setChunkSize(0);
        batchConfig.validateConfiguration();

        // Assert
        Set<String> errors = batchConfig.getConfigValidationErrors();
        assertEquals(2, errors.size());
        assertTrue(errors.contains("The chunk size must be a positive integer. Received chunk size -1"));
        assertTrue(errors.contains("The chunk size must be a positive integer. Received chunk size 0"));
    }

    @Test
    void when_ChunkSizeSetToOneThen_NoValidationErrors() {
        // Arrange
        batchConfig.setChunkSize(1);

        // Act
        batchConfig.validateConfiguration();

        // Assert
        assertTrue(batchConfig.getConfigValidationErrors().isEmpty());
    }

    @Test
    void when_LargeChunkSizeThen_NoValidationErrors() {
        // Arrange
        batchConfig.setChunkSize(Integer.MAX_VALUE);

        // Act
        batchConfig.validateConfiguration();

        // Assert
        assertTrue(batchConfig.getConfigValidationErrors().isEmpty());
    }

    @Test
    void when_DefaultChunkSizeThen_ValidationErrorAdded() {
        // Arrange - default chunkSize is 0

        // Act
        batchConfig.validateConfiguration();

        // Assert
        Set<String> errors = batchConfig.getConfigValidationErrors();
        assertFalse(errors.isEmpty());
        assertTrue(errors.contains("The chunk size must be a positive integer. Received chunk size 0"));
    }
}

