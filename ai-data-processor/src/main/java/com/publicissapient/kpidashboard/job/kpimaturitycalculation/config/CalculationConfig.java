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

package com.publicissapient.kpidashboard.job.kpimaturitycalculation.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MapUtils;

import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import lombok.Data;

/**
 * Configuration class for the calculations performed on the kpi maturity
 * calculation job
 */
@Data
public class CalculationConfig implements ConfigValidator {

	@Data
	public static class DataPoints {
		private int count;
	}

	@Data
	public static class Maturity {
		private Map<String, Double> weights;
	}

	private static final int MAXIMUM_DATA_POINTS_ALLOWED = 15;

	private final DataPoints dataPoints = new DataPoints();

	private final Maturity maturity = new Maturity();

	private Set<String> configValidationErrors = new HashSet<>();

	@Override
	public void validateConfiguration() {
		if (MapUtils.isEmpty(this.maturity.weights)) {
			configValidationErrors.add("No kpi maturity weight configuration could be found");
		}

		for (Map.Entry<String, Double> categoryIdWeightEntry : this.maturity.weights.entrySet()) {
			if (categoryIdWeightEntry.getValue() < 0.0D) {
				configValidationErrors.add(String.format(
						"A kpi maturity category weight must be higher or equal "
								+ "to zero. Invalid category '%s' was found with a weight of '%s'",
						categoryIdWeightEntry.getKey(), categoryIdWeightEntry.getValue()));
			}
		}

		double weightagesSum = this.maturity.getWeights().values().stream().mapToDouble(Double::doubleValue).sum();
		if (Double.compare(1.0D, weightagesSum) != 0) {
			configValidationErrors.add("The sum of all kpi maturity category weightages must be 1");
		}

		if (this.dataPoints.count < 1 || this.dataPoints.count > MAXIMUM_DATA_POINTS_ALLOWED) {
			configValidationErrors.add("The data points used for kpi maturity calculation must be between 1 and 15");
		}
	}

	@Override
	public Set<String> getConfigValidationErrors() {
		return Collections.unmodifiableSet(this.configValidationErrors);
	}

	public Set<String> getAllConfiguredCategories() {
		return this.maturity.weights.keySet().stream().filter(
				category -> this.maturity.weights.get(category) != null && this.maturity.weights.get(category) > 0.0D)
				.collect(Collectors.toSet());
	}
}
