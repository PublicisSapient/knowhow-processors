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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ProjectRelease;
import com.publicissapient.kpidashboard.common.model.application.ProjectVersion;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectReleaseRepo;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelServiceImpl;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.config.KpiBenchmarkCalculationConfig;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.parser.KpiParserStrategy;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;
import com.publicissapient.kpidashboard.utils.NumberUtils;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class KpiBenchmarkCalculationService {

	private final KpiParserStrategy kpiParserStrategy;
	private final KnowHOWClient knowHOWClient;
	private final ProjectBasicConfigRepository projectBasicConfigRepository;
	private final HierarchyLevelServiceImpl hierarchyLevelServiceImpl;
	private final SprintRepository sprintRepository;
	private final ProjectReleaseRepo projectReleaseRepo;
	private final KpiBenchmarkCalculationConfig kpiBenchmarkCalculationConfig;

	/**
	 * Calculates benchmark values for a single KPI. Processes KPI data across all projects to compute
	 * 70th, 80th, and 90th percentiles.
	 *
	 * @param kpiDataDTO KPI data transfer object to process
	 * @return calculated benchmark values for the KPI
	 */
	public KpiBenchmarkValues getKpiWiseBenchmarkValues(KpiDataDTO kpiDataDTO) {

		List<KpiElement> kpiElementList = fetchKpiElements(kpiDataDTO);
		String kpiFilter = kpiDataDTO.kpiFilter() != null ? kpiDataDTO.kpiFilter() : "";

		return createKpiBenchmarkValues(
				kpiDataDTO.kpiId(),
				kpiElementList,
				kpiFilter + "_" + kpiDataDTO.chartType(),
				kpiDataDTO.isPositiveTrend());
	}

	/**
	 * Fetches KPI elements from all projects for the specified KPI.
	 *
	 * @param kpiDataDTO KPI data to fetch
	 * @return list of KPI elements from all projects
	 */
	private synchronized List<KpiElement> fetchKpiElements(KpiDataDTO kpiDataDTO) {
		return projectBasicConfigRepository
				.findByKanbanAndProjectOnHold(kpiDataDTO.kanban(), false)
				.stream()
				.map(
						config -> {
							if (kpiDataDTO.kanban() || "Developer".equalsIgnoreCase(kpiDataDTO.kpiCategory()))
								return constructKanbanKpiRequest(kpiDataDTO, config.getProjectNodeId());
							else
								return constructKpiRequest(kpiDataDTO, config.getProjectNodeId(), config.getId());
						})
				.filter(Objects::nonNull)
				.map(Collections::singletonList)
				.flatMap(
						request -> {
							try {
								List<KpiElement> result;
								if (kpiDataDTO.kanban())
									result = knowHOWClient.getKpiIntegrationValuesKanbanSync(request);
								else result = knowHOWClient.getKpiIntegrationValuesSync(request);
								return result != null ? result.stream() : java.util.stream.Stream.empty();
							} catch (Exception ex) {
								log.warn(
										"Failed to fetch KPI data for KPI {}: {}", kpiDataDTO.kpiId(), ex.getMessage());
								return java.util.stream.Stream.empty();
							}
						})
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
			String kpiId, List<KpiElement> kpiElements, String kpiFilter, boolean isPositiveTrend) {
		try {
			log.debug(
					"{} Calculating Benchmark for KPI ID: {}",
					JobConstants.LOG_PREFIX_KPI_BENCHMARK_CALCULATION,
					kpiId);
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
							.map(
									entry ->
											createBenchmarkPercentiles(entry.getValue(), entry.getKey(), isPositiveTrend))
							.toList();

			log.info(
					"{} Generated Benchmark for KPI ID: {} with count {}",
					JobConstants.LOG_PREFIX_KPI_BENCHMARK_CALCULATION,
					kpiId,
					filterWiseBenchmark.size());
			return KpiBenchmarkValues.builder()
					.kpiId(kpiId)
					.filterWiseBenchmarkValues(filterWiseBenchmark)
					.calculationDate(Instant.now())
					.build();
		} catch (ClassCastException e) {
			log.error(
					"{} Error processing KPI data for KPI ID {}: {}",
					JobConstants.LOG_PREFIX_KPI_BENCHMARK_CALCULATION,
					kpiId,
					e.getMessage(),
					e);
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
	private BenchmarkPercentiles createBenchmarkPercentiles(
			List<Double> values, String filter, boolean isPositiveTrend) {
		return BenchmarkPercentiles.builder()
				.filter(filter)
				.seventyPercentile(NumberUtils.percentile(values, 70, isPositiveTrend))
				.eightyPercentile(NumberUtils.percentile(values, 80, isPositiveTrend))
				.nintyPercentile(NumberUtils.percentile(values, 90, isPositiveTrend))
				.build();
	}

	/**
	 * Constructs a KPI request for fetching data from a specific project.
	 *
	 * @param kpiDataDTO KPI data to request
	 * @param projectNodeId the project node identifier
	 * @return constructed KPI request
	 */
	private KpiRequest constructKpiRequest(
			KpiDataDTO kpiDataDTO, String projectNodeId, ObjectId basicProjectConfigId) {
		Map<String, List<String>> selectedMap = new HashMap<>();
		String category =
				kpiDataDTO.kpiCategory() != null ? kpiDataDTO.kpiCategory().toLowerCase() : "";

		int hierarchyLevel;
		String hierarchyLabel;
		String[] id = new String[] {projectNodeId};

		switch (category) {
			case "iteration" -> {
				hierarchyLevel = hierarchyLevelServiceImpl.getSprintHierarchyLevel().getLevel();
				hierarchyLabel = hierarchyLevelServiceImpl.getSprintHierarchyLevel().getHierarchyLevelId();
				SprintDetails latestActiveSprint =
						sprintRepository.findTopByBasicProjectConfigIdAndState(
								basicProjectConfigId, SprintDetails.SPRINT_STATE_ACTIVE);
				if (latestActiveSprint == null) return null;
				id = new String[] {latestActiveSprint.getOriginalSprintId() + "_" + projectNodeId};
				selectedMap.put(
						CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList(projectNodeId));
				selectedMap.put(
						CommonConstant.HIERARCHY_LEVEL_ID_SPRINT,
						Collections.singletonList(
								latestActiveSprint.getOriginalSprintId() + "_" + projectNodeId));
			}
			case "release" -> {
				hierarchyLevel = hierarchyLevelServiceImpl.getReleaseHierarchyLevel().getLevel();
				hierarchyLabel = hierarchyLevelServiceImpl.getReleaseHierarchyLevel().getHierarchyLevelId();
				ProjectRelease projectRelease = projectReleaseRepo.findByConfigId(basicProjectConfigId);
				if (projectRelease == null) return null;

				Optional<ProjectVersion> activeVersion =
						projectRelease.getListProjectVersion().stream()
								.filter(ProjectVersion::isReleased)
								.findFirst();
				if (activeVersion.isEmpty()) return null;

				selectedMap.put(
						CommonConstant.HIERARCHY_LEVEL_ID_RELEASE,
						Collections.singletonList(activeVersion.get().getId() + "_" + projectNodeId));
				id = new String[] {activeVersion.get().getId() + "_" + projectNodeId};
			}
			default -> {
				hierarchyLevel = hierarchyLevelServiceImpl.getProjectHierarchyLevel().getLevel();
				hierarchyLabel = hierarchyLevelServiceImpl.getProjectHierarchyLevel().getHierarchyLevelId();
				selectedMap.put(
						CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList(projectNodeId));
			}
		}

		return KpiRequest.builder()
				.kpiIdList(Collections.singletonList(kpiDataDTO.kpiId()))
				.label(hierarchyLabel)
				.ids(id)
				.selectedMap(selectedMap)
				.level(hierarchyLevel)
				.build();
	}

	private KpiRequest constructKanbanKpiRequest(KpiDataDTO kpiDataDTO, String projectNodeId) {
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put(
				CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList(projectNodeId));
		selectedMap.put(CommonConstant.DATE, Collections.singletonList(CommonConstant.WEEK));
		return KpiRequest.builder()
				.kpiIdList(Collections.singletonList(kpiDataDTO.kpiId()))
				.label(hierarchyLevelServiceImpl.getProjectHierarchyLevel().getHierarchyLevelId())
				.ids(
						new String[] {
							String.valueOf(
									kpiBenchmarkCalculationConfig
											.getCalculationConfig()
											.getKanbanDataPoints()
											.getCount())
						})
				.selectedMap(selectedMap)
				.level(hierarchyLevelServiceImpl.getProjectHierarchyLevel().getLevel())
				.build();
	}
}
