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

package com.publicissapient.kpidashboard.job.kpimaturitycalculation.service;

import static com.publicissapient.kpidashboard.utils.NumberUtils.isNumeric;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.KpiCategoryMapping;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.EfficiencyScore;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KPIData;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KpiMaturity;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.MaturityScore;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.KpiMasterCustomRepository;
import com.publicissapient.kpidashboard.common.repository.kpimaturity.organization.KpiMaturityRepository;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.config.KpiMaturityCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.shared.dto.SprintInputDTO;
import com.publicissapient.kpidashboard.job.shared.enums.KpiGranularity;
import com.publicissapient.kpidashboard.utils.NumberUtils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for calculating KPI maturity scores for projects within
 * the KnowHOW platform.
 *
 * <p>
 * This service performs comprehensive maturity assessment by:
 * <ul>
 * <li>Retrieving KPI data from various sources (Jira, Sonar, Jenkins,
 * etc.)</li>
 * <li>Calculating category-wise maturity scores based on configured
 * weights</li>
 * <li>Computing overall efficiency scores and percentages</li>
 * <li>Persisting maturity data for organizational reporting</li>
 * </ul>
 *
 * <p>
 * <strong>Key Business Logic:</strong>
 * <ul>
 * <li>Maturity scores are calculated per category (quality, velocity, dora,
 * etc.)</li>
 * <li>Each category score is the average of its constituent KPI maturity
 * values</li>
 * <li>Efficiency score is a weighted sum of category scores with configurable
 * weights</li>
 * <li>Only KPIs with numeric maturity values are included in calculations</li>
 * </ul>
 *
 * <p>
 * <strong>Configuration Dependencies:</strong>
 * <ul>
 * <li>KPI category mappings must be configured in the database</li>
 * <li>Maturity calculation weights must be defined in application
 * configuration</li>
 * <li>Data point count for historical analysis is configurable</li>
 * </ul>
 *
 * <p>
 * <strong>Data Flow:</strong>
 * <ol>
 * <li>Load eligible KPIs based on delivery methodology (SCRUM)</li>
 * <li>Construct KPI requests based on granularity (Sprint/Week/Day/Month)</li>
 * <li>Fetch KPI data from KnowHOW API</li>
 * <li>Calculate category-wise maturity scores</li>
 * <li>Compute weighted efficiency score</li>
 * <li>Build and return KpiMaturity object</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiMaturityCalculationService {

	/** Maximum possible efficiency score used for percentage calculations */
	private static final double EFFICIENCY_MAX_SCORE = 5.0D;

	private static final String KPI_GRANULARITY_WEEKS = "Weeks";

	private final KpiMaturityRepository kpiMaturityRepository;
	private final KpiCategoryMappingRepository kpiCategoryMappingRepository;
	private final KpiMasterCustomRepository kpiMasterCustomRepository;

	private final KpiMaturityCalculationConfig kpiMaturityCalculationConfig;

	private final KnowHOWClient knowHOWClient;

	private List<KpiMaster> kpisEligibleForMaturityCalculation;

	@PostConstruct
	private void initializePreloadedData() {
		this.kpisEligibleForMaturityCalculation = loadKpisEligibleForMaturityCalculation();
	}

	public void saveAll(List<KpiMaturity> kpiMaturities) {
		this.kpiMaturityRepository.saveAll(kpiMaturities);
	}

	/**
	 * Calculates comprehensive KPI maturity assessment for a given project.
	 *
	 * <p>
	 * This is the main entry point for maturity calculation. The method:
	 * <ul>
	 * <li>Validates configuration settings</li>
	 * <li>Constructs appropriate KPI requests based on project structure</li>
	 * <li>Fetches KPI data from external sources</li>
	 * <li>Performs maturity calculations and scoring</li>
	 * </ul>
	 *
	 * @param projectInput
	 *            the project data including organization hierarchy information and
	 *            sprint details
	 * @return calculated KPI maturity object with scores and efficiency metrics, or
	 *         {@code null} if insufficient data is available for calculation
	 */
	public KpiMaturity calculateKpiMaturityForProject(ProjectInputDTO projectInput) {
		if (CollectionUtils
				.isNotEmpty(kpiMaturityCalculationConfig.getCalculationConfig().getConfigValidationErrors())) {
			throw new IllegalStateException(String.format("The following config validations errors occurred: %s",
					String.join(CommonConstant.COMMA,
							kpiMaturityCalculationConfig.getCalculationConfig().getConfigValidationErrors())));
		}
		List<KpiRequest> kpiRequests = constructKpiRequests(projectInput);
		List<KpiElement> kpiElementList = processAllKpiRequests(kpiRequests, projectInput.deliveryMethodology());

		return calculateKpiMaturity(projectInput, kpiElementList);
	}

	private List<KpiElement> processAllKpiRequests(List<KpiRequest> kpiRequests, ProjectDeliveryMethodology projectDeliveryMethodology) {
		if(projectDeliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			return this.knowHOWClient.getKpiIntegrationValuesKanban(kpiRequests);
		}
		return this.knowHOWClient.getKpiIntegrationValues(kpiRequests);
	}

	/**
	 * Performs the core maturity calculation logic for a project.
	 *
	 * <p>
	 * This method implements the business logic for:
	 * <ul>
	 * <li>Validating that maturity calculation is possible</li>
	 * <li>Grouping KPIs by category and calculating category averages</li>
	 * <li>Computing weighted efficiency scores</li>
	 * <li>Building the final maturity assessment object</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Calculation Logic:</strong>
	 * <ul>
	 * <li>Category scores = average of constituent KPI maturity values</li>
	 * <li>Efficiency score = weighted sum of category scores</li>
	 * <li>Efficiency percentage = (efficiency score / max score) * 100</li>
	 * <li>Maturity levels are assigned as M1, M2, M3, M4, M5 based on score
	 * ceiling</li>
	 * </ul>
	 * 
	 * @param projectInput
	 *            the project input data
	 * @param kpiElementList
	 *            the retrieved KPI elements response with maturity data
	 * @return calculated KPI maturity object or {@code null} if calculation not
	 *         possible
	 */
	private KpiMaturity calculateKpiMaturity(ProjectInputDTO projectInput, List<KpiElement> kpiElementList) {
		if (Boolean.FALSE.equals(kpiMaturityCanBeCalculated(kpiElementList))) {
			log.info("No KPI data for productivity calculation could be found for project with nodeId {} and name {}",
					projectInput.nodeId(), projectInput.name());
			// Returning null will ensure that the current project is skipped from database
			// insertion
			return null;
		}
		Map<String, KpiMaster> kpiIdKpiMasterMap = this.kpisEligibleForMaturityCalculation.stream()
				.collect(Collectors.toMap(KpiMaster::getKpiId, kpiElement -> kpiElement));
		Map<String, List<KpiElement>> kpiElementsGroupedByCategory = kpiElementList.stream()
				.filter(kpiElement -> kpiIdKpiMasterMap.containsKey(kpiElement.getKpiId())
						&& StringUtils.isNotBlank(kpiIdKpiMasterMap.get(kpiElement.getKpiId()).getKpiCategory()))
				.peek(kpiElement -> kpiElement
						.setKpiCategory(kpiIdKpiMasterMap.get(kpiElement.getKpiId()).getKpiCategory()))
				.collect(Collectors.groupingBy(KpiElement::getKpiCategory));
		Map<String, Double> maturityScoreByCategory = new HashMap<>();

		List<MaturityScore> maturityScores = new ArrayList<>();
		List<KPIData> kpiDataList = new ArrayList<>();
		for (Map.Entry<String, List<KpiElement>> entry : kpiElementsGroupedByCategory.entrySet()) {
			MaturityScore maturityScore = new MaturityScore();
			maturityScore.setKpiCategory(entry.getKey());

			double categoryMaturityScore = 0.0D;

			int kpisHavingMaturityData = 0;

			for (KpiElement kpiElement : entry.getValue()) {
				if (isNumeric(kpiElement.getOverallMaturity())) {
					double kpiMaturity = Double.parseDouble(kpiElement.getOverallMaturity());
					categoryMaturityScore += kpiMaturity;
					kpisHavingMaturityData++;

					kpiDataList.add(KPIData.builder().kpiId(kpiElement.getKpiId()).overallMaturity(kpiMaturity)
							.name(kpiElement.getKpiName()).category(kpiElement.getKpiCategory()).build());
				}
			}

			if (kpisHavingMaturityData > 0) {
				maturityScore.setScore(Precision.round((categoryMaturityScore / kpisHavingMaturityData),
						NumberUtils.ROUNDING_SCALE_2));
				maturityScore.setLevel("M" + (int) Math.ceil(maturityScore.getScore()));
			}
			maturityScoreByCategory.computeIfAbsent(entry.getKey(), value -> {
				if (Objects.isNull(maturityScore.getScore())) {
					return 0.0D;
				}
				return maturityScore.getScore();
			});
			maturityScores.add(maturityScore);
		}

		EfficiencyScore efficiencyScore = calculateEfficiencyScore(maturityScoreByCategory);

		return KpiMaturity.builder().hierarchyLevel(projectInput.hierarchyLevel())
				.hierarchyEntityName(projectInput.name()).hierarchyEntityNodeId(projectInput.nodeId())
				.hierarchyLevelId(projectInput.hierarchyLevelId()).calculationDate(Instant.now())
				.maturityScores(maturityScores).kpis(kpiDataList).efficiency(efficiencyScore).build();
	}

	/**
	 * Calculates the overall efficiency score based on category maturity scores and
	 * configured weights.
	 *
	 * <p>
	 * The efficiency calculation uses a weighted average approach where:
	 * <ul>
	 * <li>Each category has a configured weight (importance factor)</li>
	 * <li>Efficiency score = Σ(category_weight × category_score)</li>
	 * <li>Efficiency percentage = (efficiency_score / max_possible_score) ×
	 * 100</li>
	 * </ul>
	 *
	 * @param maturityScoreByCategory
	 *            map of category names to their calculated maturity scores
	 * @return efficiency score object containing both absolute score and percentage
	 */
	private EfficiencyScore calculateEfficiencyScore(Map<String, Double> maturityScoreByCategory) {
		Map<String, Double> maturityWeightsGroupedByCategory = this.kpiMaturityCalculationConfig.getCalculationConfig()
				.getMaturity().getWeights();

		double overallEfficiencyScore = 0.0D;

		for (Map.Entry<String, Double> entry : maturityWeightsGroupedByCategory.entrySet()) {
			if (maturityScoreByCategory.containsKey(entry.getKey())) {
				overallEfficiencyScore += (entry.getValue() * maturityScoreByCategory.get(entry.getKey()));
			}
		}

		return EfficiencyScore.builder().score(Precision.round(overallEfficiencyScore, NumberUtils.ROUNDING_SCALE_1))
				.percentage(Precision.round(
						((overallEfficiencyScore / EFFICIENCY_MAX_SCORE) * NumberUtils.PERCENTAGE_MULTIPLIER),
						NumberUtils.ROUNDING_SCALE_1))
				.build();
	}

	/**
	 * Constructs appropriate KPI requests based on project structure and KPI
	 * granularity.
	 *
	 * <p>
	 * This method handles different KPI granularities by creating requests with
	 * appropriate:
	 * <ul>
	 * <li><strong>Time-based KPIs</strong> (MONTH, WEEK, DAY): Use project-level
	 * hierarchy with date ranges</li>
	 * <li><strong>Sprint-based KPIs</strong> (SPRINT, ITERATION, PI): Use
	 * sprint-level hierarchy with sprint IDs</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Request Construction Logic:</strong>
	 * <ul>
	 * <li>KPIs are grouped by source (Jira, Sonar, Jenkins, etc.)</li>
	 * <li>Granularity is determined from the first KPI's xAxisLabel in each
	 * group</li>
	 * <li>Request parameters are set based on granularity type</li>
	 * </ul>
	 *
	 * @param projectInput
	 *            the project input containing hierarchy and sprint information
	 * @return list of constructed KPI requests ready for API calls
	 */
	private List<KpiRequest> constructKpiRequests(ProjectInputDTO projectInput) {
		List<KpiRequest> kpiRequests = new ArrayList<>();

		Map<String, List<KpiMaster>> kpisGroupedBySource = this.kpisEligibleForMaturityCalculation.stream()
				.filter(kpiMaster -> kpiMaster.getKanban() == (projectInput.deliveryMethodology() == ProjectDeliveryMethodology.KANBAN))
				.collect(Collectors.groupingBy(KpiMaster::getKpiSource));

		for (Map.Entry<String, List<KpiMaster>> entry : kpisGroupedBySource.entrySet()) {
			KpiGranularity kpiGranularity = KpiGranularity.getByKpiXAxisLabel(entry.getValue().get(0).getXAxisLabel());
			switch (kpiGranularity) {
				case MONTH, WEEK, DAY -> kpiRequests.add(KpiRequest.builder()
						.kpiIdList(new ArrayList<>(entry.getValue().stream().map(KpiMaster::getKpiId).toList()))
						.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId()),
								CommonConstant.DATE, List.of(KPI_GRANULARITY_WEEKS)))
						.ids(new String[]{String.valueOf(
								this.kpiMaturityCalculationConfig.getCalculationConfig().getDataPoints().getCount())})
						.level(projectInput.hierarchyLevel())
						.label(projectInput.hierarchyLevelId()).build());
				case SPRINT, ITERATION, PI -> kpiRequests.add(KpiRequest.builder()
						.kpiIdList(new ArrayList<>(entry.getValue().stream().map(KpiMaster::getKpiId).toList()))
						.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT,
								projectInput.sprints().stream().map(SprintInputDTO::nodeId).toList(),
								CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId())))
						.ids(projectInput.sprints().stream().map(SprintInputDTO::nodeId).toList()
								.toArray(String[]::new))
						.level(projectInput.sprints().get(0).hierarchyLevel())
						.label(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT).build());
				case NONE -> {
					if(projectInput.deliveryMethodology() == ProjectDeliveryMethodology.KANBAN) {
						kpiRequests.add(KpiRequest.builder()
								.kpiIdList(new ArrayList<>(entry.getValue().stream().map(KpiMaster::getKpiId).toList()))
								.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId()),
										CommonConstant.DATE, List.of(KPI_GRANULARITY_WEEKS)))
								.ids(new String[]{String.valueOf(
										this.kpiMaturityCalculationConfig.getCalculationConfig().getDataPoints().getCount())})
								.level(projectInput.hierarchyLevel()).label(projectInput.hierarchyLevelId()).build());
					} else {
						kpiRequests.add(KpiRequest.builder()
								.kpiIdList(new ArrayList<>(entry.getValue().stream().map(KpiMaster::getKpiId).toList()))
								.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT,
										projectInput.sprints().stream().map(SprintInputDTO::nodeId).toList(),
										CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId())))
								.ids(projectInput.sprints().stream().map(SprintInputDTO::nodeId).toList()
										.toArray(String[]::new))
								.level(projectInput.sprints().get(0).hierarchyLevel())
								.label(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT).build());
					}
				}
			}
		}
		return kpiRequests;
	}

	/**
	 * Loads and filters KPIs that are eligible for maturity calculation.
	 *
	 * <p>
	 * This method performs a multi-step filtering process:
	 * <ol>
	 * <li>Fetch KPIs supporting maturity calculation for SCRUM and KANBAN methodology</li>
	 * <li>Map KPI categories from database configuration</li>
	 * <li>Override categories with values from KpiCategoryMapping if available</li>
	 * <li>Filter KPIs to include only those with configured category weights</li>
	 * </ol>
	 *
	 * @return filtered list of KPI masters eligible for maturity calculation
	 */
	private List<KpiMaster> loadKpisEligibleForMaturityCalculation() {
		List<KpiMaster> kpiMasterList = this.kpiMasterCustomRepository.findKpisSupportingMaturityCalculation().stream()
				.map(kpiMasterProjection -> {
					String kpiCategory;
					if (StringUtils.isEmpty(kpiMasterProjection.getKpiCategory())) {
						kpiCategory = StringUtils.EMPTY;
					} else {
						kpiCategory = kpiMasterProjection.getKpiCategory();
					}
					return KpiMaster.builder().kpiId(kpiMasterProjection.getKpiId())
							.kpiName(kpiMasterProjection.getKpiName()).kpiCategory(kpiCategory.toLowerCase())
							.kpiSource(kpiMasterProjection.getKpiSource())
							.kanban(kpiMasterProjection.isKanban())
							.xAxisLabel(kpiMasterProjection.getxAxisLabel()).build();
				}).toList();

		Map<String, String> kpiIdsGroupedByCategory = this.kpiCategoryMappingRepository
				.findAllByKpiIdIn(kpiMasterList.stream().map(KpiMaster::getKpiId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(KpiCategoryMapping::getKpiId, KpiCategoryMapping::getCategoryId));

		kpiMasterList.forEach(kpi -> {
			if (kpiIdsGroupedByCategory.containsKey(kpi.getKpiId())) {
				kpi.setKpiCategory(kpiIdsGroupedByCategory.get(kpi.getKpiId()).toLowerCase());
			}
		});

		return kpiMasterList.stream()
				.filter(kpiMaster -> StringUtils.isNotEmpty(kpiMaster.getKpiCategory())
						&& this.kpiMaturityCalculationConfig.getCalculationConfig().getAllConfiguredCategories()
								.contains(kpiMaster.getKpiCategory().toLowerCase()))
				.toList();
	}

	/**
	 * Determines whether KPI maturity calculation is possible based on available
	 * data.
	 *
	 * <p>
	 * Maturity calculation requires at least one KPI element with a numeric overall
	 * maturity value. This method validates data availability before proceeding
	 * with expensive calculation operations.
	 *
	 * <p>
	 * <strong>Validation Criteria:</strong>
	 * <ul>
	 * <li>KPI elements list must not be null or empty</li>
	 * <li>At least one KPI element must have a numeric overallMaturity value</li>
	 * <li>Numeric validation uses {@link NumberUtils#isNumeric(String)}</li>
	 * </ul>
	 *
	 * @param kpiElementsResponse
	 *            the list of KPI elements retrieved from API
	 * @return {@code true} if maturity calculation can proceed, {@code false}
	 *         otherwise
	 */
	private static boolean kpiMaturityCanBeCalculated(List<KpiElement> kpiElementsResponse) {
		if (CollectionUtils.isEmpty(kpiElementsResponse)) {
			return false;
		}
		return kpiElementsResponse.stream().anyMatch(kpiElement -> isNumeric(kpiElement.getOverallMaturity()));
	}
}
