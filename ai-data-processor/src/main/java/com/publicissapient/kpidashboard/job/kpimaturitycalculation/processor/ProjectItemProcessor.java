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

package com.publicissapient.kpidashboard.job.kpimaturitycalculation.processor;

import com.publicissapient.kpidashboard.job.constant.JobConstants;
import org.springframework.batch.item.ItemProcessor;

import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KpiMaturity;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.service.KpiMaturityCalculationService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ProjectItemProcessor implements ItemProcessor<ProjectInputDTO, KpiMaturity> {

    private final KpiMaturityCalculationService kpiMaturityCalculationService;

    @Override
    public KpiMaturity process(@Nonnull ProjectInputDTO item) {
        log.info("{} Starting kpi metrics calculation for project with nodeId: {} and deliveryMethodology: {}", JobConstants.LOG_PREFIX_KPI_MATURITY, item.nodeId(), item
                .deliveryMethodology());

        return kpiMaturityCalculationService.calculateKpiMaturityForProject(item);
    }
}
