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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.publicissapient.kpidashboard.common.model.application.DataCount;

/**
 * Abstract base class for parsing KPI data and extracting data points. Provides common
 * functionality for converting various KPI data structures into standardized data point maps for
 * benchmark calculations.
 *
 * @author kunkambl
 */
public abstract class KpiDataCountParser {

	private static final String VALUE = "value";
	private static final String LINE_VALUE = "line_value";

	/**
	 * Extracts KPI data points from the provided data list. Implementation varies based on the
	 * specific KPI data structure.
	 *
	 * @param kpiDataList the list of KPI data to parse
	 * @return a map containing extracted data points with keys as data types and values as numeric
	 *     lists
	 */
	public abstract Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList);

	/**
	 * Extracts numeric data points from a list of DataCount objects. Processes both primary values
	 * and line values if available.
	 *
	 * @param dataCountList the list of DataCount objects to process
	 * @return a map with "value" and "line_value" keys containing extracted numeric data
	 */
	protected Map<String, List<Double>> extractDataPoints(List<DataCount> dataCountList) {
		Map<String, List<Double>> valueList = new HashMap<>();
		dataCountList.forEach(
				dataCount -> {
					if (dataCount != null && dataCount.getValue() != null) {
						// Extract the numeric value from DataCount
						Object value = dataCount.getValue();
						Object lineValue = dataCount.getLineValue();
						valueList.computeIfAbsent(VALUE, k -> new ArrayList<>()).add(parseValue(value));
						if (lineValue != null)
							valueList
									.computeIfAbsent(LINE_VALUE, k -> new ArrayList<>())
									.add(parseValue(lineValue));
					}
				});
		return valueList;
	}

	/**
	 * Converts an object value to a Double. Handles both Number and String types with appropriate
	 * parsing.
	 *
	 * @param value the object to convert to Double
	 * @return the parsed Double value, or null if parsing fails
	 */
	private Double parseValue(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		} else if (value instanceof String stringValue) {
			try {
				return Double.parseDouble(stringValue);
			} catch (NumberFormatException e) {
				// Handle the case where the string cannot be parsed as a number
				return null;
			}
		}
		return null;
	}
}
