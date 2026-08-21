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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import jakarta.annotation.PostConstruct;
import lombok.Data;

/** Spring Boot configuration for the epic-hygiene-calculation batch job. */
@Data
@Component
@ConfigurationProperties(prefix = "jobs.epic-hygiene-calculation")
public class EpicHygieneCalculationJobConfig implements ConfigValidator {

	private String name;
	private BatchConfig batching = new BatchConfig();
	private SchedulingConfig scheduling = new SchedulingConfig();
	private EpicHygieneJobConfig calculationConfig = new EpicHygieneJobConfig();
	private Set<String> configValidationErrors = new HashSet<>();

	@Override
	public void validateConfiguration() {
		if (StringUtils.isEmpty(name)) {
			configValidationErrors.add("The job 'name' parameter is required");
		}
	}

	@Override
	public Set<String> getConfigValidationErrors() {
		return Collections.unmodifiableSet(configValidationErrors);
	}

	@PostConstruct
	public void retrieveJobConfigValidationErrors() {
		validateConfiguration();

		collectErrorsOf(calculationConfig);
		collectErrorsOf(batching);
		collectErrorsOf(scheduling);
	}

	private void collectErrorsOf(ConfigValidator validator) {
		if (validator == null) {
			configValidationErrors.add("A required configuration section is missing");
			return;
		}
		validator.validateConfiguration();
		configValidationErrors.addAll(validator.getConfigValidationErrors());
	}
}
