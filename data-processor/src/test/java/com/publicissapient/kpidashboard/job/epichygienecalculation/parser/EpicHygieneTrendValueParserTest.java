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

package com.publicissapient.kpidashboard.job.epichygienecalculation.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.client.customapi.dto.IterationKpiDataDTO;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData.EpicHygieneMetric;

@DisplayName("EpicHygieneTrendValueParser Tests")
class EpicHygieneTrendValueParserTest {

	private EpicHygieneTrendValueParser parser;

	@BeforeEach
	void setUp() {
		parser = new EpicHygieneTrendValueParser();
	}

	private IterationKpiDataDTO dto(String label, Double value) {
		return IterationKpiDataDTO.builder().label(label).value(value).build();
	}

	private List<IterationKpiDataDTO> fullTrendValueList() {
		return List.of(
				dto(EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS, 12d),
				dto(EpicHygieneTrendValueParser.LABEL_CONSTRUCTION_READY, 7d),
				IterationKpiDataDTO.builder()
						.label(EpicHygieneTrendValueParser.LABEL_AT_RISK_BLOCKED)
						.value(3d)
						.labelInfo("Readiness < 50%")
						.build(),
				dto(EpicHygieneTrendValueParser.LABEL_AVG_READINESS_SCORE, 68.75d));
	}

	@Nested
	@DisplayName("Parsing typed IterationKpiDataDTO entries")
	class TypedEntries {

		@Test
		@DisplayName("Should parse every label/value pair published by the KPI")
		void parseTrendValueList_TypedEntries_ReturnsAllMetrics() {
			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(fullTrendValueList());

			assertEquals(4, metrics.size());
			assertEquals(EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS, metrics.get(0).getLabel());
			assertEquals(12d, metrics.get(0).getValue());
			assertEquals("Readiness < 50%", metrics.get(2).getLabelInfo());
		}

		@Test
		@DisplayName("Should keep the unit when the KPI publishes one")
		void parseTrendValueList_WithUnit_KeepsUnit() {
			List<EpicHygieneMetric> metrics =
					parser.parseTrendValueList(
							List.of(
									IterationKpiDataDTO.builder()
											.label(EpicHygieneTrendValueParser.LABEL_AVG_READINESS_SCORE)
											.value(80d)
											.unit("%")
											.build()));

			assertEquals(1, metrics.size());
			assertEquals("%", metrics.get(0).getUnit());
		}

		@Test
		@DisplayName("Should skip entries whose label is blank")
		void parseTrendValueList_BlankLabels_SkipsEntries() {
			List<EpicHygieneMetric> metrics =
					parser.parseTrendValueList(
							Arrays.asList(dto(null, 1d), dto("", 2d), dto("   ", 3d), dto("Kept", 4d)));

			assertEquals(1, metrics.size());
			assertEquals("Kept", metrics.get(0).getLabel());
		}

		@Test
		@DisplayName("Should trim surrounding whitespace of textual fields")
		void parseTrendValueList_PaddedLabel_TrimsLabel() {
			List<EpicHygieneMetric> metrics =
					parser.parseTrendValueList(List.of(dto("  Total Active Epics  ", 5d)));

			assertEquals("Total Active Epics", metrics.get(0).getLabel());
		}

		@Test
		@DisplayName("Should keep a null value instead of defaulting it")
		void parseTrendValueList_NullValue_KeepsNull() {
			List<EpicHygieneMetric> metrics =
					parser.parseTrendValueList(List.of(dto("Some label", null)));

			assertEquals(1, metrics.size());
			assertNull(metrics.get(0).getValue());
		}
	}

	@Nested
	@DisplayName("Parsing raw map entries")
	class RawMapEntries {

		@Test
		@DisplayName("Should parse LinkedHashMap entries produced by generic deserialization")
		void parseTrendValueList_LinkedHashMaps_ReturnsMetrics() {
			LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
			entry.put("label", EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS);
			entry.put("value", 9);
			entry.put("labelInfo", "info");
			entry.put("unit", "count");

			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(List.of(entry));

			assertEquals(1, metrics.size());
			assertEquals(9d, metrics.get(0).getValue());
			assertEquals("info", metrics.get(0).getLabelInfo());
			assertEquals("count", metrics.get(0).getUnit());
		}

		@Test
		@DisplayName("Should parse a plain HashMap as well")
		void parseTrendValueList_HashMap_ReturnsMetrics() {
			Map<String, Object> entry = new HashMap<>();
			entry.put("label", EpicHygieneTrendValueParser.LABEL_CONSTRUCTION_READY);
			entry.put("value", 4L);

			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(List.of(entry));

			assertEquals(1, metrics.size());
			assertEquals(4d, metrics.get(0).getValue());
		}

		@Test
		@DisplayName("Should read numeric values delivered as strings")
		void parseTrendValueList_StringNumber_ParsesValue() {
			Map<String, Object> entry = new HashMap<>();
			entry.put("label", EpicHygieneTrendValueParser.LABEL_AVG_READINESS_SCORE);
			entry.put("value", "72.5");

			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(List.of(entry));

			assertEquals(72.5d, metrics.get(0).getValue());
		}

		@Test
		@DisplayName("Should null out values that are not numbers instead of failing")
		void parseTrendValueList_NonNumericValue_ReturnsNullValue() {
			Map<String, Object> entry = new HashMap<>();
			entry.put("label", EpicHygieneTrendValueParser.LABEL_AVG_READINESS_SCORE);
			entry.put("value", "not-a-number");

			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(List.of(entry));

			assertEquals(1, metrics.size());
			assertNull(metrics.get(0).getValue());
		}

