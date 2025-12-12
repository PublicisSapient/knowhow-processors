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

/**
 * Service responsible for extracting and transforming KPI data from KnowHOW
 * API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiDataExtractionService {

	private static final List<String> FILTER_LIST = Arrays.asList("Final Scope (Story Points)", "Average Coverage",
			"Story Points", "Overall");
	private final KnowHOWClient knowHOWClient;
	private final RecommendationCalculationConfig recommendationCalculationConfig;

	/**
	 * Fetches and extracts KPI data for the given project.
	 * 
	 * @param projectInput
	 *            the project input containing hierarchy information
	 * @return map of KPI name to formatted KPI data prompts
	 */
	public Map<String, Object> fetchKpiDataForProject(ProjectInputDTO projectInput) {
		try {
			log.debug("{} Fetching KPI data for project: {}", JobConstants.LOG_PREFIX_RECOMMENDATION,
					projectInput.nodeId());

			// Construct KPI requests
			List<KpiRequest> kpiRequests = constructKpiRequests(projectInput);

			// Fetch from KnowHOW API
			List<KpiElement> kpiElements = knowHOWClient.getKpiIntegrationValues(kpiRequests);

			// Validate KPI elements were received
			if (CollectionUtils.isEmpty(kpiElements)) {
				log.error(
						"{} No KPI elements received from KnowHOW API for project: {}. Failing recommendation calculation.",
						JobConstants.LOG_PREFIX_RECOMMENDATION, projectInput.nodeId());
				throw new IllegalStateException(
						"No KPI data received from KnowHOW API for project: " + projectInput.nodeId());
			}

			// Extract and format KPI data
			Map<String, Object> kpiData = extractKpiData(kpiElements);

			// Validate that extracted KPI data has meaningful content
			boolean hasData = kpiData.values().stream()
					.anyMatch(value -> value instanceof List && !((List<?>) value).isEmpty());

			if (!hasData) {
				log.error(
						"{} KPI data extraction resulted in empty values for all KPIs for project: {}. Failing recommendation calculation.",
						JobConstants.LOG_PREFIX_RECOMMENDATION, projectInput.nodeId());
				throw new IllegalStateException(
						"No meaningful KPI data available for project: " + projectInput.nodeId());
			}

			log.debug("{} Successfully fetched {} KPIs for project: {}", JobConstants.LOG_PREFIX_RECOMMENDATION,
					kpiData.size(), projectInput.nodeId());
			return kpiData;

		} catch (Exception e) {
			log.error("{} Error fetching KPI data for project {}: {}", JobConstants.LOG_PREFIX_RECOMMENDATION,
					projectInput.nodeId(), e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Constructs KPI requests for the given project.
	 *
	 * @param projectInput
	 *            the project input containing hierarchy information
	 * @return list of KPI requests ready for API calls
	 */
	private List<KpiRequest> constructKpiRequests(ProjectInputDTO projectInput) {
		KpiRequest kpiRequest = KpiRequest.builder()
				.kpiIdList(recommendationCalculationConfig.getCalculationConfig().getKpiList())
				.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId())))
				.ids(new String[] { projectInput.nodeId() }).level(projectInput.hierarchyLevel())
				.label(projectInput.hierarchyLevelId()).build();

		return List.of(kpiRequest);
	}

	/**
	 * Extracts and formats KPI data from KPI elements.
	 *
	 * @param kpiElements
	 *            the list of KPI elements from KnowHOW API
	 * @return map where key is KPI name and value is list of formatted data prompts
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> extractKpiData(List<KpiElement> kpiElements) {
		Map<String, Object> kpiDataMap = new HashMap<>();

		kpiElements.forEach(kpiElement -> {
			List<String> kpiDataPromptList = new ArrayList<>();
			Object trendValueObj = kpiElement.getTrendValueList();

			// Handle both List and non-List types
			if (trendValueObj instanceof List<?> trendValueList && CollectionUtils.isNotEmpty(trendValueList)) {
				DataCount dataCount = trendValueList.get(0) instanceof DataCountGroup
						? ((List<DataCountGroup>) trendValueList).stream().filter(this::matchesFilterCriteria)
								.map(DataCountGroup::getValue).flatMap(List::stream).findFirst().orElse(null)
						: ((List<DataCount>) trendValueList).get(0);

				if (dataCount != null && dataCount.getValue() instanceof List) {
					((List<DataCount>) dataCount.getValue()).forEach(dataCountItem -> {
						KpiDataPrompt kpiDataPrompt = new KpiDataPrompt();
						kpiDataPrompt.setData(dataCountItem.getData());
						kpiDataPrompt.setSProjectName(dataCountItem.getSProjectName());
						kpiDataPrompt.setSSprintName(dataCountItem.getsSprintName());
						kpiDataPrompt.setDate(dataCountItem.getDate());
						kpiDataPromptList.add(kpiDataPrompt.toString());
					});
				}
			} else if (trendValueObj != null) {
				log.debug("{} Skipping non-list trendValueList for KPI {}: {} (type: {})",
						JobConstants.LOG_PREFIX_RECOMMENDATION, kpiElement.getKpiId(),
						kpiElement.getKpiName(), trendValueObj.getClass().getSimpleName());
			}
			kpiDataMap.put(kpiElement.getKpiName(), kpiDataPromptList);
		});

		return kpiDataMap;
	}	/**
	 * Checks if DataCountGroup matches filter criteria. Matches if either the main
	 * filter is in FILTER_LIST, or both filter1 and filter2 are in FILTER_LIST.
	 * 
	 * @param trend
	 *            the DataCountGroup to check
	 * @return true if trend matches filter criteria
	 */
	private boolean matchesFilterCriteria(DataCountGroup trend) {
		return FILTER_LIST.contains(trend.getFilter())
				|| (FILTER_LIST.contains(trend.getFilter1()) && FILTER_LIST.contains(trend.getFilter2()));
	}
}
