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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.impl;

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
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.parser.KpiParserStrategy;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.BenchmarkCalculation;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiBenchmarkProcessorService;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of KpiBenchmarkProcessorService for calculating KPI benchmarks. Processes KPI data
 * from all projects to compute statistical benchmarks using percentile calculations across
 * different filter configurations.
 *
 * @author kunkambl
 */
@Service
@Slf4j
public class KpiBenchmarkProcessorServiceImpl implements KpiBenchmarkProcessorService {

	private final KpiParserStrategy kpiParserStrategy;
	private final KnowHOWClient knowHOWClient;
	private final ProjectBasicConfigRepository projectBasicConfigRepository;

	/**
	 * Constructs the benchmark processor service with required dependencies.
	 *
	 * @param kpiParserStrategy strategy for selecting appropriate KPI data parsers
	 * @param knowHOWClient client for fetching KPI data from the main application
	 * @param projectBasicConfigRepository repository for accessing project configurations
	 */
	public KpiBenchmarkProcessorServiceImpl(
			KpiParserStrategy kpiParserStrategy,
			KnowHOWClient knowHOWClient,
			ProjectBasicConfigRepository projectBasicConfigRepository) {
		this.kpiParserStrategy = kpiParserStrategy;
		this.knowHOWClient = knowHOWClient;
		this.projectBasicConfigRepository = projectBasicConfigRepository;
	}

	/** {@inheritDoc} */
	@Override
	public KpiBenchmarkValues getKpiWiseBenchmarkValues(KpiDataDTO kpiDataDTO) {
		List<KpiElement> kpiElementList = fetchKpiElements(kpiDataDTO);
		String kpiFilter = kpiDataDTO.kpiFilter() != null ? kpiDataDTO.kpiFilter() : "default";

		return createKpiBenchmarkValues(kpiDataDTO.kpiId(), kpiElementList, kpiFilter);
	}

	/**
	 * Fetches KPI elements from all projects for the specified KPI.
	 *
	 * @param kpiDataDTO KPI data to fetch
	 * @return list of KPI elements from all projects
	 */
	private List<KpiElement> fetchKpiElements(KpiDataDTO kpiDataDTO) {
		return projectBasicConfigRepository.findAll().stream()
				.map(config -> constructKpiRequest(kpiDataDTO, config.getProjectNodeId()))
				.map(Collections::singletonList)
				.flatMap(request -> knowHOWClient.getKpiIntegrationValues(request).stream())
				.toList();
	}



	/**
	 * Creates benchmark values for a specific KPI from collected data.
	 *
	 * @param kpiId the KPI identifier
	 * @param kpiElements list of KPI elements containing data
	 * @param kpiFilter the filter type for the KPI
	 * @return calculated benchmark values for the KPI
	 */
	private KpiBenchmarkValues createKpiBenchmarkValues(
			String kpiId, List<KpiElement> kpiElements, String kpiFilter) {
		try {
			log.debug("Calculating Benchmark for KPI ID: {}", kpiId);
			Map<String, List<Double>> allDataPoints =
					kpiElements.stream()
							.map(element -> processKpiData(element, kpiFilter))
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

	/**
	 * Processes KPI element data using the appropriate parser.
	 *
	 * @param kpiElement the KPI element to process
	 * @param kpiFilter the filter type for parser selection
	 * @return map of extracted data points
	 */
	private Map<String, List<Double>> processKpiData(KpiElement kpiElement, String kpiFilter) {
		return Optional.ofNullable(kpiElement.getTrendValueList())
				.filter(List.class::isInstance)
				.map(
						trendValueList ->
								kpiParserStrategy.getParser(kpiFilter).getKpiDataPoints((List<?>) trendValueList))
				.orElse(Collections.emptyMap());
	}

	/**
	 * Creates benchmark percentiles from a list of values.
	 *
	 * @param values list of numeric values
	 * @param filter the filter identifier
	 * @return calculated benchmark percentiles
	 */
	private BenchmarkPercentiles createBenchmarkPercentiles(List<Double> values, String filter) {
		return BenchmarkPercentiles.builder()
				.filter(filter)
				.seventyPercentile(BenchmarkCalculation.percentile(values, 70))
				.eightyPercentile(BenchmarkCalculation.percentile(values, 80))
				.nintyPercentile(BenchmarkCalculation.percentile(values, 90))
				.build();
	}

	/**
	 * Constructs a KPI request for fetching data from a specific project.
	 *
	 * @param kpiDataDTO KPI data to request
	 * @param projectNodeId the project node identifier
	 * @return constructed KPI request
	 */
	private KpiRequest constructKpiRequest(KpiDataDTO kpiDataDTO, String projectNodeId) {
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put(
				CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList(projectNodeId));
		return KpiRequest.builder()
				.kpiIdList(Collections.singletonList(kpiDataDTO.kpiId()))
				.label(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)
				.ids(new String[] {projectNodeId})
				.selectedMap(selectedMap)
				.level(5)
				.build();
	}
}
