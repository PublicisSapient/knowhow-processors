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

package com.publicissapient.kpidashboard.utils;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class NumberUtils {
	public static final int ROUNDING_SCALE_1 = 1;
	public static final int ROUNDING_SCALE_2 = 2;

	public static final double PERCENTAGE_MULTIPLIER = 100.0D;

	public static boolean isNumeric(String s) {
		if (StringUtils.isBlank(s)) {
			return false;
		}
		try {
			Double.parseDouble(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Calculates the specified percentile of a list of values using linear interpolation.
	 *
	 * @param values the list of double values to calculate percentile from
	 * @param percentile the percentile to calculate (0-100)
	 * @param isPositiveTrend true if higher values are better (ascending sort), false if lower values
	 *     are better (descending sort)
	 * @return the calculated percentile value
	 * @throws IllegalArgumentException if values list is null or empty
	 */
	public static double percentile(List<Double> values, double percentile, boolean isPositiveTrend) {

		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("Values list cannot be null or empty");
		}

		List<Double> sortedValues =
				values.stream()
						.distinct()
						.sorted(isPositiveTrend ? Double::compareTo : (a, b) -> Double.compare(b, a))
						.toList();

		if (sortedValues.size() == 1) {
			return sortedValues.get(0);
		}

		double index = (percentile / 100.0) * (sortedValues.size() - 1);
		int lowerIndex = (int) Math.floor(index);
		int upperIndex = (int) Math.ceil(index);

		if (lowerIndex == upperIndex) {
			return sortedValues.get(lowerIndex);
		}

		double lowerValue = sortedValues.get(lowerIndex);
		double upperValue = sortedValues.get(upperIndex);
		double weight = index - lowerIndex;

		return lowerValue + weight * (upperValue - lowerValue);
	}
}
