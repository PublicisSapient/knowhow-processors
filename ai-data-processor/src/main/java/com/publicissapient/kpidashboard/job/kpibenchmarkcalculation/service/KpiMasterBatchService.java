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

import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

/**
 * Service interface for batch processing of KPI master data. Provides functionality to retrieve KPI
 * data in manageable batches for benchmark calculation processing.
 *
 * @author kunkambl
 */
public interface KpiMasterBatchService {
	/**
	 * Retrieves the next KPI data for processing. Returns null when all KPIs have been
	 * processed.
	 *
	 * @return next KPI data, or null if no more KPIs available
	 */
	KpiDataDTO getNextKpiData();
}
