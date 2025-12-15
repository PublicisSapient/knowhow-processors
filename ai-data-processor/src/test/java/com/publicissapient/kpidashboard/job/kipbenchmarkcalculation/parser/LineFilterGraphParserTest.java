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
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;

class LineFilterGraphParserTest {

	private LineFilterGraphParser parser;

	@BeforeEach
	void setUp() {
		parser = new LineFilterGraphParser();
	}

	@Test
	void testGetKpiDataPoints_WithOverallFilter() {
		DataCount innerDataCount = new DataCount();
		innerDataCount.setValue(10.5);
		innerDataCount.setLineValue(20.3);

		List<DataCount> innerDataCountList = Arrays.asList(innerDataCount);

		DataCount outerDataCount = new DataCount();
		outerDataCount.setValue(innerDataCountList);

		List<DataCount> outerDataCountList = Arrays.asList(outerDataCount);

		DataCountGroup overallGroup = new DataCountGroup();
		overallGroup.setFilter("Overall");
		overallGroup.setValue(outerDataCountList);

		DataCountGroup otherGroup = new DataCountGroup();
		otherGroup.setFilter("Other");

		List<DataCountGroup> kpiDataList = Arrays.asList(overallGroup, otherGroup);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);

		assertEquals(2, result.size());
		assertTrue(result.containsKey("value"));
		assertTrue(result.containsKey("line_value"));
		assertEquals(Arrays.asList(10.5), result.get("value"));
		assertEquals(Arrays.asList(20.3), result.get("line_value"));
	}

	@Test
	void testGetKpiDataPoints_WithoutOverallFilter() {
		DataCountGroup group = new DataCountGroup();
		group.setFilter("Other");

		List<DataCountGroup> kpiDataList = Arrays.asList(group);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);
		assertTrue(result.isEmpty());
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
	void testGetKpiDataPoints_WithNullFilter() {
		DataCountGroup group = new DataCountGroup();
		group.setFilter(null);

		List<DataCountGroup> kpiDataList = Arrays.asList(group);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);
		assertTrue(result.isEmpty());
	}

	@Test
	void testGetKpiDataPoints_WithCaseInsensitiveOverall() {
		DataCount innerDataCount = new DataCount();
		innerDataCount.setValue(15.0);

		List<DataCount> innerDataCountList = Arrays.asList(innerDataCount);

		DataCount outerDataCount = new DataCount();
		outerDataCount.setValue(innerDataCountList);

		List<DataCount> outerDataCountList = Arrays.asList(outerDataCount);

		DataCountGroup overallGroup = new DataCountGroup();
		overallGroup.setFilter("overall");
		overallGroup.setValue(outerDataCountList);

		List<DataCountGroup> kpiDataList = Arrays.asList(overallGroup);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);

		assertEquals(1, result.size());
		assertEquals(Arrays.asList(15.0), result.get("value"));
	}
}
