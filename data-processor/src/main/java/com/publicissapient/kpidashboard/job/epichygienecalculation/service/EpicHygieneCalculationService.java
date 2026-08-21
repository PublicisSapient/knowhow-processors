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

package com.publicissapient.kpidashboard.job.epichygienecalculation.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData.EpicHygieneMetric;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.exception.EpicHygieneKpiUnavailableException;
import com.publicissapient.kpidashboard.job.epichygienecalculation.parser.EpicHygieneTrendValueParser;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes the Epic Hygiene snapshot of a single project: calls the KPI (with retries), parses its
 * {@code trendValueList} and produces the document that has to be persisted.
 *
 * <p>When the KPI stays unavailable the behaviour depends on {@code
 * calculationConfig.fallbackEnabled}: either a clearly flagged neutral record is produced, or the
 * failure is propagated so the project is skipped and traced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpicHygieneCalculationService {

	private final EpicHygieneKpiClientService kpiClientService;
	private final EpicHygieneTrendValueParser trendValueParser;
	private final EpicHygieneCalculationJobConfig jobConfig;

	/**
	 * @param projectInput the project to evaluate
	 * @return the document to persist, never {@code null}
	 * @throws EpicHygieneKpiUnavailableException when the KPI failed and fallback is disabled
	 */
	public EpicHygieneData computeForProject(ProjectInputDTO projectInput) {
		Objects.requireNonNull(projectInput, "projectInput must not be null");

		String configuredKpiId = jobConfig.getCalculationConfig().getKpiId();
		try {
			List<KpiElement> kpiElements = kpiClientService.fetchEpicHygieneKpi(projectInput);
			KpiElement epicHygieneElement = selectEpicHygieneElement(kpiElements, configuredKpiId);

			if (epicHygieneElement == null) {
				return handleFailure(
						projectInput,
						String.format("Response did not contain KPI '%s'", configuredKpiId),
						null);
			}

			List<EpicHygieneMetric> metrics =
					trendValueParser.parseTrendValueList(epicHygieneElement.getTrendValueList());

			if (CollectionUtils.isEmpty(metrics)) {
				return handleFailure(
						projectInput,
						String.format(
								"KPI '%s' returned an empty or unreadable trendValueList", configuredKpiId),
						null);
			}

			EpicHygieneData epicHygieneData = newDocument(projectInput, epicHygieneElement);
			trendValueParser.applyMetrics(epicHygieneData, metrics);

			log.info(
					"{} Parsed {} metric(s) for project {}",
					JobConstants.LOG_PREFIX_EPIC_HYGIENE,
					metrics.size(),
					projectInput.basicProjectConfigId());
			return epicHygieneData;
		} catch (EpicHygieneKpiUnavailableException ex) {
			return handleFailure(projectInput, ex.getMessage(), ex);
		}
	}

	private KpiElement selectEpicHygieneElement(List<KpiElement> kpiElements, String kpiId) {
		if (CollectionUtils.isEmpty(kpiElements)) {
			return null;
		}
		return kpiElements.stream()
				.filter(Objects::nonNull)
				.filter(kpiElement -> kpiId != null && kpiId.equalsIgnoreCase(kpiElement.getKpiId()))
				.findFirst()
				.orElse(null);
	}

	private EpicHygieneData handleFailure(
			ProjectInputDTO projectInput, String reason, EpicHygieneKpiUnavailableException cause) {
		if (!jobConfig.getCalculationConfig().isFallbackEnabled()) {
			throw cause == null
					? new EpicHygieneKpiUnavailableException(reason)
					: new EpicHygieneKpiUnavailableException(reason, cause);
		}

		log.warn(
				"{} Falling back for project {}: {}",
				JobConstants.LOG_PREFIX_EPIC_HYGIENE,
				projectInput.basicProjectConfigId(),
				reason);

		EpicHygieneData fallbackData = newDocument(projectInput, null);
		fallbackData.setFallback(true);
		fallbackData.setFailureReason(reason);
		fallbackData.setMetrics(List.of());
		return fallbackData;
	}

	private EpicHygieneData newDocument(ProjectInputDTO projectInput, KpiElement kpiElement) {
		return EpicHygieneData.builder()
				.basicProjectConfigId(projectInput.basicProjectConfigId())
				.projectNodeId(projectInput.nodeId())
				.projectName(projectInput.name())
				.kpiId(
						kpiElement == null || kpiElement.getKpiId() == null
								? jobConfig.getCalculationConfig().getKpiId()
								: kpiElement.getKpiId())
				.kpiName(kpiElement == null ? null : kpiElement.getKpiName())
				.calculationDate(Instant.now())
				.build();
	}
}
