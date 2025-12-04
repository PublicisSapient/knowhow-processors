/*
 *  Copyright 2024 Sapient Corporation
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

package com.publicissapient.kpidashboard.job.constant;

import lombok.experimental.UtilityClass;

/**
 * Constants used across AI Data Processor jobs.
 */
@UtilityClass
public final class AiDataProcessorConstants {

	public static final String PRODUCTIVITY_JOB = "Productivity";
	public static final String KPI_MATURITY_JOB = "KpiMaturity";
	public static final String AI_USAGE_STATISTICS_JOB = "AIUsageStatistics";
	public static final String RECOMMENDATION_JOB = "Recommendation";

	public static final String LOG_PREFIX_RECOMMENDATION = "[recommendation-calculation job]";
	public static final String LOG_PREFIX_PRODUCTIVITY = "[productivity-calculation job]";
	public static final String LOG_PREFIX_KPI_MATURITY = "[kpi-maturity-calculation job]";
	public static final String LOG_PREFIX_AI_USAGE_STATISTICS = "[ai-usage-statistics-collector job]";

}
