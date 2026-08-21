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

package com.publicissapient.kpidashboard.job.epichygienecalculation.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;

@DisplayName("EpicHygieneCalculationJobConfig Tests")
class EpicHygieneCalculationJobConfigTest {

	private EpicHygieneCalculationJobConfig jobConfig;

	@BeforeEach
	void setUp() {
		BatchConfig batchConfig = new BatchConfig();
		batchConfig.setChunkSize(10);

		SchedulingConfig schedulingConfig = new SchedulingConfig();
		schedulingConfig.setCron("0 0 4 * * SAT");

		jobConfig = new EpicHygieneCalculationJobConfig();
		jobConfig.setName("epic-hygiene-calculation");
		jobConfig.setBatching(batchConfig);
		jobConfig.setScheduling(schedulingConfig);
		jobConfig.setCalculationConfig(new EpicHygieneJobConfig());
	}

	@Nested
	@DisplayName("Job level validation")
	class JobLevelValidation {

		@Test
		@DisplayName("Should report no error for a fully valid configuration")
		void retrieveJobConfigValidationErrors_ValidConfig_NoErrors() {
			jobConfig.retrieveJobConfigValidationErrors();

			assertTrue(jobConfig.getConfigValidationErrors().isEmpty());
		}

		@Test
		@DisplayName("Should default the chunk size to 10 in the shipped configuration")
		void batching_DefaultChunkSize_IsTen() {
			assertEquals(10, jobConfig.getBatching().getChunkSize());
		}

		@Test
		@DisplayName("Should require the job name")
		void validateConfiguration_MissingName_ReportsError() {
			jobConfig.setName(null);

			jobConfig.validateConfiguration();

			assertTrue(
					jobConfig.getConfigValidationErrors().stream()
							.anyMatch(error -> error.contains("'name'")));
		}

		@Test
		@DisplayName("Should report an invalid chunk size")
		void retrieveJobConfigValidationErrors_InvalidChunkSize_ReportsError() {
			jobConfig.getBatching().setChunkSize(0);

			jobConfig.retrieveJobConfigValidationErrors();

			assertTrue(
					jobConfig.getConfigValidationErrors().stream()
							.anyMatch(error -> error.contains("chunk size")));
		}

		@Test
		@DisplayName("Should report a missing cron expression")
		void retrieveJobConfigValidationErrors_MissingCron_ReportsError() {
			jobConfig.getScheduling().setCron(null);

			jobConfig.retrieveJobConfigValidationErrors();

			assertTrue(
					jobConfig.getConfigValidationErrors().stream().anyMatch(error -> error.contains("cron")));
		}

		@Test
		@DisplayName("Should report a missing configuration section instead of failing")
		void retrieveJobConfigValidationErrors_MissingSection_ReportsError() {
			jobConfig.setCalculationConfig(null);

			jobConfig.retrieveJobConfigValidationErrors();

			assertFalse(jobConfig.getConfigValidationErrors().isEmpty());
		}

		@Test
		@DisplayName("Should expose an unmodifiable error set")
		void getConfigValidationErrors_IsUnmodifiable() {
			Set<String> errors = jobConfig.getConfigValidationErrors();

			assertThrows(UnsupportedOperationException.class, () -> errors.add("boom"));
		}
	}

	@Nested
	@DisplayName("Calculation configuration validation")
	class CalculationConfigValidation {

		private EpicHygieneJobConfig calculationConfig;

		@BeforeEach
		void setUp() {
			calculationConfig = new EpicHygieneJobConfig();
		}

		@Test
		@DisplayName("Should default to kpi312 with retries and fallback enabled")
		void defaults_AreProductionReady() {
			assertEquals("kpi312", calculationConfig.getKpiId());
			assertEquals(3, calculationConfig.getMaxRetryAttempts());
			assertEquals(2000L, calculationConfig.getRetryBackoffMillis());
			assertTrue(calculationConfig.isFallbackEnabled());
		}

		@Test
		@DisplayName("Should accept the default configuration")
		void validateConfiguration_Defaults_NoErrors() {
			calculationConfig.validateConfiguration();

			assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
		}

		@Test
		@DisplayName("Should reject a blank KPI id")
		void validateConfiguration_BlankKpiId_ReportsError() {
			calculationConfig.setKpiId("  ");

			calculationConfig.validateConfiguration();

			assertTrue(
					calculationConfig.getConfigValidationErrors().stream()
							.anyMatch(error -> error.contains("kpiId")));
		}

		@Test
		@DisplayName("Should reject a non positive retry attempt count")
		void validateConfiguration_ZeroAttempts_ReportsError() {
			calculationConfig.setMaxRetryAttempts(0);

			calculationConfig.validateConfiguration();

			assertTrue(
					calculationConfig.getConfigValidationErrors().stream()
							.anyMatch(error -> error.contains("maxRetryAttempts")));
		}

		@Test
		@DisplayName("Should reject an excessive retry attempt count")
		void validateConfiguration_TooManyAttempts_ReportsError() {
			calculationConfig.setMaxRetryAttempts(50);

			calculationConfig.validateConfiguration();

			assertTrue(
					calculationConfig.getConfigValidationErrors().stream()
							.anyMatch(error -> error.contains("<= 10")));
		}

		@Test
		@DisplayName("Should reject a negative back-off")
		void validateConfiguration_NegativeBackoff_ReportsError() {
			calculationConfig.setRetryBackoffMillis(-1L);

			calculationConfig.validateConfiguration();

			assertTrue(
					calculationConfig.getConfigValidationErrors().stream()
							.anyMatch(error -> error.contains("retryBackoffMillis")));
		}

		@Test
		@DisplayName("Should accept a zero back-off")
		void validateConfiguration_ZeroBackoff_NoErrors() {
			calculationConfig.setRetryBackoffMillis(0L);

			calculationConfig.validateConfiguration();

			assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
		}
	}
}
