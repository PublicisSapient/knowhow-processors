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

package com.publicissapient.kpidashboard.job.shared.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.thymeleaf.util.StringUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KpiGranularity {
	SPRINT(List.of("sprints", "sprint")), ITERATION(List.of("iteration", "current_sprint")), MONTH(
			List.of("months", "month")), WEEK(List.of("weeks", "week")), DAY(
					List.of("days", "day")), PI(List.of("pis", "pi")), NONE(Collections.emptyList());

	private final List<String> granularityNameVariations;

	public static KpiGranularity getByKpiXAxisLabel(String xAxisLabel) {
		if (StringUtils.isEmpty(xAxisLabel)) {
			return NONE;
		}
		return Arrays.stream(KpiGranularity.values())
				.filter(kpiGranularity -> kpiGranularity.granularityNameVariations.contains(xAxisLabel.toLowerCase()))
				.findFirst().orElse(NONE);
	}
}
