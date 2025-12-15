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

package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;

@Component
public class LineFilterGraphParser extends KpiDataCountParser {

	@Override
	public Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList) {
		Map<String, List<Double>> valueList = new HashMap<>();

		if (kpiDataList == null || kpiDataList.isEmpty()) {
			return valueList;
		}

		Optional<List<DataCount>> dataCountOverAll =
				((List<DataCountGroup>) kpiDataList)
						.stream()
								.filter(
										dataCountGroup ->
												dataCountGroup.getFilter() != null
														&& dataCountGroup.getFilter().equalsIgnoreCase("Overall"))
								.map(DataCountGroup::getValue)
								.findFirst();

		if (dataCountOverAll.isPresent() && CollectionUtils.isNotEmpty(dataCountOverAll.get())) {
			List<DataCount> dataCountList = (List<DataCount>) dataCountOverAll.get().get(0).getValue();
			return extractDataPoints(dataCountList);
		}

		return valueList;
	}
}
