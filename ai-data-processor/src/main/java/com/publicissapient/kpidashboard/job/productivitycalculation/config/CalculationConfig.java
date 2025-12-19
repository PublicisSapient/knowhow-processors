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

package com.publicissapient.kpidashboard.job.productivitycalculation.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MapUtils;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import lombok.Data;

/**
 * Configuration class for the calculations performed on the productivity calculations job
 */
@Data
public class CalculationConfig implements ConfigValidator {
    public static final String CATEGORY_SPEED = "speed";
    public static final String CATEGORY_QUALITY = "quality";
    public static final String CATEGORY_EFFICIENCY = "efficiency";
    public static final String CATEGORY_PRODUCTIVITY = "productivity";

    @Data
    public static class DataPoints {
        private int count;
    }

    private static final Set<String> PREDEFINED_CATEGORIES = Set.of(CATEGORY_SPEED, CATEGORY_QUALITY,
            CATEGORY_EFFICIENCY, CATEGORY_PRODUCTIVITY);

    private static final int MAXIMUM_DATA_POINTS_ALLOWED = 15;

    private final DataPoints dataPoints = new DataPoints();

    private Set<String> configValidationErrors = new HashSet<>();

    private Map<String, Double> weights;

    @Override
    public void validateConfiguration() {
        if (MapUtils.isEmpty(weights)) {
            configValidationErrors.add("No productivity gain configuration could be found");
        }
        for (Map.Entry<String, Double> categoryIdWeightEntry : weights.entrySet()) {
            if (!PREDEFINED_CATEGORIES.contains(categoryIdWeightEntry.getKey())) {
                configValidationErrors.add(String.format(
                        "Category '%s' is invalid. The only allowed categories are %s", categoryIdWeightEntry.getKey(),
                        String.join(CommonConstant.COMMA, PREDEFINED_CATEGORIES)));
            }
            if (categoryIdWeightEntry.getValue() < 0.0D) {
                configValidationErrors.add(String.format(
                        "A productivity gain category weight must be higher or equal "
                                + "to zero. Invalid category '%s' was found with a weight of '%s'",
                        categoryIdWeightEntry.getKey(), categoryIdWeightEntry.getValue()));
            }
        }
        double weightagesSum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if(Double.compare(1.0D, weightagesSum) != 0) {
            configValidationErrors.add("The sum of all category weightages must be 1");
        }

        if(this.dataPoints.count < 1 || this.dataPoints.count > MAXIMUM_DATA_POINTS_ALLOWED) {
            configValidationErrors.add("The data points used for productivity calculation must be between 1 and 15");
        }
    }

    @Override
    public Set<String> getConfigValidationErrors() {
        return Collections.unmodifiableSet(this.configValidationErrors);
    }

    public Double getWeightForCategory(String category) {
        return this.weights.get(category);
    }

    public Set<String> getAllConfiguredCategories() {
        return weights.keySet().stream()
                .filter(category -> this.weights.get(category) != null && this.weights.get(category) > 0.0D)
                .collect(Collectors.toSet());
    }
}
