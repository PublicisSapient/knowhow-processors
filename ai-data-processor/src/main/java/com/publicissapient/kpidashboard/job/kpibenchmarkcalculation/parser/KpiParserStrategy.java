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

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Strategy pattern implementation for selecting appropriate KPI data parsers based on filter types.
 * Provides a centralized way to determine which parser to use for different KPI visualization
 * types.
 *
 * @author kunkambl
 */
@Component
@RequiredArgsConstructor
public class KpiParserStrategy {

	private final LineGraphParser lineGraphParser;
	private final LineFilterGraphParser lineFilterGraphParser;
	private final LineRadioFilterGraphParser lineRadioFilterGraphParser;
	private final LineMultiFilterParser lineMultiFilterParser;
	private final CumulativeMultilineChartParser cumulativeMultilineChartParser;
	private final CumulativeMultilineChartRadioButtonParser cumulativeMultilineChartRadioButtonParser;

	/**
	 * Selects the appropriate parser based on the KPI filter type.
	 *
	 * @param kpiFilter the filter type identifier (dropdown, radiobutton, multitypefilters, etc.)
	 * @return the corresponding parser implementation, defaults to lineGraphParser if no match
	 */
	public KpiDataCountParser getParser(String kpiFilter) {
		if (kpiFilter == null || kpiFilter.isEmpty()) {
			return lineGraphParser;
		}

		return switch (kpiFilter.toLowerCase()) {
			case "dropdown_line",
							"multiselectdropdown_line",
							"dropdown_grouped_column_plus_line",
							"multiselectdropdown_grouped_column_plus_line" ->
					lineFilterGraphParser;
			case "radiobutton_line", "radiobutton_grouped_column_plus_line" -> lineRadioFilterGraphParser;
			case "multitypefilters_line", "multitypefilters_grouped_column_plus_line" ->
					lineMultiFilterParser;
			case "_cumulativemultilinechart" -> cumulativeMultilineChartParser;
			case "radiobutton_cumulativemultilinechart" -> cumulativeMultilineChartRadioButtonParser;
			default -> lineGraphParser;
		};
	}
}
