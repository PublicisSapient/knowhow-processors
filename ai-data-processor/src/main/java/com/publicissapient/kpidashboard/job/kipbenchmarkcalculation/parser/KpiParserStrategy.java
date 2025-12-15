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

import org.springframework.stereotype.Component;

@Component
public class KpiParserStrategy {

	private final LineGraphParser lineGraphParser;
	private final LineFilterGraphParser lineFilterGraphParser;
	private final LineRadioFilterGraphParser lineRadioFilterGraphParser;
	private final LineMultiFilterParser lineMultiFilterParser;

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
