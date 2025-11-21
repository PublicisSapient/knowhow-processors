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

@Slf4j
@Service
@RequiredArgsConstructor
public class KpiMaturityCalculationService {
	private static final double EFFICIENCY_MAX_SCORE = 5.0D;

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

	public KpiMaturity calculateKpiMaturityForProject(ProjectInputDTO projectInput) {
		if (CollectionUtils
				.isNotEmpty(kpiMaturityCalculationConfig.getCalculationConfig().getConfigValidationErrors())) {
			throw new IllegalStateException(String.format("The following config validations errors occurred: %s",
					String.join(CommonConstant.COMMA,
							kpiMaturityCalculationConfig.getCalculationConfig().getConfigValidationErrors())));
		}
		List<KpiRequest> kpiRequests = constructKpiRequests(projectInput);
		List<KpiElement> kpiElementList = processAllKpiRequests(kpiRequests);

		return calculateKpiMaturity(projectInput, kpiElementList);
	}

	private List<KpiElement> processAllKpiRequests(List<KpiRequest> kpiRequests) {
		return this.knowHOWClient.getKpiIntegrationValues(kpiRequests);
	}

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

	private List<KpiRequest> constructKpiRequests(ProjectInputDTO projectInput) {
		List<KpiRequest> kpiRequests = new ArrayList<>();

		Map<String, List<KpiMaster>> kpisGroupedBySource = this.kpisEligibleForMaturityCalculation.stream()
				.collect(Collectors.groupingBy(KpiMaster::getKpiSource));

		for (Map.Entry<String, List<KpiMaster>> entry : kpisGroupedBySource.entrySet()) {
			KpiGranularity kpiGranularity = KpiGranularity.getByKpiXAxisLabel(entry.getValue().get(0).getXAxisLabel());
			switch (kpiGranularity) {
			case MONTH, WEEK, DAY -> kpiRequests.add(KpiRequest.builder()
					.kpiIdList(new ArrayList<>(entry.getValue().stream().map(KpiMaster::getKpiId).toList()))
					.selectedMap(Map.of(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, List.of(projectInput.nodeId()),
							CommonConstant.DATE, List.of("Weeks")))
					.ids(new String[] { String.valueOf(
							this.kpiMaturityCalculationConfig.getCalculationConfig().getDataPoints().getCount()) })
					.level(projectInput.hierarchyLevel()).label(projectInput.hierarchyLevelId()).build());
			case SPRINT, ITERATION, NONE,
					PI ->
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
		return kpiRequests;
	}

	private List<KpiMaster> loadKpisEligibleForMaturityCalculation() {
		List<KpiMaster> kpiMasterList = this.kpiMasterCustomRepository
				.findByDeliveryMethodologyTypeSupportingMaturityCalculation(ProjectDeliveryMethodology.SCRUM).stream()
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

	private static boolean kpiMaturityCanBeCalculated(List<KpiElement> kpiElementsResponse) {
		if (CollectionUtils.isEmpty(kpiElementsResponse)) {
			return false;
		}
		return kpiElementsResponse.stream().anyMatch(kpiElement -> isNumeric(kpiElement.getOverallMaturity()));
	}
}
