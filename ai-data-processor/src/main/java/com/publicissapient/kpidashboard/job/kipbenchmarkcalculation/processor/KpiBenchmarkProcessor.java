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

package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.processor;

import java.util.List;

import org.springframework.batch.item.ItemProcessor;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiBenchmarkProcessorService;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl.KpiBenchmarkProcessorServiceImpl;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

public class KpiBenchmarkProcessor
		implements ItemProcessor<List<KpiDataDTO>, List<KpiBenchmarkValues>> {

	private final KpiBenchmarkProcessorService processorService;

	public KpiBenchmarkProcessor(KpiBenchmarkProcessorServiceImpl processorService) {
		this.processorService = processorService;
	}

	@Override
	public List<KpiBenchmarkValues> process(List<KpiDataDTO> item) throws Exception {
		List<KpiBenchmarkValues> kpiBenchmarkValuesList =
				processorService.getKpiWiseBenchmarkValues(item);
		return kpiBenchmarkValuesList;
	}
}
