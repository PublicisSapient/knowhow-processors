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

import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;

import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiMasterBatchService;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.repository.application.KpiMasterRepository;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KpiMasterBatchServiceImpl implements KpiMasterBatchService {

    private final KpiMasterRepository kpiMasterRepository;

	private static final List<String> CHART_TYPES = Arrays.asList("line", "grouped_column_plus_line");

    private KpiBatchProcessingParameters kpiBatchProcessingParameters;

    public KpiMasterBatchServiceImpl(KpiMasterRepository kpiMasterRepository) {
        this.kpiMasterRepository = kpiMasterRepository;
    }

    @Builder
    private static class KpiBatchProcessingParameters {
        private int currentIndex;
        private boolean shouldStartANewBatchProcess;
        private List<KpiDataDTO> allKpiData;
        private int batchSize;
    }

    @PostConstruct
    private void initializeBatchProcessingParameters() {
        this.kpiBatchProcessingParameters = KpiBatchProcessingParameters.builder()
                .currentIndex(0)
                .shouldStartANewBatchProcess(true)
                .batchSize(10)
                .build();
    }

    public List<KpiDataDTO> getNextKpiDataBatch() {
        if (this.kpiBatchProcessingParameters.shouldStartANewBatchProcess) {
            initializeANewBatchProcess();
            this.kpiBatchProcessingParameters.shouldStartANewBatchProcess = false;
        }

        if (this.kpiBatchProcessingParameters.allKpiData == null ||
                this.kpiBatchProcessingParameters.currentIndex >= this.kpiBatchProcessingParameters.allKpiData.size()) {
            return null;
        }

        int endIndex = Math.min(
                this.kpiBatchProcessingParameters.currentIndex + this.kpiBatchProcessingParameters.batchSize,
                this.kpiBatchProcessingParameters.allKpiData.size()
        );

        List<KpiDataDTO> batch = this.kpiBatchProcessingParameters.allKpiData
                .subList(this.kpiBatchProcessingParameters.currentIndex, endIndex);

        this.kpiBatchProcessingParameters.currentIndex = endIndex;
        return batch;
    }

    private void initializeANewBatchProcess() {
        try {
            long count = kpiMasterRepository.count();
            log.info("Total KpiMaster count in database: {}", count);
            
            List<KpiMaster> kpiMasters = (List<KpiMaster>) kpiMasterRepository.findAll();
            log.info("Found {} KpiMaster records in database", kpiMasters.size());
            
            if (!kpiMasters.isEmpty()) {
                log.info("Sample KpiMaster: id={}, name={}, chartType={}", 
                        kpiMasters.get(0).getKpiId(), 
                        kpiMasters.get(0).getKpiName(), 
                        kpiMasters.get(0).getChartType());
            }
            
            this.kpiBatchProcessingParameters.allKpiData = kpiMasters.stream()
                    .filter(kpiMaster -> CHART_TYPES.contains(kpiMaster.getChartType()))
                    .map(this::convertToKpiData)
                    .toList();
            
            log.info("Filtered to {} KpiDataDTO records with chart types: {}", 
                    this.kpiBatchProcessingParameters.allKpiData.size(), CHART_TYPES);
            
        } catch (Exception e) {
            log.error("Error accessing KpiMaster repository: {}", e.getMessage(), e);
        }
        
        this.kpiBatchProcessingParameters.currentIndex = 0;
    }

    private KpiDataDTO convertToKpiData(KpiMaster kpiMaster) {
        return KpiDataDTO.builder()
                .kpiId(kpiMaster.getKpiId())
                .kpiName(kpiMaster.getKpiName())
                .chartType(kpiMaster.getChartType())
                .kpiFilter(kpiMaster.getKpiFilter())
                .build();
    }
}