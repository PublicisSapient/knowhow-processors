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

package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.reader;

import java.util.List;

import org.springframework.batch.item.ItemReader;

import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiMasterBatchService;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class KpiItemReader implements ItemReader<List<KpiDataDTO>> {

	private final KpiMasterBatchService kpiMasterBatchService;

	@Override
	public List<KpiDataDTO> read() {
		List<KpiDataDTO> kpiDataBatch = kpiMasterBatchService.getNextKpiDataBatch();

		log.info(
				"[kpi-benchmark-calculation job] Received kpi data batch with {} items",
				kpiDataBatch != null ? kpiDataBatch.size() : 0);

		return kpiDataBatch;
	}
}
