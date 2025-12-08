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
import com.publicissapient.kpidashboard.job.constant.AiDataProcessorConstants;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for extracting and transforming KPI data from KnowHOW API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiDataExtractionService {
	
	private final KnowHOWClient knowHOWClient;
	private final RecommendationCalculationConfig recommendationCalculationConfig;
	
	private static final List<String> FILTER_LIST = Arrays.asList(
		"Final Scope (Story Points)", "Average Coverage", "Story Points", "Overall");
	
	/**
	 * Fetches and extracts KPI data for the given project.
	 * 
	 * @param projectInput the project input containing hierarchy information
	 * @return map of KPI name to formatted KPI data prompts
	 * @throws Exception if KPI data fetching or extraction fails
	 */
	public Map<String, Object> fetchKpiDataForProject(ProjectInputDTO projectInput) {
		try {
			log.debug("{} Fetching KPI data for project: {}", 
				AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInput.nodeId());
			
			// Construct KPI requests
			List<KpiRequest> kpiRequests = constructKpiRequests(projectInput);
			
			// Fetch from KnowHOW API
			List<KpiElement> kpiElements = knowHOWClient.getKpiIntegrationValues(kpiRequests);
			
			// Extract and format KPI data
			Map<String, Object> kpiData = extractKpiData(kpiElements);
			
			log.debug("{} Successfully fetched {} KPIs for project: {}", 
				AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, kpiData.size(), projectInput.nodeId());
			return kpiData;
			
		} catch (Exception e) {
			log.error("{} Error fetching KPI data for project {}: {}", 
				AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectInput.nodeId(), e.getMessage(), e);
			throw e;
		}
	}
	
	/**
	 * Constructs KPI requests for the given project.
	 *
	 * @param projectInput the project input containing hierarchy information
	 * @return list of KPI requests ready for API calls
	 */
	private List<KpiRequest> constructKpiRequests(ProjectInputDTO projectInput) {
		KpiRequest kpiRequest = KpiRequest.builder()
			.kpiIdList(recommendationCalculationConfig.getCalculationConfig().getKpiList())
			.selectedMap(Map.of(
				CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId()),
				CommonConstant.HIERARCHY_LEVEL_ID_SPRINT, new ArrayList<>()
			))
			.ids(new String[]{projectInput.nodeId()})
			.level(projectInput.hierarchyLevel())
			.label(projectInput.hierarchyLevelId())
			.build();
			
		return List.of(kpiRequest);
	}
	
	/**
	 * Extracts and formats KPI data from KPI elements.
	 *
	 * @param kpiElements the list of KPI elements from KnowHOW API
	 * @return map where key is KPI name and value is list of formatted data prompts
	 */
	private Map<String, Object> extractKpiData(List<KpiElement> kpiElements) {
		Map<String, Object> kpiDataMap = new HashMap<>();

		kpiElements.forEach(kpiElement -> {
			List<String> kpiDataPromptList = new ArrayList<>();
			List<?> trendValueList = (List<?>) kpiElement.getTrendValueList();

			if (CollectionUtils.isNotEmpty(trendValueList)) {
				DataCount dataCount = extractDataCount(trendValueList);
				
				if (dataCount != null && dataCount.getValue() instanceof List) {
					formatDataCountItems(dataCount, kpiDataPromptList);
				}
			}
			kpiDataMap.put(kpiElement.getKpiName(), kpiDataPromptList);
		});

		return kpiDataMap;
	}
	
	/**
	 * Extracts relevant DataCount from trend value list based on filters.
	 */
	@SuppressWarnings("unchecked")
	private DataCount extractDataCount(List<?> trendValueList) {
		if (CollectionUtils.isEmpty(trendValueList)) {
			return null;
		}

		return trendValueList.get(0) instanceof DataCountGroup ? ((List<DataCountGroup>) trendValueList).stream()
				.filter(trend -> FILTER_LIST.contains(trend.getFilter())
						|| (FILTER_LIST.contains(trend.getFilter1()) && FILTER_LIST.contains(trend.getFilter2())))
				.map(DataCountGroup::getValue).flatMap(List::stream).findFirst().orElse(null)
				: ((List<DataCount>) trendValueList).get(0);
	}
	
	/**
	 * Formats DataCount items into KpiDataPrompt objects with JSON string
	 * representation.
	 */
	@SuppressWarnings("unchecked")
	private void formatDataCountItems(DataCount dataCount, List<String> kpiDataPromptList) {
		((List<DataCount>) dataCount.getValue()).forEach(dataCountItem -> {
			if (dataCountItem != null) {
				KpiDataPrompt kpiDataPrompt = new KpiDataPrompt();
				kpiDataPrompt.setData(dataCountItem.getData());
				kpiDataPrompt.setSProjectName(dataCountItem.getSProjectName());
				kpiDataPrompt.setSSprintName(dataCountItem.getsSprintName());
				kpiDataPrompt.setDate(dataCountItem.getDate());
				kpiDataPromptList.add(kpiDataPrompt.toString());
			}
		});
	}
}
