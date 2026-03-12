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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;

class KpiBenchmarkCalculationConfigTest {

	@Mock private BatchConfig batchConfig;

	@Mock private SchedulingConfig schedulingConfig;

	private KpiBenchmarkCalculationConfig config;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		config = new KpiBenchmarkCalculationConfig();
		config.setBatching(batchConfig);
		config.setScheduling(schedulingConfig);
	}

	@Test
	void testValidateConfiguration_WithValidName() {
		config.setName("test-job");
		config.validateConfiguration();
		assertTrue(config.getConfigValidationErrors().isEmpty());
	}

	@Test
	void testValidateConfiguration_WithEmptyName() {
		config.setName("");
		config.validateConfiguration();
		assertTrue(config.getConfigValidationErrors().contains("The job 'name' parameter is required"));
	}

	@Test
	void testValidateConfiguration_WithNullName() {
		config.setName(null);
		config.validateConfiguration();
		assertTrue(config.getConfigValidationErrors().contains("The job 'name' parameter is required"));
	}

	@Test
	void testGetConfigValidationErrors_ReturnsUnmodifiableSet() {
		config.setName("test-job");
		Set<String> errors = config.getConfigValidationErrors();
		assertThrows(UnsupportedOperationException.class, () -> errors.add("new error"));
	}
}
