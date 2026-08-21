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

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.exception.EpicHygieneKpiUnavailableException;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin, retrying facade in front of the KnowHOW Epic Hygiene KPI (kpi312).
 *
 * <p>The KPI is AI backed, so transient failures (gateway timeouts, 5xx, throttling) are expected.
 * Every project is therefore attempted {@code calculationConfig.maxRetryAttempts} times with a
 * linearly growing back-off. An empty payload is treated as a failure as well, because publishing
 * an empty hygiene snapshot would be indistinguishable from "project has no Epics".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpicHygieneKpiClientService {

	private final KnowHOWClient knowHOWClient;
	private final EpicHygieneCalculationJobConfig jobConfig;

	/**
	 * Fetches the Epic Hygiene KPI for one project, retrying transient failures.
	 *
	 * @param projectInput project whose KPI has to be evaluated
	 * @return the non-empty list of KPI elements returned by KnowHOW API
	 * @throws EpicHygieneKpiUnavailableException when every attempt failed
	 */
	public List<KpiElement> fetchEpicHygieneKpi(ProjectInputDTO projectInput) {
		if (projectInput == null) {
			throw new IllegalArgumentException("projectInput must not be null");
		}

		EpicHygieneJobConfig calculationConfig = jobConfig.getCalculationConfig();
		int maxAttempts = Math.max(1, calculationConfig.getMaxRetryAttempts());
		long backoffMillis = Math.max(0L, calculationConfig.getRetryBackoffMillis());

		KpiRequest kpiRequest = buildKpiRequest(projectInput, calculationConfig.getKpiId());
		Exception lastFailure = null;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				List<KpiElement> kpiElements =
						knowHOWClient.getKpiIntegrationValuesSync(List.of(kpiRequest));

				if (CollectionUtils.isEmpty(kpiElements)) {
					throw new EpicHygieneKpiUnavailableException(
							String.format(
									"KnowHOW API returned no KPI element for project %s",
									projectInput.basicProjectConfigId()));
				}

				log.info(
						"{} Fetched {} KPI element(s) for project {} on attempt {}/{}",
						JobConstants.LOG_PREFIX_EPIC_HYGIENE,
						kpiElements.size(),
						projectInput.basicProjectConfigId(),
						attempt,
						maxAttempts);
				return kpiElements;
			} catch (Exception ex) {
				lastFailure = ex;
				log.warn(
						"{} Attempt {}/{} failed for project {}: {}",
						JobConstants.LOG_PREFIX_EPIC_HYGIENE,
						attempt,
						maxAttempts,
						projectInput.basicProjectConfigId(),
						ex.getMessage());

				if (attempt < maxAttempts && !sleepBeforeNextAttempt(backoffMillis * attempt)) {
					break;
				}
			}
		}

		throw new EpicHygieneKpiUnavailableException(
				String.format(
						"Epic Hygiene KPI unavailable for project %s after %s attempt(s): %s",
						projectInput.basicProjectConfigId(), maxAttempts, describe(lastFailure)),
				lastFailure);
	}

	private String describe(Exception failure) {
		return failure == null ? "unknown reason" : failure.getMessage();
	}

	private KpiRequest buildKpiRequest(ProjectInputDTO projectInput, String kpiId) {
		return KpiRequest.builder()
				.kpiIdList(List.of(kpiId))
				.selectedMap(
						Map.of(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId())))
				.ids(new String[] {projectInput.nodeId()})
				.level(projectInput.hierarchyLevel())
				.label(projectInput.hierarchyLevelId())
				.build();
	}

	/**
	 * @return {@code false} when the thread was interrupted and retrying must stop
	 */
	private boolean sleepBeforeNextAttempt(long millis) {
		if (millis <= 0) {
			return true;
		}
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			log.warn(
					"{} Retry back-off interrupted — aborting remaining attempts",
					JobConstants.LOG_PREFIX_EPIC_HYGIENE);
			return false;
		}
	}
}
