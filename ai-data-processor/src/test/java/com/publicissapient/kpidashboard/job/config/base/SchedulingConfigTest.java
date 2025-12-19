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

class SchedulingConfigTest {

    private SchedulingConfig schedulingConfig;

    @BeforeEach
    void setUp() {
        schedulingConfig = new SchedulingConfig();
    }

    @Test
    void when_ValidCronExpressionThen_NoValidationErrors() {
        // Arrange
        schedulingConfig.setCron("0 0 12 * * ?");

        // Act
        schedulingConfig.validateConfiguration();

        // Assert
        assertTrue(schedulingConfig.getConfigValidationErrors().isEmpty());
    }

    @Test
    void when_EmptyCronExpressionThen_ValidationErrorAdded() {
        // Arrange
        schedulingConfig.setCron("");

        // Act
        schedulingConfig.validateConfiguration();

        // Assert
        Set<String> errors = schedulingConfig.getConfigValidationErrors();
        assertFalse(errors.isEmpty());
        assertEquals(1, errors.size());
        assertTrue(errors.contains("The cron expression must not be empty"));
    }

    @Test
    void when_NullCronExpressionThen_ValidationErrorAdded() {
        // Arrange
        schedulingConfig.setCron(null);

        // Act
        schedulingConfig.validateConfiguration();

        // Assert
        Set<String> errors = schedulingConfig.getConfigValidationErrors();
        assertFalse(errors.isEmpty());
        assertEquals(1, errors.size());
        assertTrue(errors.contains("The cron expression must not be empty"));
    }

    @Test
    void when_ValidationErrorsExistThen_ReturnsUnmodifiableSet() {
        // Arrange
        schedulingConfig.setCron("");
        schedulingConfig.validateConfiguration();

        // Act
        Set<String> errors = schedulingConfig.getConfigValidationErrors();

        // Assert
        assertThrows(UnsupportedOperationException.class, () -> {
            errors.add("Should not be able to modify");
        });
    }

    @Test
    void when_NoValidationErrorsThen_ReturnsEmptyUnmodifiableSet() {
        // Arrange
        schedulingConfig.setCron("0 */5 * * * ?");
        schedulingConfig.validateConfiguration();

        // Act
        Set<String> errors = schedulingConfig.getConfigValidationErrors();

        // Assert
        assertTrue(errors.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> {
            errors.add("Should not be able to modify");
        });
    }

    @Test
    void when_ValidComplexCronExpressionThen_NoValidationErrors() {
        // Arrange
        schedulingConfig.setCron("0 15 10 ? * MON-FRI");

        // Act
        schedulingConfig.validateConfiguration();

        // Assert
        assertTrue(schedulingConfig.getConfigValidationErrors().isEmpty());
    }

    @Test
    void when_SingleCharacterCronThen_NoValidationErrors() {
        // Arrange
        schedulingConfig.setCron("*");

        // Act
        schedulingConfig.validateConfiguration();

        // Assert
        assertTrue(schedulingConfig.getConfigValidationErrors().isEmpty());
    }

    @Test
    void when_DefaultCronValueThen_ValidationErrorAdded() {
        // Arrange - default cron is null

        // Act
        schedulingConfig.validateConfiguration();

        // Assert
        Set<String> errors = schedulingConfig.getConfigValidationErrors();
        assertFalse(errors.isEmpty());
        assertTrue(errors.contains("The cron expression must not be empty"));
    }
}

