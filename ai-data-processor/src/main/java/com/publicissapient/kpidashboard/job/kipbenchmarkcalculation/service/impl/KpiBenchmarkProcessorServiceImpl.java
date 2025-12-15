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

package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser.KpiParserStrategy;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.BenchmarkCalculation;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiBenchmarkProcessorService;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class KpiBenchmarkProcessorServiceImpl implements KpiBenchmarkProcessorService {

	private final KpiParserStrategy kpiParserStrategy;
	private final KnowHOWClient knowHOWClient;
	private final ProjectBasicConfigRepository projectBasicConfigRepository;

	public KpiBenchmarkProcessorServiceImpl(
			KpiParserStrategy kpiParserStrategy,
			KnowHOWClient knowHOWClient,
			ProjectBasicConfigRepository projectBasicConfigRepository) {
		this.kpiParserStrategy = kpiParserStrategy;
		this.knowHOWClient = knowHOWClient;
		this.projectBasicConfigRepository = projectBasicConfigRepository;
	}

	@Override
	public List<KpiBenchmarkValues> getKpiWiseBenchmarkValues(List<KpiDataDTO> kpiDataDTOList) {
		List<KpiElement> kpiElementList = fetchKpiElements(kpiDataDTOList);
		Map<String, String> kpiFilterMap = createKpiFilterMap(kpiDataDTOList);

		return kpiElementList.stream()
				.collect(Collectors.groupingBy(KpiElement::getKpiId))
				.entrySet()
				.stream()
				.map(entry -> createKpiBenchmarkValues(entry.getKey(), entry.getValue(), kpiFilterMap))
				.toList();
	}

	private List<KpiElement> fetchKpiElements(List<KpiDataDTO> kpiDataDTOList) {
		return projectBasicConfigRepository.findAll().stream()
				.map(config -> constructKpiRequest(kpiDataDTOList, config.getProjectNodeId()))
				.map(Collections::singletonList)
				.flatMap(request -> knowHOWClient.getKpiIntegrationValues(request).stream())
				.toList();
	}

	private Map<String, String> createKpiFilterMap(List<KpiDataDTO> kpiDataDTOList) {
		return kpiDataDTOList.stream()
				.collect(
						Collectors.toMap(
								KpiDataDTO::kpiId, dto -> dto.kpiFilter() != null ? dto.kpiFilter() : "default"));
	}

	private KpiBenchmarkValues createKpiBenchmarkValues(
			String kpiId, List<KpiElement> kpiElements, Map<String, String> kpiFilterMap) {
		try {
			log.debug("Calculating Benchmark for KPI ID: {}", kpiId);
			Map<String, List<Double>> allDataPoints =
					kpiElements.stream()
							.map(element -> processKpiData(element, kpiFilterMap.get(kpiId)))
							.flatMap(map -> map.entrySet().stream())
							.collect(
									Collectors.groupingBy(
											Map.Entry::getKey,
											Collectors.flatMapping(
													entry -> entry.getValue().stream(), Collectors.toList())));

			List<BenchmarkPercentiles> filterWiseBenchmark =
					allDataPoints.entrySet().stream()
							.map(entry -> createBenchmarkPercentiles(entry.getValue(), entry.getKey()))
							.toList();

			return KpiBenchmarkValues.builder()
					.kpiId(kpiId)
					.filterWiseBenchmarkValues(filterWiseBenchmark)
					.lastUpdatedTimestamp(System.currentTimeMillis())
					.build();
		} catch (ClassCastException e) {
			log.error("Error processing KPI data for KPI ID {}: {}", kpiId, e.getMessage(), e);
			return null;
		}
	}

	private Map<String, List<Double>> processKpiData(KpiElement kpiElement, String kpiFilter) {
		return Optional.ofNullable(kpiElement.getTrendValueList())
				.filter(List.class::isInstance)
				.map(
						trendValueList ->
								kpiParserStrategy.getParser(kpiFilter).getKpiDataPoints((List<?>) trendValueList))
				.orElse(Collections.emptyMap());
	}

	private BenchmarkPercentiles createBenchmarkPercentiles(List<Double> values, String filter) {
		return BenchmarkPercentiles.builder()
				.filter(filter)
				.seventyPercentile(BenchmarkCalculation.percentile(values, 70))
				.eightyPercentile(BenchmarkCalculation.percentile(values, 80))
				.nintyPercentile(BenchmarkCalculation.percentile(values, 90))
				.build();
	}

	private KpiRequest constructKpiRequest(List<KpiDataDTO> kpiDataDTOList, String projectNodeId) {
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put(
				CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList(projectNodeId));
		return KpiRequest.builder()
				.kpiIdList(kpiDataDTOList.stream().map(KpiDataDTO::kpiId).toList())
				.label(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)
				.ids(new String[] {projectNodeId})
				.selectedMap(selectedMap)
				.level(5)
				.build();
	}
}
