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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import lombok.Data;

/** Inner calculation parameters for the epic-hygiene-calculation batch job. */
@Data
public class EpicHygieneJobConfig implements ConfigValidator {

	/** Hard upper bound so a mis-configuration cannot stall the whole job. */
	private static final int MAX_ALLOWED_RETRY_ATTEMPTS = 10;

	private Set<String> configValidationErrors = new HashSet<>();

	/** Identifier of the Epic Hygiene KPI exposed by KnowHOW API. */
	private String kpiId = "kpi312";

	/** Total number of attempts (first call included) made against the KPI endpoint. */
	private int maxRetryAttempts = 3;

	/** Base delay between two attempts; the delay grows linearly with the attempt number. */
	private long retryBackoffMillis = 2000L;

	/**
	 * When {@code true} a project whose KPI call could not be satisfied is still persisted, flagged
	 * as a fallback record, so downstream consumers can tell "no data" apart from "never ran". When
	 * {@code false} the failure is propagated and the project is skipped.
	 */
	private boolean fallbackEnabled = true;

	@Override
	public void validateConfiguration() {
		if (StringUtils.isBlank(kpiId)) {
			configValidationErrors.add("calculationConfig.kpiId must not be blank");
		}
		if (maxRetryAttempts < 1) {
			configValidationErrors.add(
					String.format(
							"calculationConfig.maxRetryAttempts must be >= 1. Received %s", maxRetryAttempts));
		}
		if (maxRetryAttempts > MAX_ALLOWED_RETRY_ATTEMPTS) {
			configValidationErrors.add(
					String.format(
							"calculationConfig.maxRetryAttempts must be <= %s. Received %s",
							MAX_ALLOWED_RETRY_ATTEMPTS, maxRetryAttempts));
		}
		if (retryBackoffMillis < 0) {
			configValidationErrors.add(
					String.format(
							"calculationConfig.retryBackoffMillis must not be negative. Received %s",
							retryBackoffMillis));
		}
	}

	@Override
	public Set<String> getConfigValidationErrors() {
		return Collections.unmodifiableSet(configValidationErrors);
	}
}
