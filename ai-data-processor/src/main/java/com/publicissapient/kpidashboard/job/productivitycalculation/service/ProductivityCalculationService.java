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

package com.publicissapient.kpidashboard.job.productivitycalculation.service;

import static com.publicissapient.kpidashboard.job.productivitycalculation.config.CalculationConfig.CATEGORY_EFFICIENCY;
import static com.publicissapient.kpidashboard.job.productivitycalculation.config.CalculationConfig.CATEGORY_PRODUCTIVITY;
import static com.publicissapient.kpidashboard.job.productivitycalculation.config.CalculationConfig.CATEGORY_QUALITY;
import static com.publicissapient.kpidashboard.job.productivitycalculation.config.CalculationConfig.CATEGORY_SPEED;
import static com.publicissapient.kpidashboard.utils.NumberUtils.PERCENTAGE_MULTIPLIER;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.IssueKpiModalValue;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.CategoryScores;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.KPIData;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.Productivity;
import com.publicissapient.kpidashboard.common.repository.productivity.ProductivityRepository;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.ProductivityCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.shared.dto.SprintInputDTO;
import com.publicissapient.kpidashboard.job.shared.enums.KpiGranularity;
import com.publicissapient.kpidashboard.utils.NumberUtils;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductivityCalculationService {

	private static final double WEEK_WEIGHT = 1.0D;
	private static final double SPRINT_WEIGHT = 2.0D;

	private static final String KPI_ID_WASTAGE = "kpi131";
	private static final String KPI_ID_WORK_STATUS = "kpi128";

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	private static final class KPIConfiguration {
		private double weightInProductivityScoreCalculation;

		private String kpiId;
		private String kpiName;
		private String dataCountGroupFilterUsedForCalculation;
		private String dataCountGroupFilter1UsedForCalculation;
		private String dataCountGroupFilter2UsedForCalculation;

		private PositiveGainTrend positiveGainTrend;

		private KpiGranularity kpiGranularity;

		private enum PositiveGainTrend {
			ASCENDING, DESCENDING
		}
	}

	@Getter
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	private static final class KPIVariationCalculationData {
		private double dataPointGainWeightSumProduct;
		private double weightParts;

		private String kpiName;
		private String kpiId;
	}

	private final ProductivityRepository productivityRepository;

	private final KnowHOWClient knowHOWClient;

	private final ProductivityCalculationConfig productivityCalculationJobConfig;

	private Map<String, Map<String, KPIConfiguration>> categoryKpiIdConfigurationMap;

	@PostConstruct
	private void initializeConfiguration() {
		this.categoryKpiIdConfigurationMap = constructCategoryKpiIdConfigurationMap();
	}

	public void saveAll(List<Productivity> productivityList) {
		this.productivityRepository.saveAll(productivityList);
	}

	public Productivity calculateProductivityGainForProject(ProjectInputDTO projectInputDTO) {
		if (CollectionUtils
				.isNotEmpty(productivityCalculationJobConfig.getCalculationConfig().getConfigValidationErrors())) {
			throw new IllegalStateException(String.format("The following config validations errors occurred: %s",
					String.join(CommonConstant.COMMA,
							productivityCalculationJobConfig.getCalculationConfig().getConfigValidationErrors())));
		}
		List<KpiRequest> kpiRequests = constructKpiRequests(projectInputDTO);
		List<KpiElement> kpiElementList = processAllKpiRequests(kpiRequests);

		return calculateProductivityGain(projectInputDTO, kpiElementList);
	}

	private List<KpiElement> processAllKpiRequests(List<KpiRequest> kpiRequests) {
		return this.knowHOWClient.getKpiIntegrationValues(kpiRequests);
	}

	private Map<String, List<KPIVariationCalculationData>> constructCategoryBasedKPIVariationCalculationDataMap(
			Map<String, List<KpiElement>> kpiIdKpiElementsMap, ProjectInputDTO projectInputDTO) {
		Map<String, List<KPIVariationCalculationData>> categoryBasedKPIVariationCalculationDataMap = new HashMap<>();
		for (String kpiCategory : productivityCalculationJobConfig.getCalculationConfig()
				.getAllConfiguredCategories()) {
			categoryBasedKPIVariationCalculationDataMap.put(kpiCategory,
					constructGainTrendCalculationDataForAllKPIsInCategory(kpiIdKpiElementsMap, projectInputDTO,
							kpiCategory));
		}
		return categoryBasedKPIVariationCalculationDataMap;
	}

	private Productivity calculateProductivityGain(ProjectInputDTO projectInputDTO,
			List<KpiElement> kpisFromAllCategories) {
		Map<String, List<KpiElement>> kpiIdKpiElementsMap = kpisFromAllCategories.stream()
				.collect(Collectors.groupingBy(KpiElement::getKpiId));

		Map<String, List<KPIVariationCalculationData>> categoryBasedKPIVariationCalculationData = constructCategoryBasedKPIVariationCalculationDataMap(
				kpiIdKpiElementsMap, projectInputDTO);

		if (categoryBasedKPIVariationCalculationData.values().stream().allMatch(CollectionUtils::isEmpty)) {
			log.info("No KPI data for productivity calculation could be found for project with nodeId {} and name {}",
					projectInputDTO.nodeId(), projectInputDTO.name());
			// Returning null will ensure that the current project is skipped from database
			// insertion
			return null;
		}

		Productivity productivity = new Productivity();
		productivity.setHierarchyEntityName(projectInputDTO.name());
		productivity.setHierarchyEntityNodeId(projectInputDTO.nodeId());
		productivity.setHierarchyLevelId(projectInputDTO.hierarchyLevelId());
		productivity.setHierarchyLevel(projectInputDTO.hierarchyLevel());
		productivity.setCalculationDate(Instant.now());

		List<KPIData> kpiDataList = constructKPIDataAndTrendsUsedForProductivityCalculation(
				categoryBasedKPIVariationCalculationData);
		productivity.setKpis(kpiDataList);

		double speedGain = calculateCategorizedGain(categoryBasedKPIVariationCalculationData.get(CATEGORY_SPEED));
		double qualityGain = calculateCategorizedGain(categoryBasedKPIVariationCalculationData.get(CATEGORY_QUALITY));
		double productivityGain = calculateCategorizedGain(
				categoryBasedKPIVariationCalculationData.get(CATEGORY_PRODUCTIVITY));
		double efficiencyGain = calculateCategorizedGain(
				categoryBasedKPIVariationCalculationData.get(CATEGORY_EFFICIENCY));

		double overallGain = (speedGain
				* productivityCalculationJobConfig.getCalculationConfig().getWeightForCategory(CATEGORY_SPEED))
				+ (qualityGain * productivityCalculationJobConfig.getCalculationConfig()
						.getWeightForCategory(CATEGORY_QUALITY))
				+ (productivityGain * productivityCalculationJobConfig.getCalculationConfig()
						.getWeightForCategory(CATEGORY_PRODUCTIVITY))
				+ (efficiencyGain * productivityCalculationJobConfig.getCalculationConfig()
						.getWeightForCategory(CATEGORY_EFFICIENCY));

		double overallGainRounded = Precision.round(overallGain, NumberUtils.ROUNDING_SCALE_2);

		productivity.setCategoryScores(CategoryScores.builder().speed(speedGain).quality(qualityGain)
				.productivity(productivityGain).efficiency(efficiencyGain).overall(overallGainRounded).build());

		return productivity;
	}

	private List<KpiRequest> constructKpiRequests(ProjectInputDTO projectInputDTO) {
		Map<KpiGranularity, Set<String>> kpiIdsGroupedByXAxisMeasurement = new EnumMap<>(KpiGranularity.class);

		categoryKpiIdConfigurationMap.values().forEach(stringKPIConfigurationMap -> {
			for (Map.Entry<String, KPIConfiguration> entry : stringKPIConfigurationMap.entrySet()) {
				kpiIdsGroupedByXAxisMeasurement.computeIfAbsent(entry.getValue().getKpiGranularity(),
						key -> new HashSet<>());
				kpiIdsGroupedByXAxisMeasurement.get(entry.getValue().getKpiGranularity()).add(entry.getKey());
			}
		});

		List<KpiRequest> kpiRequests = new ArrayList<>();

		for (Map.Entry<KpiGranularity, Set<String>> entry : kpiIdsGroupedByXAxisMeasurement.entrySet()) {
			switch (entry.getKey()) {
			case WEEK -> kpiRequests.add(KpiRequest.builder().kpiIdList(new ArrayList<>(entry.getValue()))
					.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInputDTO.nodeId()),
							CommonConstant.DATE, List.of("Weeks")))
					.ids(new String[] { String.valueOf(
							productivityCalculationJobConfig.getCalculationConfig().getDataPoints().getCount()) })
					.level(projectInputDTO.hierarchyLevel()).label(projectInputDTO.hierarchyLevelId()).build());
			case SPRINT -> {
				if (CollectionUtils.isNotEmpty(projectInputDTO.sprints())) {
					kpiRequests.add(KpiRequest.builder().kpiIdList(new ArrayList<>(entry.getValue()))
							.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT,
									projectInputDTO.sprints().stream().map(SprintInputDTO::nodeId).toList(),
									CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInputDTO.nodeId())))
							.ids(projectInputDTO.sprints().stream().map(SprintInputDTO::nodeId).toList()
									.toArray(String[]::new))
							.level(projectInputDTO.sprints().get(0).hierarchyLevel())
							.label(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT).build());
				}
			}
			case ITERATION -> kpiRequests.addAll(projectInputDTO.sprints().stream().map(projectSprint -> KpiRequest
					.builder().kpiIdList(new ArrayList<>(entry.getValue()))
					.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT, List.of(projectSprint.nodeId()),
							CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInputDTO.nodeId())))
					.ids(new String[] { projectSprint.nodeId() }).level(projectSprint.hierarchyLevel())
					.label(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT).build()).toList());
			}
		}
		return kpiRequests;
	}

	private static List<KPIData> constructKPIDataAndTrendsUsedForProductivityCalculation(
			Map<String, List<KPIVariationCalculationData>> categoryBasedKPIVariationCalculationData) {
		List<KPIData> kpiDataList = new ArrayList<>();
		double variationPercentage;
		for (Map.Entry<String, List<KPIVariationCalculationData>> categoryBasedKpiGainTrendCalculationDataEntry : categoryBasedKPIVariationCalculationData
				.entrySet()) {
			for (KPIVariationCalculationData kpiVariationCalculationData : categoryBasedKpiGainTrendCalculationDataEntry
					.getValue()) {
				variationPercentage = Precision.round((kpiVariationCalculationData.getDataPointGainWeightSumProduct()
						/ kpiVariationCalculationData.getWeightParts()), NumberUtils.ROUNDING_SCALE_2);
				kpiDataList.add(KPIData.builder().category(categoryBasedKpiGainTrendCalculationDataEntry.getKey())
						.name(kpiVariationCalculationData.getKpiName()).kpiId(kpiVariationCalculationData.getKpiId())
						.calculationValue(kpiVariationCalculationData.getDataPointGainWeightSumProduct())
						.variationPercentage(variationPercentage).build());
			}
		}
		return kpiDataList;
	}

	@SuppressWarnings({ "java:S3776", "java:S134" })
	private List<KPIVariationCalculationData> constructGainTrendCalculationDataForAllKPIsInCategory(
			Map<String, List<KpiElement>> kpiIdKpiElementsMap, ProjectInputDTO projectInputDTO, String categoryName) {
		List<KPIVariationCalculationData> kpiVariationCalculationDataList = new ArrayList<>();
		int kpiWeightParts;
		for (Map.Entry<String, KPIConfiguration> kpiIdKpiConfigurationMapEntry : categoryKpiIdConfigurationMap
				.get(categoryName).entrySet()) {
			KPIConfiguration kpiConfiguration = kpiIdKpiConfigurationMapEntry.getValue();
			List<KpiElement> kpiData = kpiIdKpiElementsMap.get(kpiIdKpiConfigurationMapEntry.getKey());

			Map<Integer, List<Double>> kpiValuesByDataPointMap = constructKpiValuesByDataPointMap(kpiConfiguration,
					kpiData, projectInputDTO);

			if (MapUtils.isNotEmpty(kpiValuesByDataPointMap)) {
				Optional<Map.Entry<Integer, List<Double>>> entryContainingTheBaseLineValue = kpiValuesByDataPointMap
						.entrySet().stream()
						.filter(entrySet -> Double.compare(
								entrySet.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0D),
								0.0D) != 0)
						.findFirst();
				if (entryContainingTheBaseLineValue.isPresent()) {
					double kpiDataPointGainWeightSumProduct = 0.0D;
					double baseLineValue = entryContainingTheBaseLineValue.get().getValue().stream()
							.mapToDouble(Double::doubleValue).average().orElse(0.0D);

					kpiWeightParts = (int) (kpiValuesByDataPointMap.keySet().size()
							* kpiConfiguration.weightInProductivityScoreCalculation);

					for (Map.Entry<Integer, List<Double>> entry : kpiValuesByDataPointMap.entrySet()) {
						double average = entry.getValue().stream().mapToDouble(Double::doubleValue).average()
								.orElse(0.0D);
						if (KPIConfiguration.PositiveGainTrend.ASCENDING == kpiConfiguration.positiveGainTrend) {
							kpiDataPointGainWeightSumProduct += ((average - baseLineValue) / baseLineValue)
									* PERCENTAGE_MULTIPLIER * kpiConfiguration.weightInProductivityScoreCalculation;
						} else {
							kpiDataPointGainWeightSumProduct += ((baseLineValue - average) / baseLineValue)
									* PERCENTAGE_MULTIPLIER * kpiConfiguration.weightInProductivityScoreCalculation;
						}
					}
					kpiVariationCalculationDataList.add(KPIVariationCalculationData.builder()
							.dataPointGainWeightSumProduct(kpiDataPointGainWeightSumProduct).weightParts(kpiWeightParts)
							.kpiName(kpiIdKpiConfigurationMapEntry.getValue().getKpiName())
							.kpiId(kpiIdKpiConfigurationMapEntry.getValue().getKpiId()).build());
				}
			}
		}
		return kpiVariationCalculationDataList;
	}

	private static double calculateCategorizedGain(
			List<KPIVariationCalculationData> kpiVariationCalculationDataListForCategory) {
		if (CollectionUtils.isEmpty(kpiVariationCalculationDataListForCategory)) {
			return 0.0D;
		}
		int totalNumberOfParts = 0;
		double totalWeightSum = 0.0D;

		for (KPIVariationCalculationData kpiVariationCalculationData : kpiVariationCalculationDataListForCategory) {
			totalWeightSum += kpiVariationCalculationData.getDataPointGainWeightSumProduct();
			totalNumberOfParts += (int) kpiVariationCalculationData.getWeightParts();
		}

		if (totalNumberOfParts != 0) {
			return Precision.round((totalWeightSum / totalNumberOfParts), NumberUtils.ROUNDING_SCALE_2);
		}
		return 0.0D;
	}

	@SuppressWarnings({ "java:S3776", "java:S134" })
	private static Map<Integer, List<Double>> constructKpiValuesByDataPointMap(KPIConfiguration kpiConfiguration,
			List<KpiElement> kpiElementsFromProcessorResponse, ProjectInputDTO projectInputDTO) {
		if (CollectionUtils.isNotEmpty(kpiElementsFromProcessorResponse)) {
			Map<Integer, List<Double>> kpiValuesByDataPointMap = new HashMap<>();

			if (kpiConfiguration.getKpiGranularity() == KpiGranularity.ITERATION) {
				populateKpiValuesByDataPointMapForIterationBasedKpi(kpiValuesByDataPointMap, projectInputDTO,
						kpiElementsFromProcessorResponse, kpiConfiguration.getKpiId());
			} else {
				kpiElementsFromProcessorResponse.forEach(kpiElement -> {
					Object trendValuesObj = kpiElement.getTrendValueList();
					if (trendValuesObj instanceof List<?>) {
						List<?> trendValuesList = (List<?>) kpiElement.getTrendValueList();
						if (CollectionUtils.isNotEmpty(trendValuesList)) {
							if (trendValuesList.get(0) instanceof DataCount) {
								trendValuesList.forEach(trendValue -> {
									DataCount dataCount = (DataCount) trendValue;
									if (dataCount.getValue() instanceof List) {
										List<DataCount> dataValuesOfHierarchyEntity = (List<DataCount>) dataCount
												.getValue();
										for (int dataPoint = 0; dataPoint < dataValuesOfHierarchyEntity
												.size(); dataPoint++) {
											kpiValuesByDataPointMap.computeIfAbsent(dataPoint, v -> new ArrayList<>());
											kpiValuesByDataPointMap.get(dataPoint).add(
													((Number) dataValuesOfHierarchyEntity.get(dataPoint).getValue())
															.doubleValue());
										}
									}
								});
							} else if (trendValuesList.get(0) instanceof DataCountGroup) {
								List<DataCountGroup> overallDataCountGroups = trendValuesList.stream().filter(
										trendValue -> dataCountGroupMatchesFiltersSetForOverallProductivityGainCalculation(
												trendValue, kpiConfiguration))
										.map(DataCountGroup.class::cast).toList();
								if (CollectionUtils.isNotEmpty(overallDataCountGroups)) {
									overallDataCountGroups.forEach(overallDataCountGroup -> overallDataCountGroup
											.getValue().forEach(entityLevelDataCount -> {
												List<DataCount> dataValuesOfHierarchyEntity = (List<DataCount>) entityLevelDataCount
														.getValue();
												for (int dataPoint = 0; dataPoint < dataValuesOfHierarchyEntity
														.size(); dataPoint++) {
													kpiValuesByDataPointMap.computeIfAbsent(dataPoint,
															v -> new ArrayList<>());
													kpiValuesByDataPointMap.get(dataPoint)
															.add(((Number) dataValuesOfHierarchyEntity.get(dataPoint)
																	.getValue()).doubleValue());
												}
											}));
								}
							} else {
								log.info("KPI {} did not have any data ", kpiConfiguration.getKpiId());
							}
						}
					}
				});
			}
			return kpiValuesByDataPointMap;
		}
		return Collections.emptyMap();
	}

	@SuppressWarnings("java:S3776")
	private static void populateKpiValuesByDataPointMapForIterationBasedKpi(
			Map<Integer, List<Double>> dataPointAggregatedKpiSumMap, ProjectInputDTO projectInputDTO,
			List<KpiElement> kpiData, String kpiId) {
		Map<String, Double> sprintIdKpiValueMap = new HashMap<>();
		kpiData.forEach(kpiElement -> {
			double kpiSprintValue = 0.0D;
			if (CollectionUtils.isNotEmpty(kpiElement.getIssueData())) {
				for (IssueKpiModalValue issueKpiModalValue : kpiElement.getIssueData()) {
					if (KPI_ID_WASTAGE.equalsIgnoreCase(kpiId)) {
						kpiSprintValue += (issueKpiModalValue.getIssueBlockedTime()
								+ issueKpiModalValue.getIssueWaitTime());
					}
					if (KPI_ID_WORK_STATUS.equalsIgnoreCase(kpiId)
							&& Objects.nonNull(issueKpiModalValue.getCategoryWiseDelay().get("Planned"))) {
						kpiSprintValue += (issueKpiModalValue.getCategoryWiseDelay().get("Planned"));
					}
				}
			}
			sprintIdKpiValueMap.put(kpiElement.getSprintId(), kpiSprintValue);
		});

		for (int dataPoint = 0; dataPoint < projectInputDTO.sprints().size(); dataPoint++) {
			dataPointAggregatedKpiSumMap.computeIfAbsent(dataPoint, v -> new ArrayList<>());
			dataPointAggregatedKpiSumMap.get(dataPoint)
					.add(sprintIdKpiValueMap.getOrDefault(projectInputDTO.sprints().get(dataPoint).nodeId(), 0.0D));
		}
	}

	private static boolean dataCountGroupMatchesFiltersSetForOverallProductivityGainCalculation(Object trendValue,
			KPIConfiguration kpiConfiguration) {
		DataCountGroup dataCountGroup = (DataCountGroup) trendValue;
		String dataCountGroupFilter = kpiConfiguration.getDataCountGroupFilterUsedForCalculation();
		String dataCountGroupFilter1 = kpiConfiguration.getDataCountGroupFilter1UsedForCalculation();
		String dataCountGroupFilter2 = kpiConfiguration.getDataCountGroupFilter2UsedForCalculation();

		boolean matchesAllKpiConfigurationFilters = false;

		if (StringUtils.isNotEmpty(dataCountGroupFilter)) {
			matchesAllKpiConfigurationFilters = dataCountGroupFilter.equalsIgnoreCase(dataCountGroup.getFilter());
		}

		if (StringUtils.isNotEmpty(dataCountGroupFilter1)) {
			matchesAllKpiConfigurationFilters = dataCountGroupFilter1.equalsIgnoreCase(dataCountGroup.getFilter1());
		}

		if (StringUtils.isNotEmpty(dataCountGroupFilter2)) {
			matchesAllKpiConfigurationFilters = dataCountGroupFilter2.equalsIgnoreCase(dataCountGroup.getFilter2());
		}

		return matchesAllKpiConfigurationFilters;
	}

	private Map<String, Map<String, KPIConfiguration>> constructCategoryKpiIdConfigurationMap() {
		Map<String, Map<String, KPIConfiguration>> configuredCategoryKpiIdConfigurationMap = new HashMap<>();
		if (CollectionUtils
				.isNotEmpty(productivityCalculationJobConfig.getCalculationConfig().getAllConfiguredCategories())) {
			productivityCalculationJobConfig.getCalculationConfig().getAllConfiguredCategories()
					.forEach(configuredCategory -> {
						switch (configuredCategory) {
						case CATEGORY_EFFICIENCY -> configuredCategoryKpiIdConfigurationMap.put(CATEGORY_EFFICIENCY,
								constructKpiIdKpiConfigurationMapForEfficiencyKpis());
						case CATEGORY_SPEED -> configuredCategoryKpiIdConfigurationMap.put(CATEGORY_SPEED,
								constructKpiIdKpiConfigurationMapForSpeedKpis());
						case CATEGORY_PRODUCTIVITY -> configuredCategoryKpiIdConfigurationMap.put(CATEGORY_PRODUCTIVITY,
								constructKpiIdKpiConfigurationMapForProductivityKpis());
						case CATEGORY_QUALITY -> configuredCategoryKpiIdConfigurationMap.put(CATEGORY_QUALITY,
								constructKpiIdKpiConfigurationMapForQualityKpis());
						default -> log.warn("Category {} was not found", configuredCategory);
						}
					});
		}
		return Collections.unmodifiableMap(configuredCategoryKpiIdConfigurationMap);
	}

	private static Map<String, KPIConfiguration> constructKpiIdKpiConfigurationMapForSpeedKpis() {
		return Map.of("kpi39",
				KPIConfiguration.builder().kpiId("kpi39").kpiName("Sprint Velocity")
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build(),
				"kpi158",
				KPIConfiguration.builder().kpiId("kpi158").kpiName("Mean Time To Merge")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK).build(),
				"kpi8",
				KPIConfiguration.builder().kpiId("kpi8").kpiName("Code Build Time")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build(),
				"kpi160",
				KPIConfiguration.builder().kpiId("kpi160").kpiName("Pickup Time")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK).build(),
				"kpi164",
				KPIConfiguration.builder().kpiId("kpi164").kpiName("Scope Churn")
						.dataCountGroupFilterUsedForCalculation("Story Points")
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build());
	}

	private static Map<String, KPIConfiguration> constructKpiIdKpiConfigurationMapForQualityKpis() {
		return Map.of("kpi111",
				KPIConfiguration.builder().kpiId("kpi111").kpiName("Defect Density")
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build(),
				"kpi35",
				KPIConfiguration.builder().kpiId("kpi35").kpiName("Defect Seepage Rate")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build(),
				"kpi194",
				KPIConfiguration.builder().kpiId("kpi194").kpiName("Defect Severity Index")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build(),
				"kpi190",
				KPIConfiguration.builder().kpiId("kpi190").kpiName("Defect Reopen Rate")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build());
	}

	private static Map<String, KPIConfiguration> constructKpiIdKpiConfigurationMapForEfficiencyKpis() {
		return Map.of("kpi46",
				KPIConfiguration.builder().kpiId("kpi46").kpiName("Sprint Capacity Utilization")
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build(),
				KPI_ID_WASTAGE,
				KPIConfiguration.builder().weightInProductivityScoreCalculation(SPRINT_WEIGHT).kpiName("Wastage")
						.kpiId(KPI_ID_WASTAGE).positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.ITERATION).build(),
				KPI_ID_WORK_STATUS,
				KPIConfiguration.builder().kpiId(KPI_ID_WORK_STATUS).kpiName("Work Status")
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.ITERATION).build());
	}

	private static Map<String, KPIConfiguration> constructKpiIdKpiConfigurationMapForProductivityKpis() {
		return Map.of("kpi72",
				KPIConfiguration.builder().kpiId("kpi72").kpiName("Commitment Reliability")
						.dataCountGroupFilter1UsedForCalculation("Final Scope (Count)")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.kpiGranularity(KpiGranularity.SPRINT).build(),
				"kpi157",
				KPIConfiguration.builder().kpiId("kpi157").kpiName("Check-Ins & Merge Requests")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.kpiGranularity(KpiGranularity.WEEK).build(),
				"kpi182",
				KPIConfiguration.builder().kpiId("kpi182").kpiName("PR Success Rate")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.ASCENDING)
						.kpiGranularity(KpiGranularity.WEEK).build(),
				"kpi180",
				KPIConfiguration.builder().kpiId("kpi180").kpiName("Revert Rate")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.positiveGainTrend(KPIConfiguration.PositiveGainTrend.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK).build());
	}
}
