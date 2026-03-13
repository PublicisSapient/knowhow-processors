/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.job.recommendationcalculation.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.KpiDataPrompt;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Service responsible for extracting and transforming KPI data from KnowHOW API. */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiDataExtractionService {

	private static final List<String> FILTER_LIST =
			Arrays.asList(
					"Final Scope (Story Points)",
					"Average Coverage",
					"Story Points",
					"Overall",
					"Story Count",
					"Total Defects");
	private final KnowHOWClient knowHOWClient;
	private final RecommendationCalculationConfig recommendationCalculationConfig;

	/**
	 * Fetches and extracts KPI data for the given project.
	 *
	 * @param projectInput the project input containing hierarchy information
	 * @return map with {@code Pair<kpiId, kpiName>} as key and formatted KPI data as value
	 */
	public Map<Pair<String, String>, Object> fetchKpiDataForProject(ProjectInputDTO projectInput) {
		try {
			log.info(
					"{} Fetching KPI data for project: {}",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					projectInput.basicProjectConfigId());

			// Construct KPI requests
			List<KpiRequest> kpiRequests = constructKpiRequests(projectInput);

			// Fetch from KnowHOW API
			List<KpiElement> kpiElements = knowHOWClient.getKpiIntegrationValuesSync(kpiRequests);

			// Validate KPI elements were received
			if (CollectionUtils.isEmpty(kpiElements)) {
				log.error(
						"{} No KPI elements received from KnowHOW API for project: {}. Failing recommendation calculation.",
						JobConstants.LOG_PREFIX_RECOMMENDATION,
						projectInput.basicProjectConfigId());
				throw new IllegalStateException(
						"No KPI data received from KnowHOW API for project: "
								+ projectInput.basicProjectConfigId());
			}

			// Extract and format KPI data
			Map<Pair<String, String>, Object> kpiData = extractKpiData(kpiElements);

			// Validate that extracted KPI data has meaningful content
			if (kpiData.isEmpty()) {
				log.error(
						"{} KPI data extraction resulted in empty map for project: {}. All KPIs had no data. Failing recommendation calculation.",
						JobConstants.LOG_PREFIX_RECOMMENDATION,
						projectInput.basicProjectConfigId());
				throw new IllegalStateException(
						"No meaningful KPI data available for project: " + projectInput.basicProjectConfigId());
			}

			log.info(
					"{} Successfully fetched {} KPIs with data for project: {}",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					kpiData.size(),
					projectInput.basicProjectConfigId());
			return kpiData;

		} catch (Exception e) {
			log.error(
					"{} Error fetching KPI data for project {}: {}",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					projectInput.basicProjectConfigId(),
					e.getMessage(),
					e);
			throw e;
		}
	}

	/**
	 * Filters and returns data for a single KPI using pre-built lookup map.
	 *
	 * @param allKpiDataMap map of all KPI data with {@code Pair<kpiId, kpiName>} as keys
	 * @param kpiIdToKeyMap pre-built map from kpiId to {@code Pair<kpiId, kpiName>} for O(1) lookup
	 * @param kpiId the KPI ID to filter for
	 * @return map containing single KPI data, or empty map if not found
	 */
	public Map<Pair<String, String>, Object> filterKpiDataWithIndex(
			Map<Pair<String, String>, Object> allKpiDataMap,
			Map<String, Pair<String, String>> kpiIdToKeyMap,
			String kpiId) {
		Map<Pair<String, String>, Object> singleKpiData = new HashMap<>();

		Pair<String, String> kpiKey = kpiIdToKeyMap.get(kpiId);
		if (kpiKey != null) {
			singleKpiData.put(kpiKey, allKpiDataMap.get(kpiKey));
		} else {
			log.debug(
					"{} KPI ID {} not found in extracted KPI data. Available KPI IDs: {}",
					JobConstants.LOG_PREFIX_RECOMMENDATION,
					kpiId,
					kpiIdToKeyMap.keySet());
		}

		return singleKpiData;
	}

	/**
	 * Constructs KPI requests for the given project.
	 *
	 * @param projectInput the project input containing hierarchy information
	 * @return list of KPI requests ready for API calls
	 */
	private List<KpiRequest> constructKpiRequests(ProjectInputDTO projectInput) {
		KpiRequest kpiRequest =
				KpiRequest.builder()
						.kpiIdList(recommendationCalculationConfig.getCalculationConfig().getKpiList())
						.selectedMap(
								Map.of(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId())))
						.ids(new String[] {projectInput.nodeId()})
						.level(projectInput.hierarchyLevel())
						.label(projectInput.hierarchyLevelId())
						.build();

		return List.of(kpiRequest);
	}

	/**
	 * Extracts and formats KPI data from KPI elements. Uses {@code Pair<kpiId, kpiName>} as map key
	 *
	 * @param kpiElements the list of KPI elements from KnowHOW API
	 * @return map where key is {@code Pair<kpiId, kpiName>} and value is list of formatted data
	 *     prompts
	 */
	@SuppressWarnings("unchecked")
	private Map<Pair<String, String>, Object> extractKpiData(List<KpiElement> kpiElements) {
		Map<Pair<String, String>, Object> kpiDataMap = new HashMap<>();

		kpiElements.forEach(
				kpiElement -> {
					List<String> kpiDataPromptList = new ArrayList<>();
					Object trendValueObj = kpiElement.getTrendValueList();

					// Handle both List and non-List types
					if (trendValueObj instanceof List<?> trendValueList
							&& CollectionUtils.isNotEmpty(trendValueList)) {
						DataCount dataCount =
								trendValueList.get(0) instanceof DataCountGroup
										? ((List<DataCountGroup>) trendValueList)
												.stream()
														.filter(this::matchesFilterCriteria)
														.map(DataCountGroup::getValue)
														.flatMap(List::stream)
														.findFirst()
														.orElse(null)
										: ((List<DataCount>) trendValueList).get(0);

						if (dataCount != null && dataCount.getValue() instanceof List) {
							((List<DataCount>) dataCount.getValue())
									.forEach(
											dataCountItem -> {
												KpiDataPrompt kpiDataPrompt = new KpiDataPrompt();
												kpiDataPrompt.setData(dataCountItem.getData());
												kpiDataPrompt.setSProjectName(dataCountItem.getSProjectName());
												kpiDataPrompt.setSSprintName(dataCountItem.getsSprintName());
												kpiDataPrompt.setDate(dataCountItem.getDate());
												kpiDataPromptList.add(kpiDataPrompt.toString());
											});
						}
					} else if (trendValueObj != null) {
						log.debug(
								"{} Skipping non-list trendValueList for KPI {}: {} (type: {})",
								JobConstants.LOG_PREFIX_RECOMMENDATION,
								kpiElement.getKpiId(),
								kpiElement.getKpiName(),
								trendValueObj.getClass().getSimpleName());
					}

					if (!kpiDataPromptList.isEmpty()) {
						kpiDataMap.put(
								Pair.of(kpiElement.getKpiId(), kpiElement.getKpiName()), kpiDataPromptList);
					} else {
						log.warn(
								"{} Skipping KPI {}: {} - no data available after extraction",
								JobConstants.LOG_PREFIX_RECOMMENDATION,
								kpiElement.getKpiId(),
								kpiElement.getKpiName());
					}
				});

		return kpiDataMap;
	}

	/**
	 * Checks if DataCountGroup matches filter criteria. Matches if either the main filter is in
	 * FILTER_LIST, or both filter1 and filter2 are in FILTER_LIST.
	 *
	 * @param trend the DataCountGroup to check
	 * @return true if trend matches filter criteria
	 */
	private boolean matchesFilterCriteria(DataCountGroup trend) {
		return FILTER_LIST.contains(trend.getFilter())
				|| (FILTER_LIST.contains(trend.getFilter1()) && FILTER_LIST.contains(trend.getFilter2()));
	}
}
