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

class LineMultiFilterParserTest {

	private LineMultiFilterParser parser;

	@BeforeEach
	void setUp() {
		parser = new LineMultiFilterParser();
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

		DataCountGroup group = new DataCountGroup();
		group.setFilter1("Filter1");
		group.setFilter2("Filter2");
		group.setValue(outerDataCountList);

		List<DataCountGroup> kpiDataList = Arrays.asList(group);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);

		assertEquals(2, result.size());
		assertTrue(result.containsKey("value#Filter1#Filter2"));
		assertTrue(result.containsKey("line_value#Filter1#Filter2"));
		assertEquals(Arrays.asList(10.5), result.get("value#Filter1#Filter2"));
		assertEquals(Arrays.asList(20.3), result.get("line_value#Filter1#Filter2"));
	}

	@Test
	void testGetKpiDataPoints_WithMultipleGroups() {
		DataCount innerDataCount = new DataCount();
		innerDataCount.setValue(15.0);

		List<DataCount> innerDataCountList = Arrays.asList(innerDataCount);

		DataCount outerDataCount = new DataCount();
		outerDataCount.setValue(innerDataCountList);

		List<DataCount> outerDataCountList = Arrays.asList(outerDataCount);

		DataCountGroup group1 = new DataCountGroup();
		group1.setFilter1("A");
		group1.setFilter2("X");
		group1.setValue(outerDataCountList);

		DataCountGroup group2 = new DataCountGroup();
		group2.setFilter1("B");
		group2.setFilter2("Y");
		group2.setValue(outerDataCountList);

		List<DataCountGroup> kpiDataList = Arrays.asList(group1, group2);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);

		assertEquals(2, result.size());
		assertTrue(result.containsKey("value#A#X"));
		assertTrue(result.containsKey("value#B#Y"));
		assertEquals(Arrays.asList(15.0), result.get("value#A#X"));
		assertEquals(Arrays.asList(15.0), result.get("value#B#Y"));
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
		group.setFilter1("Filter1");
		group.setFilter2("Filter2");
		group.setValue(Collections.emptyList());

		List<DataCountGroup> kpiDataList = Arrays.asList(group);

		Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataList);
		assertTrue(result.isEmpty());
	}
}
