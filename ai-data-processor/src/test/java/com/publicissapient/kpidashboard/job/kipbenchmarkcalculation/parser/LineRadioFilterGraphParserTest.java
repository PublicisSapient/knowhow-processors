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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;

class LineRadioFilterGraphParserTest {

	private LineRadioFilterGraphParser parser;

	@BeforeEach
	void setUp() {
		parser = new LineRadioFilterGraphParser();
	}

	@Test
	void testGetKpiDataPoints_WithValidData() {
		DataCount innerDataCount = new DataCount();
		innerDataCount.setValue(10.5);
		innerDataCount.setLineValue(20.3);

		List<DataCount> innerDataCountList = Arrays.asList(innerDataCount);

		DataCount outerDataCount = new DataCount();
		outerDataCount.setValue(innerDataCountList);

		List<DataCount> outerDataCountList = Arrays.asList(outerDataCount);

		DataCountGroup group1 = new DataCountGroup();
		group1.setFilter("Filter1");
		group1.setValue(outerDataCountList);

		DataCountGroup group2 = new DataCountGroup();
		group2.setFilter("Filter2");
		group2.setValue(outerDataCountList);

		List<DataCountGroup> kpiDataList = Arrays.asList(group1, group2);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);

		assertEquals(4, result.size());
		assertTrue(result.containsKey("value#Filter1"));
		assertTrue(result.containsKey("line_value#Filter1"));
		assertTrue(result.containsKey("value#Filter2"));
		assertTrue(result.containsKey("line_value#Filter2"));
		assertEquals(Arrays.asList(10.5), result.get("value#Filter1"));
		assertEquals(Arrays.asList(20.3), result.get("line_value#Filter1"));
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
	void testGetKpiDataPoints_WithNullGroup() {
		List<DataCountGroup> kpiDataList = Arrays.asList((DataCountGroup) null);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);
		assertTrue(result.isEmpty());
	}

	@Test
	void testGetKpiDataPoints_WithEmptyValue() {
		DataCountGroup group = new DataCountGroup();
		group.setFilter("Filter1");
		group.setValue(Collections.emptyList());

		List<DataCountGroup> kpiDataList = Arrays.asList(group);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);
		assertTrue(result.isEmpty());
	}
}
