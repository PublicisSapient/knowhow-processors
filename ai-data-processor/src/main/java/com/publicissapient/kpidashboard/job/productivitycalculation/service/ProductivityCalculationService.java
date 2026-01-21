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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.IssueKpiModalValue;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.CategoryScores;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.KPIData;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.KpiDataPoint;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.Productivity;
import com.publicissapient.kpidashboard.common.repository.productivity.ProductivityRepository;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;
import com.publicissapient.kpidashboard.common.shared.enums.TrendDirection;
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
 * Service responsible for calculating productivity gains and performance metrics for projects
 * within the KnowHOW platform.
 *
 * <p><strong>Core Business Logic:</strong>
 *
 * <ul>
 *   <li>Calculates percentage variation for each KPI based on baseline comparison
 *   <li>Applies configurable weights to different KPIs and categories
 *   <li>Computes weighted category scores and overall productivity gain
 *   <li>Handles different KPI granularities (Sprint, Week, Iteration)
 * </ul>
 *
 * <p><strong>Calculation Methodology:</strong>
 *
 * <ol>
 *   <li><strong>Baseline Establishment:</strong> Uses first non-zero data point as baseline
 *   <li><strong>Trend Analysis:</strong> Calculates percentage change from baseline for each
 *       subsequent data point
 *   <li><strong>Weight Application:</strong> Applies KPI-specific weights (Sprint=2.0, Week=1.0)
 *   <li><strong>Category Aggregation:</strong> Averages weighted KPI variations within each
 *       category
 *   <li><strong>Overall Score:</strong> Weighted sum of category scores using configurable category
 *       weights
 * </ol>
 *
 * <p><strong>Trend Direction Handling:</strong>
 *
 * <ul>
 *   <li><strong>Ascending Trend (Positive):</strong> Higher values indicate improvement (e.g.,
 *       Sprint Velocity)
 *   <li><strong>Descending Trend (Positive):</strong> Lower values indicate improvement (e.g.,
 *       Defect Density)
 * </ul>
 *
 * <p><strong>Data Processing Flow:</strong>
 *
 * <ol>
 *   <li>Load KPI configurations with weights and trend directions
 *   <li>Construct granularity-specific KPI requests (Sprint/Week/Iteration)
 *   <li>Fetch KPI data from KnowHOW API
 *   <li>Extract and aggregate data points by time periods
 *   <li>Calculate percentage variations and apply weights
 *   <li>Compute category and overall productivity scores
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductivityCalculationService {

	private static final int MIN_NUMBER_OF_NONZERO_DATA_POINTS_REQUIRED = 2;

	private static final double WEEK_WEIGHT = 1.0D;
	private static final double SPRINT_WEIGHT = 2.0D;

	private static final String KPI_ID_WASTAGE = "kpi131";
	private static final String KPI_ID_WORK_STATUS = "kpi128";
	private static final String KPI_ID_TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE = "kpi55";
	private static final String KPI_ID_TEST_EXECUTION_AND_PASS_PERCENTAGE = "kpi71";

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	private static final class KPIConfiguration {
		private double weightInProductivityScoreCalculation;

		private String kpiId;
		private String kpiName;
		private String dataCountGroupFilterUsedForCalculation;
		private String dataCountGroupFilter1UsedForCalculation;
		private String dataCountGroupFilter2UsedForCalculation;

		private TrendDirection desiredTrend;

		private KpiGranularity kpiGranularity;

		private List<ProjectDeliveryMethodology> supportedDeliveryMethodologies;
	}

	@Getter
	@Builder
	private static final class KPIProductivityCalculationData {
		private double dataPointGainWeightSumProduct;
		private double weightParts;

		private String kpiName;
		private String kpiId;

		private TrendDirection desiredTrend;

		private List<KpiDataPoint> dataPoints;
	}

	private final ProductivityRepository productivityRepository;

	private final KnowHOWClient knowHOWClient;

	private final ProductivityCalculationConfig productivityCalculationJobConfig;

	private Map<String, Map<String, KPIConfiguration>> categoryKpiIdConfigurationMap;

	@PostConstruct
	private void initializeConfiguration() {
		this.categoryKpiIdConfigurationMap = constructCategoryKpiIdConfigurationMap();
	}

	@Transactional
	public void saveAll(List<Productivity> productivityList) {
		this.productivityRepository.saveAll(productivityList);
	}

	/**
	 * Calculates comprehensive productivity gain assessment for a given project.
	 *
	 * <p>This is the main entry point for productivity calculation. The method:
	 *
	 * <ul>
	 *   <li>Validates configuration settings
	 *   <li>Constructs appropriate KPI requests based on project structure
	 *   <li>Fetches KPI data from external sources
	 *   <li>Performs trend analysis and productivity calculations
	 * </ul>
	 *
	 * @param projectInputDTO the project data including hierarchy information and sprint details
	 * @return calculated productivity object with category scores and overall gain metrics, or {@code
	 *     null} if insufficient data is available for calculation
	 */
	public Productivity calculateProductivityGainForProject(ProjectInputDTO projectInputDTO) {
		if (CollectionUtils.isNotEmpty(
				productivityCalculationJobConfig.getCalculationConfig().getConfigValidationErrors())) {
			throw new IllegalStateException(
					String.format(
							"The following config validations errors occurred: %s",
							String.join(
									CommonConstant.COMMA,
									productivityCalculationJobConfig
											.getCalculationConfig()
											.getConfigValidationErrors())));
		}
		List<KpiRequest> kpiRequests = constructKpiRequests(projectInputDTO);
		List<KpiElement> kpiElementList =
				processAllKpiRequests(kpiRequests, projectInputDTO.deliveryMethodology());

		return calculateProductivity(projectInputDTO, kpiElementList);
	}

	private List<KpiElement> processAllKpiRequests(
			List<KpiRequest> kpiRequests, ProjectDeliveryMethodology projectDeliveryMethodology) {
		if (projectDeliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			return this.knowHOWClient.getKpiIntegrationValuesSync(kpiRequests);
		}
		if (projectDeliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			return this.knowHOWClient.getKpiIntegrationValuesKanbanSync(kpiRequests);
		}
		return Collections.emptyList();
	}

	private Map<String, List<KPIProductivityCalculationData>>
			constructCategoryBasedKPIVariationCalculationDataMap(
					Map<String, List<KpiElement>> kpiIdKpiElementsMap, ProjectInputDTO projectInputDTO) {
		Map<String, List<KPIProductivityCalculationData>>
				categoryBasedKPIVariationCalculationDataMap = new HashMap<>();
		for (String kpiCategory :
				productivityCalculationJobConfig.getCalculationConfig().getAllConfiguredCategories()) {
			categoryBasedKPIVariationCalculationDataMap.put(
					kpiCategory,
					constructWeightedBaselineVariationCalculationDataForAllKPIsInCategory(
							kpiIdKpiElementsMap, projectInputDTO, kpiCategory));
		}
		return categoryBasedKPIVariationCalculationDataMap;
	}

	/**
	 * Performs the core productivity calculation logic for a project.
	 *
	 * <p>This method implements the main business logic for:
	 *
	 * <ul>
	 *   <li>Validating that productivity calculation is possible
	 *   <li>Computing category-wise productivity gains
	 *   <li>Calculating weighted overall productivity score
	 *   <li>Building the final productivity assessment object
	 * </ul>
	 *
	 * <p><strong>Overall Score Calculation:</strong> The overall productivity gain is computed as a
	 * weighted sum of category scores:
	 *
	 * <pre>
	 * Overall = (Speed × SpeedWeight) + (Quality × QualityWeight) +
	 *           (Productivity × ProductivityWeight) + (Efficiency × EfficiencyWeight)
	 * </pre>
	 *
	 * @param projectInputDTO the project input data
	 * @param kpisFromAllCategories the retrieved KPI elements from all categories
	 * @return calculated productivity object or {@code null} if calculation not possible
	 */
	private Productivity calculateProductivity(
			ProjectInputDTO projectInputDTO, List<KpiElement> kpisFromAllCategories) {
		Map<String, List<KpiElement>> kpiIdKpiElementsMap =
				kpisFromAllCategories.stream().collect(Collectors.groupingBy(KpiElement::getKpiId));

		Map<String, List<KPIProductivityCalculationData>>
				categoryBasedKPIVariationCalculationData =
						constructCategoryBasedKPIVariationCalculationDataMap(
								kpiIdKpiElementsMap, projectInputDTO);

		log.info(
				"Resulted category based KPI variation calculation data for project with nodeId {} and name {} -> "
						+ "{}",
				projectInputDTO.nodeId(),
				projectInputDTO.name(),
				categoryBasedKPIVariationCalculationData);

		if (categoryBasedKPIVariationCalculationData.values().stream()
				.allMatch(CollectionUtils::isEmpty)) {
			log.info(
					"[productivity-calculation-job] No KPI data for productivity calculation could be found for project with nodeId {} and name {}",
					projectInputDTO.nodeId(),
					projectInputDTO.name());
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

		List<KPIData> kpiDataList =
				constructKPIDataAndTrendsUsedForProductivityCalculation(
						categoryBasedKPIVariationCalculationData);
		productivity.setKpis(kpiDataList);

		double speedGain =
				calculateCategorizedGain(categoryBasedKPIVariationCalculationData.get(CATEGORY_SPEED));
		double qualityGain =
				calculateCategorizedGain(categoryBasedKPIVariationCalculationData.get(CATEGORY_QUALITY));
		double productivityGain =
				calculateCategorizedGain(
						categoryBasedKPIVariationCalculationData.get(CATEGORY_PRODUCTIVITY));
		double efficiencyGain =
				calculateCategorizedGain(categoryBasedKPIVariationCalculationData.get(CATEGORY_EFFICIENCY));

		double overallGain =
				(speedGain
								* productivityCalculationJobConfig
										.getCalculationConfig()
										.getWeightForCategory(CATEGORY_SPEED))
						+ (qualityGain
								* productivityCalculationJobConfig
										.getCalculationConfig()
										.getWeightForCategory(CATEGORY_QUALITY))
						+ (productivityGain
								* productivityCalculationJobConfig
										.getCalculationConfig()
										.getWeightForCategory(CATEGORY_PRODUCTIVITY))
						+ (efficiencyGain
								* productivityCalculationJobConfig
										.getCalculationConfig()
										.getWeightForCategory(CATEGORY_EFFICIENCY));

		double overallGainRounded = Precision.round(overallGain, NumberUtils.ROUNDING_SCALE_2);

		productivity.setCategoryScores(
				CategoryScores.builder()
						.speed(speedGain)
						.quality(qualityGain)
						.productivity(productivityGain)
						.efficiency(efficiencyGain)
						.overall(overallGainRounded)
						.build());

		return productivity;
	}

	/**
	 * Constructs appropriate KPI requests based on project structure and KPI granularity.
	 *
	 * <p>This method handles different KPI granularities by creating requests with appropriate
	 * parameters:
	 *
	 * <ul>
	 *   <li><strong>WEEK:</strong> Project-level requests with date-based filtering
	 *   <li><strong>SPRINT:</strong> Sprint-level requests with sprint ID filtering
	 *   <li><strong>ITERATION:</strong> Individual sprint requests for detailed analysis
	 * </ul>
	 *
	 * <p><strong>Request Construction Logic:</strong>
	 *
	 * <ol>
	 *   <li>Group KPIs by their granularity type
	 *   <li>Create granularity-specific request parameters
	 *   <li>Set appropriate hierarchy levels and filters
	 *   <li>Include configured data point counts for historical analysis
	 * </ol>
	 *
	 * @param projectInputDTO the project input containing hierarchy and sprint information
	 * @return list of constructed KPI requests ready for API calls
	 */
	@SuppressWarnings({"java:S138"})
	private List<KpiRequest> constructKpiRequests(ProjectInputDTO projectInputDTO) {
		List<KpiRequest> kpiRequests = new ArrayList<>();

		List<KPIConfiguration> allKpiConfigurations =
				categoryKpiIdConfigurationMap.values().stream()
						.flatMap(kpiIdKpiConfigurationMap -> kpiIdKpiConfigurationMap.values().stream())
						.filter(
								kpiConfiguration ->
										CollectionUtils.isNotEmpty(kpiConfiguration.getSupportedDeliveryMethodologies())
												&& kpiConfiguration
														.getSupportedDeliveryMethodologies()
														.contains(projectInputDTO.deliveryMethodology()))
						.toList();

		for (KPIConfiguration kpiConfiguration : allKpiConfigurations) {
			switch (kpiConfiguration.getKpiGranularity()) {
				case MONTH, WEEK, DAY ->
						kpiRequests.add(
								KpiRequest.builder()
										.kpiIdList(List.of(kpiConfiguration.getKpiId()))
										.selectedMap(
												Map.of(
														CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
														List.of(projectInputDTO.nodeId()),
														CommonConstant.DATE,
														List.of("Weeks")))
										.ids(
												new String[] {
													String.valueOf(
															productivityCalculationJobConfig
																	.getCalculationConfig()
																	.getDataPoints()
																	.getCount())
												})
										.level(projectInputDTO.hierarchyLevel())
										.label(projectInputDTO.hierarchyLevelId())
										.build());
				case SPRINT, PI -> {
					if (CollectionUtils.isNotEmpty(projectInputDTO.sprints())) {
						kpiRequests.add(
								KpiRequest.builder()
										.kpiIdList(List.of(kpiConfiguration.getKpiId()))
										.selectedMap(
												Map.of(
														CommonConstant.HIERARCHY_LEVEL_ID_SPRINT,
														projectInputDTO.sprints().stream().map(SprintInputDTO::nodeId).toList(),
														CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
														List.of(projectInputDTO.nodeId())))
										.ids(
												projectInputDTO.sprints().stream()
														.map(SprintInputDTO::nodeId)
														.toList()
														.toArray(String[]::new))
										.level(projectInputDTO.sprints().get(0).hierarchyLevel())
										.label(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT)
										.build());
					}
				}
				case ITERATION ->
						kpiRequests.addAll(
								projectInputDTO.sprints().stream()
										.map(
												projectSprint ->
														KpiRequest.builder()
																.kpiIdList(List.of(kpiConfiguration.getKpiId()))
																.selectedMap(
																		Map.of(
																				CommonConstant.HIERARCHY_LEVEL_ID_SPRINT,
																				List.of(projectSprint.nodeId()),
																				CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
																				List.of(projectInputDTO.nodeId())))
																.ids(new String[] {projectSprint.nodeId()})
																.level(projectSprint.hierarchyLevel())
																.label(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT)
																.build())
										.toList());
				case NONE -> {
					if (projectInputDTO.deliveryMethodology() == ProjectDeliveryMethodology.KANBAN) {
						kpiRequests.add(
								KpiRequest.builder()
										.kpiIdList(List.of(kpiConfiguration.getKpiId()))
										.selectedMap(
												Map.of(
														CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
														List.of(projectInputDTO.nodeId()),
														CommonConstant.DATE,
														List.of("Weeks")))
										.ids(
												new String[] {
													String.valueOf(
															this.productivityCalculationJobConfig
																	.getCalculationConfig()
																	.getDataPoints()
																	.getCount())
												})
										.level(projectInputDTO.hierarchyLevel())
										.label(projectInputDTO.hierarchyLevelId())
										.build());
					} else {
						if (CollectionUtils.isNotEmpty(projectInputDTO.sprints())) {
							kpiRequests.add(
									KpiRequest.builder()
											.kpiIdList(List.of(kpiConfiguration.getKpiId()))
											.selectedMap(
													Map.of(
															CommonConstant.HIERARCHY_LEVEL_ID_SPRINT,
															projectInputDTO.sprints().stream()
																	.map(SprintInputDTO::nodeId)
																	.toList(),
															CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
															List.of(projectInputDTO.nodeId())))
											.ids(
													projectInputDTO.sprints().stream()
															.map(SprintInputDTO::nodeId)
															.toList()
															.toArray(String[]::new))
											.level(projectInputDTO.sprints().get(0).hierarchyLevel())
											.label(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT)
											.build());
						}
					}
				}
				default ->
						log.info(
								"[productivity-calculation-job] Received unexpected kpi granularity {}",
								kpiConfiguration);
			}
		}
		return kpiRequests;
	}

	private static List<KPIData> constructKPIDataAndTrendsUsedForProductivityCalculation(
			Map<String, List<KPIProductivityCalculationData>>
					categoryBasedKPIVariationCalculationData) {
		List<KPIData> kpiDataList = new ArrayList<>();
		double variationPercentage;
		for (Map.Entry<String, List<KPIProductivityCalculationData>>
				categoryBasedKpiGainTrendCalculationDataEntry :
						categoryBasedKPIVariationCalculationData.entrySet()) {
			for (KPIProductivityCalculationData kpiProductivityCalculationData :
					categoryBasedKpiGainTrendCalculationDataEntry.getValue()) {
				variationPercentage =
						Precision.round(
								(kpiProductivityCalculationData.getDataPointGainWeightSumProduct()
										/ kpiProductivityCalculationData.getWeightParts()),
								NumberUtils.ROUNDING_SCALE_2);
				kpiDataList.add(
						KPIData.builder()
								.category(categoryBasedKpiGainTrendCalculationDataEntry.getKey())
								.name(kpiProductivityCalculationData.getKpiName())
								.kpiId(kpiProductivityCalculationData.getKpiId())
								.desiredTrend(kpiProductivityCalculationData.getDesiredTrend())
								.dataPoints(kpiProductivityCalculationData.getDataPoints())
								.variationPercentage(variationPercentage)
								.build());
			}
		}
		return kpiDataList;
	}

	/**
	 * Constructs gain trend calculation data for all KPIs within a specific category.
	 *
	 * <p>This method processes KPI data to calculate productivity variations by:
	 *
	 * <ul>
	 *   <li>Extracting data points from KPI trend values
	 *   <li>Establishing baseline from first non-zero data point
	 *   <li>Computing percentage variations based on trend direction
	 *   <li>Applying KPI-specific weights to variations
	 * </ul>
	 *
	 * <p><strong>Baseline Selection:</strong> The baseline is the first data point with a non-zero
	 * average value, ensuring meaningful percentage calculations.
	 *
	 * <p><strong>Variation Calculation:</strong>
	 *
	 * <ul>
	 *   <li><strong>Ascending Trend:</strong> ((current - baseline) / baseline) × 100
	 *   <li><strong>Descending Trend:</strong> ((baseline - current) / baseline) × 100
	 * </ul>
	 *
	 * @param kpiIdKpiElementsMap map of KPI IDs to their corresponding elements
	 * @param projectInputDTO the project input data for context
	 * @param categoryName the category name for filtering KPIs
	 * @return list of variation calculation data for KPIs in the specified category
	 */
	@SuppressWarnings({"java:S3776", "java:S134", "java:S138"})
	private List<KPIProductivityCalculationData>
			constructWeightedBaselineVariationCalculationDataForAllKPIsInCategory(
					Map<String, List<KpiElement>> kpiIdKpiElementsMap,
					ProjectInputDTO projectInputDTO,
					String categoryName) {
		List<KPIProductivityCalculationData>
				kpiProductivityCalculationDataList = new ArrayList<>();
		int kpiWeightParts;
		for (Map.Entry<String, KPIConfiguration> kpiIdKpiConfigurationMapEntry :
				categoryKpiIdConfigurationMap.get(categoryName).entrySet()) {
			KPIConfiguration kpiConfiguration = kpiIdKpiConfigurationMapEntry.getValue();
			List<KpiElement> kpiData = kpiIdKpiElementsMap.get(kpiIdKpiConfigurationMapEntry.getKey());

			Map<Integer, List<Double>> kpiValuesByDataPointMap =
					constructKpiValuesByDataPointMap(kpiConfiguration, kpiData, projectInputDTO);

			if (MapUtils.isNotEmpty(kpiValuesByDataPointMap)) {
				if (kpiProductivityCanBeCalculated(kpiValuesByDataPointMap)) {
					// The baseline is the non-zero data point
					Optional<Map.Entry<Integer, List<Double>>> entryContainingTheBaseLineValueOptional =
							kpiValuesByDataPointMap.entrySet().stream()
									.filter(
											entrySet ->
													Double.compare(
																	entrySet.getValue().stream()
																			.mapToDouble(Double::doubleValue)
																			.average()
																			.orElse(0.0D),
																	0.0D)
															!= 0)
									.findFirst();
					if (entryContainingTheBaseLineValueOptional.isPresent()) {
						Map.Entry<Integer, List<Double>> entryContainingTheBaselineValue =
								entryContainingTheBaseLineValueOptional.get();
						double kpiDataPointGainWeightSumProduct = 0.0D;
						double baseLineValue =
								entryContainingTheBaselineValue.getValue().stream()
										.mapToDouble(Double::doubleValue)
										.average()
										.orElse(0.0D);

						kpiWeightParts =
								(int)
										((kpiValuesByDataPointMap.keySet().size()
														- entryContainingTheBaselineValue.getKey())
												* kpiConfiguration.weightInProductivityScoreCalculation);

						for (Map.Entry<Integer, List<Double>> entry : kpiValuesByDataPointMap.entrySet()) {
							// the trailing 0 data point values before the baseline value should be excluded
							// from
							// the
							// calculation
							if (entry.getKey() >= entryContainingTheBaselineValue.getKey()) {
								double currentDataPointValue =
										entry.getValue().stream()
												.mapToDouble(Double::doubleValue)
												.average()
												.orElse(0.0D);
								if (TrendDirection.ASCENDING == kpiConfiguration.desiredTrend) {
									kpiDataPointGainWeightSumProduct +=
											((currentDataPointValue - baseLineValue) / baseLineValue)
													* PERCENTAGE_MULTIPLIER
													* kpiConfiguration.weightInProductivityScoreCalculation;
								} else {
									kpiDataPointGainWeightSumProduct +=
											((baseLineValue - currentDataPointValue) / baseLineValue)
													* PERCENTAGE_MULTIPLIER
													* kpiConfiguration.weightInProductivityScoreCalculation;
								}
							}
						}
						kpiProductivityCalculationDataList.add(
								KPIProductivityCalculationData.builder()
										.dataPointGainWeightSumProduct(kpiDataPointGainWeightSumProduct)
										.weightParts(kpiWeightParts)
										.kpiName(kpiIdKpiConfigurationMapEntry.getValue().getKpiName())
										.kpiId(kpiIdKpiConfigurationMapEntry.getValue().getKpiId())
										.desiredTrend(kpiConfiguration.getDesiredTrend())
										.dataPoints(
												kpiValuesByDataPointMap.entrySet().stream()
														.map(
																dataPointKpiValueEntry ->
																		KpiDataPoint.builder()
																				.index(dataPointKpiValueEntry.getKey())
																				.value(
																						dataPointKpiValueEntry.getValue().stream()
																								.mapToDouble(Double::doubleValue)
																								.average()
																								.orElse(0.0D))
																				.build())
														.toList())
										.build());
					}
				} else {
					log.info(
							"[productivity-calculation-job] For project with nodeId {} and name {}, the KPI with id {} and name {} cannot be included in the productivity calculation "
									+ "because of insufficient eligible data points. Received data points {}",
							projectInputDTO.nodeId(),
							projectInputDTO.name(),
							kpiConfiguration.getKpiId(),
							kpiConfiguration.getKpiName(),
							kpiValuesByDataPointMap);
				}
			}
		}
		return kpiProductivityCalculationDataList;
	}

	/**
	 * Calculates the overall gain for a specific category based on weighted KPI variations.
	 *
	 * <p>This method computes the category-level productivity gain by:
	 *
	 * <ul>
	 *   <li>Summing all weighted variation products within the category
	 *   <li>Dividing by total weight parts to get weighted average
	 *   <li>Rounding to appropriate precision for consistency
	 * </ul>
	 *
	 * <p><strong>Formula:</strong>
	 *
	 * <pre>
	 * Category Gain = Σ(KPI_weighted_variations) / Σ(KPI_weight_parts)
	 * </pre>
	 *
	 * @param kpiProductivityCalculationDataListForCategory list of variation calculation
	 *     data for the category
	 * @return calculated category gain percentage, or 0.0 if no valid data available
	 */
	private static double calculateCategorizedGain(
			List<KPIProductivityCalculationData>
					kpiProductivityCalculationDataListForCategory) {
		if (CollectionUtils.isEmpty(kpiProductivityCalculationDataListForCategory)) {
			return 0.0D;
		}
		int totalNumberOfParts = 0;
		double totalWeightSum = 0.0D;

		for (KPIProductivityCalculationData kpiProductivityCalculationData :
				kpiProductivityCalculationDataListForCategory) {
			totalWeightSum +=
					kpiProductivityCalculationData.getDataPointGainWeightSumProduct();
			totalNumberOfParts += (int) kpiProductivityCalculationData.getWeightParts();
		}

		if (totalNumberOfParts != 0) {
			return Precision.round((totalWeightSum / totalNumberOfParts), NumberUtils.ROUNDING_SCALE_2);
		}
		return 0.0D;
	}

	/**
	 * Constructs a map of data points to KPI values for trend analysis and productivity computation.
	 *
	 * <p>This method extracts and organizes KPI values by time periods, handling different data
	 * structures and granularities:
	 *
	 * <ul>
	 *   <li><strong>DataCount:</strong> Simple time-series data
	 *   <li><strong>DataCountGroup:</strong> Filtered and grouped data
	 *   <li><strong>Iteration-based:</strong> Sprint-specific calculations
	 * </ul>
	 *
	 * <p><strong>Data Point Organization:</strong> The returned map uses integer keys representing
	 * time periods (0=oldest, n=newest) and lists of double values representing KPI measurements for
	 * that period.
	 *
	 * @param kpiConfiguration the KPI configuration with filters and settings
	 * @param kpiElementsFromProcessorResponse the KPI elements from API response
	 * @param projectInputDTO the project input data for context
	 * @return map of data point indices to lists of KPI values, or empty map if no data
	 */
	@SuppressWarnings({"java:S3776"})
	private static Map<Integer, List<Double>> constructKpiValuesByDataPointMap(
			KPIConfiguration kpiConfiguration,
			List<KpiElement> kpiElementsFromProcessorResponse,
			ProjectInputDTO projectInputDTO) {
		if (CollectionUtils.isNotEmpty(kpiElementsFromProcessorResponse)) {
			Map<Integer, List<Double>> kpiValuesByDataPointMap = new LinkedHashMap<>();

			if (kpiConfiguration.getKpiGranularity() == KpiGranularity.ITERATION) {
				populateKpiValuesByDataPointMapForIterationBasedKpi(
						kpiValuesByDataPointMap,
						projectInputDTO,
						kpiElementsFromProcessorResponse,
						kpiConfiguration.getKpiId());
			} else {
				kpiElementsFromProcessorResponse.forEach(
						kpiElement -> {
							Object trendValuesObj = kpiElement.getTrendValueList();
							if (trendValuesObj instanceof List<?>) {
								List<?> trendValuesList = (List<?>) kpiElement.getTrendValueList();
								if (CollectionUtils.isNotEmpty(trendValuesList)) {
									if (trendValuesList.get(0) instanceof DataCount) {
										populateKpiValuesByDataPointMapFromDataCountResponseType(
												trendValuesList, kpiValuesByDataPointMap, kpiConfiguration);
									} else if (trendValuesList.get(0) instanceof DataCountGroup) {
										populateKpiValuesByDataPointMapFromDataCountGroupResponseType(
												trendValuesList, kpiValuesByDataPointMap, kpiConfiguration);
									} else {
										log.info(
												"[productivity-calculation-job] KPI {} did not have any data ",
												kpiConfiguration.getKpiId());
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
	 * Method used to extract the data points and their related values from a trendValueList response of type DataCount
	 *
	 * @see DataCount
	 * @param trendValuesList list containing the KPI element response used for extracting the data points
	 * @param kpiValuesByDataPointMap map used for storing the data point index -> data point value necessary for
	 *                                   productivity calculation
	 * @param kpiConfiguration class representing the configuration of the KPI for which the data points are extracted
	 */
	private static void populateKpiValuesByDataPointMapFromDataCountResponseType(
			List<?> trendValuesList,
			Map<Integer, List<Double>> kpiValuesByDataPointMap,
			KPIConfiguration kpiConfiguration) {
		trendValuesList.forEach(
				trendValue -> {
					DataCount dataCount = (DataCount) trendValue;
					if (dataCount.getValue() instanceof List) {
						List<DataCount> dataValuesOfHierarchyEntity = (List<DataCount>) dataCount.getValue();
						for (int dataPoint = 0; dataPoint < dataValuesOfHierarchyEntity.size(); dataPoint++) {
							double dataPointValue;
							if (KPI_ID_TEST_EXECUTION_AND_PASS_PERCENTAGE.equalsIgnoreCase(
									kpiConfiguration.getKpiId())) {
								dataPointValue =
										computeDataPointValueForTestExecutionAndPassPercentageKPI(
												dataValuesOfHierarchyEntity.get(dataPoint));
							} else {
								dataPointValue =
										((Number) dataValuesOfHierarchyEntity.get(dataPoint).getValue()).doubleValue();
							}
							kpiValuesByDataPointMap.computeIfAbsent(dataPoint, v -> new ArrayList<>());
							kpiValuesByDataPointMap.get(dataPoint).add(dataPointValue);
						}
					}
				});
	}

	/**
	 * As the current response received for the "Test Execution and Pass Percentage" KPI cannot be directly used in
	 * the productivity calculation some adjustments were necessary to determine the data point values in the
	 * following manner:
	 * <ul>
	 *     <li>Extracting the open tickets count from the KPI response → this value corresponds to the
	 *     {@code dataCount.value}</li>
	 *     <li>Extracting the closed tickets count from the KPI response → this value
	 *     corresponds to the {@code dataCount.lineValue}</li>
	 *     <li>Applying the data point formula for this KPI:
	 *     <b>DataPointValue = closedTicketCount / openTicketsCount</b>
	 *     </li>
	 * </ul>
	 *
	 * @param dataCount class representing a data point to be plotted on a chart
	 * @return the adjusted data point value calculated based on received DataCount
	 */
	private static double computeDataPointValueForTestExecutionAndPassPercentageKPI(
			DataCount dataCount) {
		if (dataCount != null) {
			double executedTestsPercentage = ((Number) dataCount.getValue()).doubleValue();
			double passedTestsPercentage = ((Number) dataCount.getLineValue()).doubleValue();
			return executedTestsPercentage * passedTestsPercentage;
		}
		return 0.0D;
	}

	/**
	 * Method used to extract the data points and their related values from a trendValueList response of type
	 * DataCountGroup
	 *
	 * @see DataCountGroup
	 * @param trendValuesList list containing the KPI element response used for extracting the data points
	 * @param kpiValuesByDataPointMap map used for storing the data point index -> data point value necessary for
	 *                                   productivity calculation
	 * @param kpiConfiguration class representing the configuration of the KPI for which the data points are extracted
	 */
	private static void populateKpiValuesByDataPointMapFromDataCountGroupResponseType(
			List<?> trendValuesList,
			Map<Integer, List<Double>> kpiValuesByDataPointMap,
			KPIConfiguration kpiConfiguration) {
		List<DataCountGroup> overallDataCountGroups =
				trendValuesList.stream()
						.filter(
								trendValue ->
										dataCountGroupMatchesFiltersSetForOverallProductivityCalculation(
												trendValue, kpiConfiguration))
						.map(DataCountGroup.class::cast)
						.toList();
		if (CollectionUtils.isNotEmpty(overallDataCountGroups)) {
			overallDataCountGroups.forEach(
					overallDataCountGroup ->
							overallDataCountGroup
									.getValue()
									.forEach(
											entityLevelDataCount -> {
												List<DataCount> dataValuesOfHierarchyEntity =
														(List<DataCount>) entityLevelDataCount.getValue();
												for (int dataPoint = 0;
														dataPoint < dataValuesOfHierarchyEntity.size();
														dataPoint++) {
													double dataPointValue;
													if (KPI_ID_TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE.equalsIgnoreCase(
															kpiConfiguration.getKpiId())) {
														dataPointValue =
																computeDataPointValueForTicketOpenVsClosedRateByTypeKPI(
																		dataValuesOfHierarchyEntity.get(dataPoint));
													} else {
														dataPointValue =
																((Number) dataValuesOfHierarchyEntity.get(dataPoint).getValue())
																		.doubleValue();
													}
													kpiValuesByDataPointMap.computeIfAbsent(
															dataPoint, v -> new ArrayList<>());
													kpiValuesByDataPointMap.get(dataPoint).add((dataPointValue));
												}
											}));
		}
	}

	/**
	 * As the current response received for the "Ticket Open vs Closed Rate by Type" KPI cannot be directly used in
	 * the productivity calculation some adjustments were necessary to determine the data point values in the
	 * following manner:
	 * <ul>
	 *     <li>Extracting the executed tests percentage from the KPI response → this value corresponds to the
	 *     {@code dataCount.value}</li>
	 *     <li>Extracting the successfully run (passed) tests percentage from the KPI response → this value
	 *     corresponds to the {@code dataCount.lineValue}</li>
	 *     <li>Applying the data point formula for this KPI:
	 *     <b>DataPointValue = executedTestsPercentage * passedTestsPercentage</b>
	 *     </li>
	 * </ul>
	 *
	 * @param dataCount class representing a data point to be plotted on a chart
	 * @return the adjusted data point value calculated based on received DataCount
	 */
	private static double computeDataPointValueForTicketOpenVsClosedRateByTypeKPI(
			DataCount dataCount) {
		if (dataCount != null) {
			double openTicketsCount = ((Number) dataCount.getValue()).doubleValue();
			double closedTicketsCount = ((Number) dataCount.getLineValue()).doubleValue();
			if (Double.compare(openTicketsCount, 0.0D) != 0) {
				return closedTicketsCount / openTicketsCount;
			}
		}
		return 0.0D;
	}

	/**
	 * Populates KPI values for iteration-based KPIs with special calculation logic.
	 *
	 * <p>This method handles iteration-based KPIs that require custom data extraction:
	 *
	 * <ul>
	 *   <li><strong>Wastage KPI (kpi131):</strong> Sum of blocked time and wait time
	 *   <li><strong>Work Status KPI (kpi128):</strong> Sum of planned delays
	 * </ul>
	 *
	 * <p>The method processes issue-level data to calculate sprint-level aggregates, then organizes
	 * these values by data point for trend analysis and productivity computation.
	 *
	 * @param dataPointAggregatedKpiSumMap the map to populate with data point values
	 * @param projectInputDTO the project input data with sprint information
	 * @param kpiData the KPI elements containing issue-level data
	 * @param kpiId the specific KPI ID for custom calculation logic
	 */
	@SuppressWarnings("java:S3776")
	private static void populateKpiValuesByDataPointMapForIterationBasedKpi(
			Map<Integer, List<Double>> dataPointAggregatedKpiSumMap,
			ProjectInputDTO projectInputDTO,
			List<KpiElement> kpiData,
			String kpiId) {
		Map<String, Double> sprintIdKpiValueMap = new HashMap<>();
		kpiData.forEach(
				kpiElement -> {
					double kpiSprintValue = 0.0D;
					if (CollectionUtils.isNotEmpty(kpiElement.getIssueData())) {
						for (IssueKpiModalValue issueKpiModalValue : kpiElement.getIssueData()) {
							if (KPI_ID_WASTAGE.equalsIgnoreCase(kpiId)) {
								kpiSprintValue +=
										(issueKpiModalValue.getIssueBlockedTime()
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
			dataPointAggregatedKpiSumMap
					.get(dataPoint)
					.add(
							sprintIdKpiValueMap.getOrDefault(
									projectInputDTO.sprints().get(dataPoint).nodeId(), 0.0D));
		}
	}

	private static boolean dataCountGroupMatchesFiltersSetForOverallProductivityCalculation(
			Object trendValue, KPIConfiguration kpiConfiguration) {
		DataCountGroup dataCountGroup = (DataCountGroup) trendValue;
		String dataCountGroupFilter = kpiConfiguration.getDataCountGroupFilterUsedForCalculation();
		String dataCountGroupFilter1 = kpiConfiguration.getDataCountGroupFilter1UsedForCalculation();
		String dataCountGroupFilter2 = kpiConfiguration.getDataCountGroupFilter2UsedForCalculation();

		boolean matchesAllKpiConfigurationFilters = false;

		if (StringUtils.isNotEmpty(dataCountGroupFilter)) {
			matchesAllKpiConfigurationFilters =
					dataCountGroupFilter.equalsIgnoreCase(dataCountGroup.getFilter());
		}

		if (StringUtils.isNotEmpty(dataCountGroupFilter1)) {
			matchesAllKpiConfigurationFilters =
					dataCountGroupFilter1.equalsIgnoreCase(dataCountGroup.getFilter1());
		}

		if (StringUtils.isNotEmpty(dataCountGroupFilter2)) {
			matchesAllKpiConfigurationFilters =
					dataCountGroupFilter2.equalsIgnoreCase(dataCountGroup.getFilter2());
		}

		return matchesAllKpiConfigurationFilters;
	}

	private Map<String, Map<String, KPIConfiguration>> constructCategoryKpiIdConfigurationMap() {
		Map<String, Map<String, KPIConfiguration>> configuredCategoryKpiIdConfigurationMap =
				new HashMap<>();
		if (CollectionUtils.isNotEmpty(
				productivityCalculationJobConfig.getCalculationConfig().getAllConfiguredCategories())) {
			productivityCalculationJobConfig
					.getCalculationConfig()
					.getAllConfiguredCategories()
					.forEach(
							configuredCategory -> {
								switch (configuredCategory) {
									case CATEGORY_EFFICIENCY ->
											configuredCategoryKpiIdConfigurationMap.put(
													CATEGORY_EFFICIENCY,
													constructKpiIdKpiConfigurationMapForEfficiencyKpis());
									case CATEGORY_SPEED ->
											configuredCategoryKpiIdConfigurationMap.put(
													CATEGORY_SPEED, constructKpiIdKpiConfigurationMapForSpeedKpis());
									case CATEGORY_PRODUCTIVITY ->
											configuredCategoryKpiIdConfigurationMap.put(
													CATEGORY_PRODUCTIVITY,
													constructKpiIdKpiConfigurationMapForProductivityKpis());
									case CATEGORY_QUALITY ->
											configuredCategoryKpiIdConfigurationMap.put(
													CATEGORY_QUALITY, constructKpiIdKpiConfigurationMapForQualityKpis());
									default ->
											log.warn(
													"[productivity-calculation-job] Category {} was not found",
													configuredCategory);
								}
							});
		}
		return Collections.unmodifiableMap(configuredCategoryKpiIdConfigurationMap);
	}

	private static boolean kpiProductivityCanBeCalculated(
			Map<Integer, List<Double>> kpiValuesByDataPointMap) {
		return MapUtils.isNotEmpty(kpiValuesByDataPointMap)
				&& kpiValuesByDataPointMap.entrySet().stream()
								.filter(
										entrySet ->
												Double.compare(
																entrySet.getValue().stream()
																		.mapToDouble(Double::doubleValue)
																		.average()
																		.orElse(0.0D),
																0.0D)
														!= 0)
								.count()
						>= MIN_NUMBER_OF_NONZERO_DATA_POINTS_REQUIRED;
	}

	private static Map<String, KPIConfiguration> constructKpiIdKpiConfigurationMapForSpeedKpis() {
		return Map.of(
				"kpi39",
				KPIConfiguration.builder()
						.kpiId("kpi39")
						.kpiName("Sprint Velocity")
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi158",
				KPIConfiguration.builder()
						.kpiId("kpi158")
						.kpiName("Mean Time To Merge")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi8",
				KPIConfiguration.builder()
						.kpiId("kpi8")
						.kpiName("Code Build Time")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi160",
				KPIConfiguration.builder()
						.kpiId("kpi160")
						.kpiName("Pickup Time")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi164",
				KPIConfiguration.builder()
						.kpiId("kpi164")
						.kpiName("Scope Churn")
						.dataCountGroupFilterUsedForCalculation("Story Points")
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi49",
				KPIConfiguration.builder()
						.kpiId("kpi49")
						.kpiName("Ticket Velocity")
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.KANBAN))
						.build(),
				"kpi66",
				KPIConfiguration.builder()
						.kpiId("kpi66")
						.kpiName("Code Build Time")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.KANBAN))
						.build());
	}

	@SuppressWarnings({"java:S138"})
	private static Map<String, KPIConfiguration> constructKpiIdKpiConfigurationMapForQualityKpis() {
		return Map.of(
				"kpi111",
				KPIConfiguration.builder()
						.kpiId("kpi111")
						.kpiName("Defect Density")
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi35",
				KPIConfiguration.builder()
						.kpiId("kpi35")
						.kpiName("Defect Seepage Rate")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi194",
				KPIConfiguration.builder()
						.kpiId("kpi194")
						.kpiName("Defect Severity Index")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi190",
				KPIConfiguration.builder()
						.kpiId("kpi190")
						.kpiName("Defect Reopen Rate")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi62",
				KPIConfiguration.builder()
						.kpiId("kpi62")
						.kpiName("Unit Test Coverage")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.KANBAN))
						.build(),
				"kpi63",
				KPIConfiguration.builder()
						.kpiId("kpi63")
						.kpiName("Regression Automation Coverage")
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.KANBAN))
						.build(),
				"kpi64",
				KPIConfiguration.builder()
						.kpiId("kpi64")
						.kpiName("Code Violations")
						.dataCountGroupFilter1UsedForCalculation(CommonConstant.OVERALL)
						.dataCountGroupFilter2UsedForCalculation("Type")
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.KANBAN))
						.build(),
				KPI_ID_TEST_EXECUTION_AND_PASS_PERCENTAGE,
				KPIConfiguration.builder()
						.kpiId(KPI_ID_TEST_EXECUTION_AND_PASS_PERCENTAGE)
						.kpiName("Test Execution and pass percentage")
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.KANBAN))
						.build());
	}

	private static Map<String, KPIConfiguration>
			constructKpiIdKpiConfigurationMapForEfficiencyKpis() {
		return Map.of(
				"kpi46",
				KPIConfiguration.builder()
						.kpiId("kpi46")
						.kpiName("Sprint Capacity Utilization")
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				KPI_ID_WASTAGE,
				KPIConfiguration.builder()
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.kpiName("Wastage")
						.kpiId(KPI_ID_WASTAGE)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.ITERATION)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				KPI_ID_WORK_STATUS,
				KPIConfiguration.builder()
						.kpiId(KPI_ID_WORK_STATUS)
						.kpiName("Work Status")
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.ITERATION)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi48",
				KPIConfiguration.builder()
						.kpiId("kpi48")
						.kpiName("Net Open Ticket by Status")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.KANBAN))
						.build());
	}

	private static Map<String, KPIConfiguration>
			constructKpiIdKpiConfigurationMapForProductivityKpis() {
		return Map.of(
				"kpi72",
				KPIConfiguration.builder()
						.kpiId("kpi72")
						.kpiName("Commitment Reliability")
						.dataCountGroupFilter1UsedForCalculation("Final Scope (Count)")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.SPRINT)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi157",
				KPIConfiguration.builder()
						.kpiId("kpi157")
						.kpiName("Check-Ins & Merge Requests")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi182",
				KPIConfiguration.builder()
						.kpiId("kpi182")
						.kpiName("PR Success Rate")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				"kpi180",
				KPIConfiguration.builder()
						.kpiId("kpi180")
						.kpiName("Revert Rate")
						.dataCountGroupFilter2UsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(SPRINT_WEIGHT)
						.desiredTrend(TrendDirection.DESCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.SCRUM))
						.build(),
				KPI_ID_TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE,
				KPIConfiguration.builder()
						.kpiId(KPI_ID_TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE)
						.kpiName("Ticket Open vs Closed rate by type")
						.dataCountGroupFilterUsedForCalculation(CommonConstant.OVERALL)
						.weightInProductivityScoreCalculation(WEEK_WEIGHT)
						.desiredTrend(TrendDirection.ASCENDING)
						.kpiGranularity(KpiGranularity.WEEK)
						.supportedDeliveryMethodologies(List.of(ProjectDeliveryMethodology.KANBAN))
						.build());
	}
}
