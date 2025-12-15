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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.common.model.application.DataCount;

class LineGraphParserTest {

	private LineGraphParser parser;

	@BeforeEach
	void setUp() {
		parser = new LineGraphParser();
	}

	@Test
	void testGetKpiDataPoints_WithValidData() {
		DataCount innerDataCount1 = new DataCount();
		innerDataCount1.setValue(10.5);
		innerDataCount1.setLineValue(20.3);

		DataCount innerDataCount2 = new DataCount();
		innerDataCount2.setValue(15.7);

		List<DataCount> innerDataCountList = Arrays.asList(innerDataCount1, innerDataCount2);

		DataCount outerDataCount = new DataCount();
		outerDataCount.setValue(innerDataCountList);

		List<DataCount> kpiDataTrendValueList = Arrays.asList(outerDataCount);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataTrendValueList);

		assertEquals(2, result.size());
		assertTrue(result.containsKey("value"));
		assertTrue(result.containsKey("line_value"));
		assertEquals(Arrays.asList(10.5, 15.7), result.get("value"));
		assertEquals(Arrays.asList(20.3), result.get("line_value"));
	}

	@Test
	void testGetKpiDataPoints_WithNullList() {
		Map<String, List<Double>> result = parser.getKpiDataPoints(null);
		assertTrue(result.isEmpty());
	}

	@Test
	void testGetKpiDataPoints_WithEmptyList() {
		Map<String, List<Double>> result = parser.getKpiDataPoints(Collections.emptyList());
		assertTrue(result.isEmpty());
	}

	@Test
	void testGetKpiDataPoints_WithEmptyInnerData() {
		DataCount outerDataCount = new DataCount();
		outerDataCount.setValue(Collections.emptyList());

		List<DataCount> kpiDataTrendValueList = Arrays.asList(outerDataCount);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataTrendValueList);
		assertTrue(result.isEmpty());
	}
}
