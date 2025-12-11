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

package com.publicissapient.kpidashboard.job.kpimaturitycalculation.writer;

import java.util.List;

import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KpiMaturity;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.service.KpiMaturityCalculationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ProjectItemWriter implements ItemWriter<KpiMaturity> {

	private final KpiMaturityCalculationService kpiMaturityCalculationService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Override
	public void write(@NonNull Chunk<? extends KpiMaturity> chunk) {
		log.info("{} Received chunk items for inserting into database with size: {}",
				JobConstants.LOG_PREFIX_KPI_MATURITY, chunk.size());
		kpiMaturityCalculationService.saveAll((List<KpiMaturity>) chunk.getItems());
	}
}
