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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.client.customapi.dto.IterationKpiDataDTO;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData.EpicHygieneMetric;
import com.publicissapient.kpidashboard.job.constant.JobConstants;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns the {@code trendValueList} published by the Epic Hygiene KPI into {@link EpicHygieneMetric}
 * entries and maps the well known labels onto the typed fields of {@link EpicHygieneData}.
 *
 * <p>The parser is deliberately shape tolerant: depending on how the payload was deserialized an
 * entry can be an {@link IterationKpiDataDTO} or a raw {@link Map}. Anything it does not understand
 * is skipped instead of failing the whole chunk.
 */
@Slf4j
@Component
public class EpicHygieneTrendValueParser {

	public static final String LABEL_TOTAL_ACTIVE_EPICS = "Total Active Epics";
	public static final String LABEL_CONSTRUCTION_READY = "Construction Ready";
	public static final String LABEL_AT_RISK_BLOCKED = "At Risk / Blocked";
	public static final String LABEL_AVG_READINESS_SCORE = "Avg Readiness Score";

	private static final String LABEL_KEY = "label";
	private static final String VALUE_KEY = "value";
	private static final String LABEL_INFO_KEY = "labelInfo";
	private static final String UNIT_KEY = "unit";

	/**
	 * Flattens the KPI payload into label/value pairs.
	 *
	 * @param trendValueList the raw {@code trendValueList} of the KPI element; may be {@code null}
	 * @return never {@code null}; empty when nothing usable could be extracted
	 */
	public List<EpicHygieneMetric> parseTrendValueList(Object trendValueList) {
		if (!(trendValueList instanceof List<?> entries) || entries.isEmpty()) {
			log.debug(
					"{} trendValueList is empty or not a list — nothing to parse",
					JobConstants.LOG_PREFIX_EPIC_HYGIENE);
			return new ArrayList<>();
		}

		List<EpicHygieneMetric> metrics = new ArrayList<>();
		for (Object entry : entries) {
			EpicHygieneMetric metric = toMetric(entry);
			if (metric != null && StringUtils.isNotBlank(metric.getLabel())) {
				metrics.add(metric);
			}
		}

		if (metrics.isEmpty()) {
			log.warn(
					"{} trendValueList held {} entry(ies) but none carried a usable label",
					JobConstants.LOG_PREFIX_EPIC_HYGIENE,
					entries.size());
		}
		return metrics;
	}

	/** Copies the parsed metrics onto the target document, resolving the well known labels. */
	public void applyMetrics(EpicHygieneData target, List<EpicHygieneMetric> metrics) {
		if (target == null) {
			return;
		}
		List<EpicHygieneMetric> safeMetrics = metrics == null ? new ArrayList<>() : metrics;
		target.setMetrics(safeMetrics);

		for (EpicHygieneMetric metric : safeMetrics) {
			String label = metric.getLabel();
			if (matches(label, LABEL_TOTAL_ACTIVE_EPICS)) {
				target.setTotalActiveEpics(toInteger(metric.getValue()));
			} else if (matches(label, LABEL_CONSTRUCTION_READY)) {
				target.setConstructionReadyEpics(toInteger(metric.getValue()));
			} else if (matches(label, LABEL_AT_RISK_BLOCKED)) {
				target.setAtRiskEpics(toInteger(metric.getValue()));
			} else if (matches(label, LABEL_AVG_READINESS_SCORE)) {
				target.setAvgReadinessScore(metric.getValue());
			} else {
				log.debug(
						"{} Unmapped Epic Hygiene metric '{}' kept in metrics only",
						JobConstants.LOG_PREFIX_EPIC_HYGIENE,
						label);
			}
		}
	}

	private EpicHygieneMetric toMetric(Object entry) {
		if (entry instanceof IterationKpiDataDTO dto) {
			return EpicHygieneMetric.builder()
					.label(trimToNull(dto.getLabel()))
					.value(dto.getValue())
					.labelInfo(trimToNull(dto.getLabelInfo()))
					.unit(trimToNull(dto.getUnit()))
					.build();
		}
		if (entry instanceof Map<?, ?> map) {
			Map<?, ?> safeMap = map instanceof LinkedHashMap ? map : new LinkedHashMap<>(map);
			return EpicHygieneMetric.builder()
					.label(toStringValue(safeMap.get(LABEL_KEY)))
					.value(toDouble(safeMap.get(VALUE_KEY)))
					.labelInfo(toStringValue(safeMap.get(LABEL_INFO_KEY)))
					.unit(toStringValue(safeMap.get(UNIT_KEY)))
					.build();
		}
		log.debug(
				"{} Skipping unsupported trendValueList entry of type {}",
				JobConstants.LOG_PREFIX_EPIC_HYGIENE,
				entry == null ? "null" : entry.getClass().getSimpleName());
		return null;
	}

	private boolean matches(String actualLabel, String expectedLabel) {
		String normalizedActual = normalize(actualLabel);
		return normalizedActual != null && normalizedActual.equals(normalize(expectedLabel));
	}

	/** Case-, spacing- and separator-insensitive so minor label drift does not break the mapping. */
	private String normalize(String label) {
		if (StringUtils.isBlank(label)) {
			return null;
		}
		return label.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	private Integer toInteger(Double value) {
		return value == null ? null : (int) Math.round(value);
	}

	private Double toDouble(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		String rawValue = String.valueOf(value).trim();
		if (rawValue.isEmpty()) {
			return null;
		}
		try {
			return Double.valueOf(rawValue);
		} catch (NumberFormatException numberFormatException) {
			log.debug(
					"{} Could not read '{}' as a number", JobConstants.LOG_PREFIX_EPIC_HYGIENE, rawValue);
			return null;
		}
	}

	private String toStringValue(Object value) {
		return value == null ? null : trimToNull(String.valueOf(value));
	}

	private String trimToNull(String value) {
		return StringUtils.isBlank(value) ? null : value.trim();
	}
}
