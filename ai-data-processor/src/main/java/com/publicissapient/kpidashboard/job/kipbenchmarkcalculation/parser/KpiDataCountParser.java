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

package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.publicissapient.kpidashboard.common.model.application.DataCount;

public abstract class KpiDataCountParser {

	private static final String VALUE = "value";
	private static final String LINE_VALUE = "line_value";

	public abstract Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList);

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

	private Double parseValue(Object value) {
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		} else if (value instanceof String) {
			try {
				return Double.parseDouble((String) value);
			} catch (NumberFormatException e) {
				// Handle the case where the string cannot be parsed as a number
				return null;
			}
		}
		return null;
	}
}
