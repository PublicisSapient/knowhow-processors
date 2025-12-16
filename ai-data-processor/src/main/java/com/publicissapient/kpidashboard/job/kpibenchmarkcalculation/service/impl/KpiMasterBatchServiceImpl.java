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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.repository.application.KpiMasterRepository;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiMasterBatchService;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of KpiMasterBatchService for batch processing of KPI master data. Manages batch
 * retrieval of KPI data with configurable batch sizes and filters KPIs based on supported chart
 * types for benchmark calculations.
 *
 * @author kunkambl
 */
@Slf4j
@Service
public class KpiMasterBatchServiceImpl implements KpiMasterBatchService {

	private final KpiMasterRepository kpiMasterRepository;

	private static final List<String> CHART_TYPES = Arrays.asList("line", "grouped_column_plus_line");

	private KpiBatchProcessingParameters kpiBatchProcessingParameters;

	/**
	 * Constructs the batch service with KPI master repository dependency.
	 *
	 * @param kpiMasterRepository repository for accessing KPI master data
	 */
	public KpiMasterBatchServiceImpl(KpiMasterRepository kpiMasterRepository) {
		this.kpiMasterRepository = kpiMasterRepository;
	}

	/** Internal class for managing batch processing state and parameters. */
	@Builder
	private static class KpiBatchProcessingParameters {
		private int currentIndex;
		private boolean shouldStartANewBatchProcess;
		private List<KpiDataDTO> allKpiData;
		private int batchSize;
	}

	/** Initializes batch processing parameters after bean construction. */
	@PostConstruct
	private void initializeBatchProcessingParameters() {
		this.kpiBatchProcessingParameters =
				KpiBatchProcessingParameters.builder()
						.currentIndex(0)
						.shouldStartANewBatchProcess(true)
						.batchSize(10)
						.build();
	}

	/** {@inheritDoc} */
	public List<KpiDataDTO> getNextKpiDataBatch() {
		if (this.kpiBatchProcessingParameters.shouldStartANewBatchProcess) {
			initializeANewBatchProcess();
			this.kpiBatchProcessingParameters.shouldStartANewBatchProcess = false;
		}

		if (this.kpiBatchProcessingParameters.allKpiData == null
				|| this.kpiBatchProcessingParameters.currentIndex
						>= this.kpiBatchProcessingParameters.allKpiData.size()) {
			return Collections.emptyList();
		}

		int endIndex =
				Math.min(
						this.kpiBatchProcessingParameters.currentIndex
								+ this.kpiBatchProcessingParameters.batchSize,
						this.kpiBatchProcessingParameters.allKpiData.size());

		List<KpiDataDTO> batch =
				this.kpiBatchProcessingParameters.allKpiData.subList(
						this.kpiBatchProcessingParameters.currentIndex, endIndex);

		this.kpiBatchProcessingParameters.currentIndex = endIndex;
		return batch;
	}

	/**
	 * Initializes a new batch processing cycle by loading all eligible KPI data. Filters KPIs based
	 * on supported chart types for benchmark calculations.
	 */
	private void initializeANewBatchProcess() {
		try {
			long count = kpiMasterRepository.count();
			log.info("Total KpiMaster count in database: {}", count);

			List<KpiMaster> kpiMasters = (List<KpiMaster>) kpiMasterRepository.findAll();
			log.info("Found {} KpiMaster records in database", kpiMasters.size());

			if (!kpiMasters.isEmpty()) {
				log.info(
						"Sample KpiMaster: id={}, name={}, chartType={}",
						kpiMasters.get(0).getKpiId(),
						kpiMasters.get(0).getKpiName(),
						kpiMasters.get(0).getChartType());
			}

			this.kpiBatchProcessingParameters.allKpiData =
					kpiMasters.stream()
							.filter(kpiMaster -> CHART_TYPES.contains(kpiMaster.getChartType()))
							.map(this::convertToKpiData)
							.toList();

			log.info(
					"Filtered to {} KpiDataDTO records with chart types: {}",
					this.kpiBatchProcessingParameters.allKpiData.size(),
					CHART_TYPES);

		} catch (Exception e) {
			log.error("Error accessing KpiMaster repository: {}", e.getMessage(), e);
		}

		this.kpiBatchProcessingParameters.currentIndex = 0;
	}

	/**
	 * Converts KpiMaster entity to KpiDataDTO for processing.
	 *
	 * @param kpiMaster the KPI master entity to convert
	 * @return converted KPI data transfer object
	 */
	private KpiDataDTO convertToKpiData(KpiMaster kpiMaster) {
		return KpiDataDTO.builder()
				.kpiId(kpiMaster.getKpiId())
				.kpiName(kpiMaster.getKpiName())
				.chartType(kpiMaster.getChartType())
				.kpiFilter(kpiMaster.getKpiFilter())
				.build();
	}
}
