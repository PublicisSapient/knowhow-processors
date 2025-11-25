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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalculationConfigTest {

	private CalculationConfig calculationConfig;

	@BeforeEach
	void setUp() {
		calculationConfig = new CalculationConfig();
	}

	@Test
	void when_EmptyWeightsMapThen_ValidationErrorAdded() {
		// Arrange
		calculationConfig.getMaturity().setWeights(new HashMap<>());
		calculationConfig.getDataPoints().setCount(5);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
	}

	@Test
	void when_NegativeWeightValueThen_ValidationErrorAdded() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", -0.1);
		weights.put("quality", 1.1);
		calculationConfig.getMaturity().setWeights(weights);
		calculationConfig.getDataPoints().setCount(5);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
	}

	@Test
	void when_WeightsSumNotEqualToOneThen_ValidationErrorAdded() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 0.3);
		weights.put("quality", 0.3);
		calculationConfig.getMaturity().setWeights(weights);
		calculationConfig.getDataPoints().setCount(5);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
	}

	@Test
	void when_DataPointsCountLessThanOneThen_ValidationErrorAdded() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 1.0);
		calculationConfig.getMaturity().setWeights(weights);
		calculationConfig.getDataPoints().setCount(0);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
		assertTrue(errors.contains("The data points used for kpi maturity calculation must be between 1 and 15"));
	}

	@Test
	void when_DataPointsCountGreaterThanFifteenThen_ValidationErrorAdded() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 1.0);
		calculationConfig.getMaturity().setWeights(weights);
		calculationConfig.getDataPoints().setCount(16);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
		assertTrue(errors.contains("The data points used for kpi maturity calculation must be between 1 and 15"));
	}

	@Test
	void when_ValidDataPointsBoundaryValuesThen_NoValidationErrors() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 1.0);
		calculationConfig.getMaturity().setWeights(weights);

		// Test minimum boundary
		calculationConfig.getDataPoints().setCount(1);
		calculationConfig.validateConfiguration();
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());

		// Reset errors for next test
		calculationConfig = new CalculationConfig();
		calculationConfig.getMaturity().setWeights(weights);

		// Test maximum boundary
		calculationConfig.getDataPoints().setCount(15);
		calculationConfig.validateConfiguration();
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_CategoriesWithPositiveWeightsThen_ReturnsPositiveWeightCategories() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 0.5);
		weights.put("quality", 0.3);
		weights.put("value", 0.2);
		calculationConfig.getMaturity().setWeights(weights);

		// Act
		Set<String> configuredCategories = calculationConfig.getAllConfiguredCategories();

		// Assert
		assertEquals(3, configuredCategories.size());
		assertTrue(configuredCategories.contains("speed"));
		assertTrue(configuredCategories.contains("quality"));
		assertTrue(configuredCategories.contains("value"));
	}

	@Test
	void when_CategoriesWithZeroWeightsThen_ExcludesZeroWeightCategories() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 0.5);
		weights.put("quality", 0.0);
		weights.put("value", 0.5);
		calculationConfig.getMaturity().setWeights(weights);

		// Act
		Set<String> configuredCategories = calculationConfig.getAllConfiguredCategories();

		// Assert
		assertEquals(2, configuredCategories.size());
		assertTrue(configuredCategories.contains("speed"));
		assertFalse(configuredCategories.contains("quality"));
		assertTrue(configuredCategories.contains("value"));
	}

	@Test
	void when_CategoriesWithNullWeightsThen_ExcludesNullWeightCategories() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 0.5);
		weights.put("quality", null);
		weights.put("value", 0.5);
		calculationConfig.getMaturity().setWeights(weights);

		// Act
		Set<String> configuredCategories = calculationConfig.getAllConfiguredCategories();

		// Assert
		assertEquals(2, configuredCategories.size());
		assertTrue(configuredCategories.contains("speed"));
		assertFalse(configuredCategories.contains("quality"));
		assertTrue(configuredCategories.contains("value"));
	}

	@Test
	void when_ValidationErrorsExistThen_ReturnsUnmodifiableSet() {
		// Arrange
		calculationConfig.getMaturity().setWeights(new HashMap<>());
		calculationConfig.getDataPoints().setCount(0);
		calculationConfig.validateConfiguration();

		// Act
		Set<String> errors = calculationConfig.getConfigValidationErrors();

		// Assert
		assertThrows(UnsupportedOperationException.class, () -> {
			errors.add("Should not be able to modify");
		});
	}

	@Test
	void when_MultipleValidationErrorsThen_AllErrorsAccumulated() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("invalidCategory", -0.5);
		weights.put("speed", 0.3);
		calculationConfig.getMaturity().setWeights(weights);
		calculationConfig.getDataPoints().setCount(20);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertTrue(errors.size() >= 3); // Invalid category, negative weight, sum != 1, data points > 15
	}

	@Test
	void when_WeightsSumWithFloatingPointPrecisionThen_HandlesCorrectly() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 0.1);
		weights.put("quality", 0.2);
		weights.put("value", 0.3);
		weights.put("dora", 0.4);
		// Sum = 1.0 exactly
		calculationConfig.getMaturity().setWeights(weights);
		calculationConfig.getDataPoints().setCount(5);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_WeightsSumSlightlyOffDueToFloatingPointThen_ValidationErrorAdded() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 0.33333);
		weights.put("quality", 0.33333);
		weights.put("value", 0.33333);
		// Sum = 0.99999, not exactly 1.0
		calculationConfig.getMaturity().setWeights(weights);
		calculationConfig.getDataPoints().setCount(5);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
	}

	@Test
	void when_AllPredefinedCategoriesUsedThen_NoValidationErrors() {
		// Arrange
		Map<String, Double> weights = new HashMap<>();
		weights.put("speed", 0.25);
		weights.put("quality", 0.25);
		weights.put("value", 0.25);
		weights.put("dora", 0.25);
		calculationConfig.getMaturity().setWeights(weights);
		calculationConfig.getDataPoints().setCount(10);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_DataPointsObjectInitializedThen_HasDefaultValues() {
		// Arrange & Act
		CalculationConfig.DataPoints dataPoints = new CalculationConfig.DataPoints();

		// Assert
		assertEquals(0, dataPoints.getCount()); // Default int value
	}
}
