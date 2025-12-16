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

/**
 * Strategy pattern implementation for selecting appropriate KPI data parsers based on filter types.
 * Provides a centralized way to determine which parser to use for different KPI visualization
 * types.
 *
 * @author kunkambl
 */
@Component
public class KpiParserStrategy {

	private final LineGraphParser lineGraphParser;
	private final LineFilterGraphParser lineFilterGraphParser;
	private final LineRadioFilterGraphParser lineRadioFilterGraphParser;
	private final LineMultiFilterParser lineMultiFilterParser;

	/**
	 * Constructs a KpiParserStrategy with all available parser implementations.
	 *
	 * @param lineGraphParser parser for basic line graph KPIs
	 * @param lineFilterGraphParser parser for dropdown/multiselect filtered KPIs
	 * @param lineRadioFilterGraphParser parser for radio button filtered KPIs
	 * @param lineMultiFilterParser parser for multi-type filtered KPIs
	 */
	public KpiParserStrategy(
			LineGraphParser lineGraphParser,
			LineFilterGraphParser lineFilterGraphParser,
			LineRadioFilterGraphParser lineRadioFilterGraphParser,
			LineMultiFilterParser lineMultiFilterParser) {
		this.lineGraphParser = lineGraphParser;
		this.lineFilterGraphParser = lineFilterGraphParser;
		this.lineRadioFilterGraphParser = lineRadioFilterGraphParser;
		this.lineMultiFilterParser = lineMultiFilterParser;
	}

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
			case "dropdown", "multiselectdropdown" -> lineFilterGraphParser;
			case "radiobutton" -> lineRadioFilterGraphParser;
			case "multitypefilters" -> lineMultiFilterParser;
			default -> lineGraphParser;
		};
	}
}
