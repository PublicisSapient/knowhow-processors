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

/**
 * Service responsible for calculating productivity gains and performance metrics for projects within the KnowHOW platform.
 *
 * <p><strong>Core Business Logic:</strong>
 * <ul>
 *   <li>Calculates percentage variation for each KPI based on baseline comparison</li>
 *   <li>Applies configurable weights to different KPIs and categories</li>
 *   <li>Computes weighted category scores and overall productivity gain</li>
 *   <li>Handles different KPI granularities (Sprint, Week, Iteration)</li>
 * </ul>
 *
 * <p><strong>Calculation Methodology:</strong>
 * <ol>
 *   <li><strong>Baseline Establishment:</strong> Uses first non-zero data point as baseline</li>
 *   <li><strong>Trend Analysis:</strong> Calculates percentage change from baseline for each subsequent data point</li>
 *   <li><strong>Weight Application:</strong> Applies KPI-specific weights (Sprint=2.0, Week=1.0)</li>
 *   <li><strong>Category Aggregation:</strong> Averages weighted KPI variations within each category</li>
 *   <li><strong>Overall Score:</strong> Weighted sum of category scores using configurable category weights</li>
 * </ol>
 *
 * <p><strong>Trend Direction Handling:</strong>
 * <ul>
 *   <li><strong>Ascending Trend (Positive):</strong> Higher values indicate improvement (e.g., Sprint Velocity)</li>
 *   <li><strong>Descending Trend (Positive):</strong> Lower values indicate improvement (e.g., Defect Density)</li>
 * </ul>
 *
 * <p><strong>Data Processing Flow:</strong>
 * <ol>
 *   <li>Load KPI configurations with weights and trend directions</li>
 *   <li>Construct granularity-specific KPI requests (Sprint/Week/Iteration)</li>
 *   <li>Fetch KPI data from KnowHOW API</li>
 *   <li>Extract and aggregate data points by time periods</li>
 *   <li>Calculate percentage variations and apply weights</li>
 *   <li>Compute category and overall productivity scores</li>
 * </ol>
 *
 */
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

	/**
	 * Calculates comprehensive productivity gain assessment for a given project.
	 *
	 * <p>This is the main entry point for productivity calculation. The method:
	 * <ul>
	 *   <li>Validates configuration settings</li>
	 *   <li>Constructs appropriate KPI requests based on project structure</li>
	 *   <li>Fetches KPI data from external sources</li>
	 *   <li>Performs trend analysis and productivity calculations</li>
	 * </ul>
	 *
	 * @param projectInputDTO the project data including hierarchy information and sprint details
	 * @return calculated productivity object with category scores and overall gain metrics,
	 *         or {@code null} if insufficient data is available for calculation
	 */
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

	/**
	 * Performs the core productivity calculation logic for a project.
	 *
	 * <p>This method implements the main business logic for:
	 * <ul>
	 *   <li>Validating that productivity calculation is possible</li>
	 *   <li>Computing category-wise productivity gains</li>
	 *   <li>Calculating weighted overall productivity score</li>
	 *   <li>Building the final productivity assessment object</li>
	 * </ul>
	 *
	 * <p><strong>Overall Score Calculation:</strong>
	 * The overall productivity gain is computed as a weighted sum of category scores:
	 * <pre>
	 * Overall = (Speed × SpeedWeight) + (Quality × QualityWeight) +
	 *           (Productivity × ProductivityWeight) + (Efficiency × EfficiencyWeight)
	 * </pre>
	 *
	 * @param projectInputDTO the project input data
	 * @param kpisFromAllCategories the retrieved KPI elements from all categories
	 * @return calculated productivity object or {@code null} if calculation not possible
	 */
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

	/**
	 * Constructs appropriate KPI requests based on project structure and KPI granularity.
	 *
	 * <p>This method handles different KPI granularities by creating requests with appropriate parameters:
	 * <ul>
	 *   <li><strong>WEEK:</strong> Project-level requests with date-based filtering</li>
	 *   <li><strong>SPRINT:</strong> Sprint-level requests with sprint ID filtering</li>
	 *   <li><strong>ITERATION:</strong> Individual sprint requests for detailed analysis</li>
	 * </ul>
	 *
	 * <p><strong>Request Construction Logic:</strong>
	 * <ol>
	 *   <li>Group KPIs by their granularity type</li>
	 *   <li>Create granularity-specific request parameters</li>
	 *   <li>Set appropriate hierarchy levels and filters</li>
	 *   <li>Include configured data point counts for historical analysis</li>
	 * </ol>
	 *
	 * @param projectInputDTO the project input containing hierarchy and sprint information
	 * @return list of constructed KPI requests ready for API calls
	 */
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
			default -> log.info("Received unexpected x axis measurement unit {}", entry.getKey());
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

	/**
	 * Constructs gain trend calculation data for all KPIs within a specific category.
	 *
	 * <p>This method processes KPI data to calculate productivity variations by:
	 * <ul>
	 *   <li>Extracting data points from KPI trend values</li>
	 *   <li>Establishing baseline from first non-zero data point</li>
	 *   <li>Computing percentage variations based on trend direction</li>
	 *   <li>Applying KPI-specific weights to variations</li>
	 * </ul>
	 *
	 * <p><strong>Baseline Selection:</strong>
	 * The baseline is the first data point with a non-zero average value, ensuring
	 * meaningful percentage calculations.
	 *
	 * <p><strong>Variation Calculation:</strong>
	 * <ul>
	 *   <li><strong>Ascending Trend:</strong> ((current - baseline) / baseline) × 100</li>
	 *   <li><strong>Descending Trend:</strong> ((baseline - current) / baseline) × 100</li>
	 * </ul>
	 *
	 * @param kpiIdKpiElementsMap map of KPI IDs to their corresponding elements
	 * @param projectInputDTO the project input data for context
	 * @param categoryName the category name for filtering KPIs
	 * @return list of variation calculation data for KPIs in the specified category
	 */
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

	/**
	 * Calculates the overall gain for a specific category based on weighted KPI variations.
	 *
	 * <p>This method computes the category-level productivity gain by:
	 * <ul>
	 *   <li>Summing all weighted variation products within the category</li>
	 *   <li>Dividing by total weight parts to get weighted average</li>
	 *   <li>Rounding to appropriate precision for consistency</li>
	 * </ul>
	 *
	 * <p><strong>Formula:</strong>
	 * <pre>
	 * Category Gain = Σ(KPI_weighted_variations) / Σ(KPI_weight_parts)
	 * </pre>
	 *
	 * @param kpiVariationCalculationDataListForCategory list of variation calculation data for the category
	 * @return calculated category gain percentage, or 0.0 if no valid data available
	 */
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

	/**
	 * Constructs a map of data points to KPI values for trend analysis and productivity computation.
	 *
	 * <p>This method extracts and organizes KPI values by time periods, handling different
	 * data structures and granularities:
	 * <ul>
	 *   <li><strong>DataCount:</strong> Simple time-series data</li>
	 *   <li><strong>DataCountGroup:</strong> Filtered and grouped data</li>
	 *   <li><strong>Iteration-based:</strong> Sprint-specific calculations</li>
	 * </ul>
	 *
	 * <p><strong>Data Point Organization:</strong>
	 * The returned map uses integer keys representing time periods (0=oldest, n=newest)
	 * and lists of double values representing KPI measurements for that period.
	 *
	 * @param kpiConfiguration the KPI configuration with filters and settings
	 * @param kpiElementsFromProcessorResponse the KPI elements from API response
	 * @param projectInputDTO the project input data for context
	 * @return map of data point indices to lists of KPI values, or empty map if no data
	 */
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


	/**
	 * Populates KPI values for iteration-based KPIs with special calculation logic.
	 *
	 * <p>This method handles iteration-based KPIs that require custom data extraction:
	 * <ul>
	 *   <li><strong>Wastage KPI (kpi131):</strong> Sum of blocked time and wait time</li>
	 *   <li><strong>Work Status KPI (kpi128):</strong> Sum of planned delays</li>
	 * </ul>
	 *
	 * <p>The method processes issue-level data to calculate sprint-level aggregates,
	 * then organizes these values by data point for trend analysis and productivity computation.
	 *
	 * @param dataPointAggregatedKpiSumMap the map to populate with data point values
	 * @param projectInputDTO the project input data with sprint information
	 * @param kpiData the KPI elements containing issue-level data
	 * @param kpiId the specific KPI ID for custom calculation logic
	 */
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
