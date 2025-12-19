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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiBenchmarkValuesPersistentService;

public class KpiBenchmarkValuesWriter implements ItemWriter<KpiBenchmarkValues> {

	private final KpiBenchmarkValuesPersistentService kpiBenchmarkValuesPersistentService;

	public KpiBenchmarkValuesWriter(
			KpiBenchmarkValuesPersistentService kpiBenchmarkValuesPersistentService) {
		this.kpiBenchmarkValuesPersistentService = kpiBenchmarkValuesPersistentService;
	}

	@Override
	public void write(Chunk<? extends KpiBenchmarkValues> chunk) throws Exception {
		chunk.forEach(kpiBenchmarkValuesPersistentService::saveKpiBenchmarkValues);
	}
}
