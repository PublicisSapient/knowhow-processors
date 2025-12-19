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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

/**
 * Service interface for processing KPI data and calculating benchmark values. Provides
 * functionality to compute percentile-based benchmarks for KPIs across different projects and
 * filter configurations.
 *
 * @author kunkambl
 */
public interface KpiBenchmarkProcessorService {

	/**
	 * Calculates benchmark values for a single KPI. Processes KPI data across all
	 * projects to compute 70th, 80th, and 90th percentiles.
	 *
	 * @param kpiDataDTO KPI data transfer object to process
	 * @return calculated benchmark values for the KPI
	 */
	KpiBenchmarkValues getKpiWiseBenchmarkValues(KpiDataDTO kpiDataDTO);
}