		@Test
		@DisplayName("Should treat a blank string value as no value")
		void parseTrendValueList_BlankStringValue_ReturnsNullValue() {
			Map<String, Object> entry = new HashMap<>();
			entry.put("label", "Some label");
			entry.put("value", "   ");

			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(List.of(entry));

			assertNull(metrics.get(0).getValue());
		}

		@Test
		@DisplayName("Should skip maps that carry no label")
		void parseTrendValueList_MapWithoutLabel_SkipsEntry() {
			Map<String, Object> entry = new HashMap<>();
			entry.put("value", 1);

			assertTrue(parser.parseTrendValueList(List.of(entry)).isEmpty());
		}
	}

	@Nested
	@DisplayName("Edge cases")
	class EdgeCases {

		@Test
		@DisplayName("Should return an empty list when trendValueList is null")
		void parseTrendValueList_Null_ReturnsEmptyList() {
			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(null);

			assertNotNull(metrics);
			assertTrue(metrics.isEmpty());
		}

		@Test
		@DisplayName("Should return an empty list when trendValueList is empty")
		void parseTrendValueList_EmptyList_ReturnsEmptyList() {
			assertTrue(parser.parseTrendValueList(Collections.emptyList()).isEmpty());
		}

		@Test
		@DisplayName("Should return an empty list when trendValueList is not a list at all")
		void parseTrendValueList_NotAList_ReturnsEmptyList() {
			assertTrue(parser.parseTrendValueList(new Object()).isEmpty());
			assertTrue(parser.parseTrendValueList("a string payload").isEmpty());
		}

		@Test
		@DisplayName("Should skip unsupported entry types such as DataCount")
		void parseTrendValueList_UnsupportedEntryTypes_SkipsThem() {
			List<Object> entries = new ArrayList<>();
			entries.add(new DataCount("data", 1));
			entries.add(null);
			entries.add(dto(EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS, 2d));

			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(entries);

			assertEquals(1, metrics.size());
			assertEquals(EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS, metrics.get(0).getLabel());
		}

		@Test
		@DisplayName("Should return an empty list when no entry is understood")
		void parseTrendValueList_OnlyUnsupportedEntries_ReturnsEmptyList() {
			assertTrue(parser.parseTrendValueList(List.of(new DataCount("data", 1))).isEmpty());
		}
	}

	@Nested
	@DisplayName("Mapping metrics onto the document")
	class ApplyMetrics {

		@Test
		@DisplayName("Should map every well known label onto its typed field")
		void applyMetrics_KnownLabels_PopulatesTypedFields() {
			EpicHygieneData data = EpicHygieneData.builder().build();

			parser.applyMetrics(data, parser.parseTrendValueList(fullTrendValueList()));

			assertEquals(12, data.getTotalActiveEpics());
			assertEquals(7, data.getConstructionReadyEpics());
			assertEquals(3, data.getAtRiskEpics());
			assertEquals(68.75d, data.getAvgReadinessScore());
			assertEquals(4, data.getMetrics().size());
		}

		@Test
		@DisplayName("Should match labels regardless of case and spacing drift")
		void applyMetrics_LabelDrift_StillMaps() {
			EpicHygieneData data = EpicHygieneData.builder().build();

			parser.applyMetrics(
					data,
					parser.parseTrendValueList(
							List.of(
									dto("total   active epics", 5d),
									dto("CONSTRUCTION READY", 2d),
									dto("At Risk/Blocked", 1d),
									dto("avg readiness score", 50d))));

			assertEquals(5, data.getTotalActiveEpics());
			assertEquals(2, data.getConstructionReadyEpics());
			assertEquals(1, data.getAtRiskEpics());
			assertEquals(50d, data.getAvgReadinessScore());
		}

		@Test
		@DisplayName("Should round fractional counts to the nearest integer")
		void applyMetrics_FractionalCount_Rounds() {
			EpicHygieneData data = EpicHygieneData.builder().build();

			parser.applyMetrics(
					data,
					parser.parseTrendValueList(
							List.of(dto(EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS, 12.6d))));

			assertEquals(13, data.getTotalActiveEpics());
		}

		@Test
		@DisplayName("Should keep unknown labels in metrics without failing")
		void applyMetrics_UnknownLabel_KeptInMetricsOnly() {
			EpicHygieneData data = EpicHygieneData.builder().build();

			parser.applyMetrics(data, parser.parseTrendValueList(List.of(dto("Brand New Metric", 42d))));

			assertEquals(1, data.getMetrics().size());
			assertNull(data.getTotalActiveEpics());
			assertNull(data.getConstructionReadyEpics());
			assertNull(data.getAtRiskEpics());
			assertNull(data.getAvgReadinessScore());
		}

		@Test
		@DisplayName("Should leave typed fields untouched when a known label has no value")
		void applyMetrics_KnownLabelWithoutValue_LeavesFieldNull() {
			EpicHygieneData data = EpicHygieneData.builder().build();

			parser.applyMetrics(
					data,
					parser.parseTrendValueList(
							List.of(dto(EpicHygieneTrendValueParser.LABEL_TOTAL_ACTIVE_EPICS, null))));

			assertNull(data.getTotalActiveEpics());
		}

		@Test
		@DisplayName("Should store an empty metric list when metrics are null")
		void applyMetrics_NullMetrics_StoresEmptyList() {
			EpicHygieneData data = EpicHygieneData.builder().build();

			parser.applyMetrics(data, null);

			assertNotNull(data.getMetrics());
			assertTrue(data.getMetrics().isEmpty());
		}

		@Test
		@DisplayName("Should do nothing when the target document is null")
		void applyMetrics_NullTarget_DoesNotThrow() {
			List<EpicHygieneMetric> metrics = parser.parseTrendValueList(fullTrendValueList());

			assertDoesNotThrow(() -> parser.applyMetrics(null, metrics));
		}
	}
}
