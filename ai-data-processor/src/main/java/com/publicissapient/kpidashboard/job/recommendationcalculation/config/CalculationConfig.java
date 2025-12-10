/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.job.recommendationcalculation.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;
import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import lombok.Data;

/**
 * Configuration class for recommendation calculation job.
 */
@Data
public class CalculationConfig implements ConfigValidator {

	private Set<String> configValidationErrors = new HashSet<>();

	private Persona enabledPersona;
	private List<String> kpiList;

	@Override
	public void validateConfiguration() {
		if (enabledPersona == null) {
			configValidationErrors.add("No enabled persona configured for recommendation calculation");
		}
		if (CollectionUtils.isEmpty(kpiList)) {
			configValidationErrors.add("No KPI list configured for recommendation calculation");
		}
	}

	@Override
	public Set<String> getConfigValidationErrors() {
		return Collections.unmodifiableSet(configValidationErrors);
	}
}
