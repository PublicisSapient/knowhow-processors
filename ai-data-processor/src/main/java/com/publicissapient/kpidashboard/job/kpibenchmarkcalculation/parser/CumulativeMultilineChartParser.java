package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.client.customapi.dto.IterationKpiValue;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;

@Component
public class CumulativeMultilineChartParser extends KpiDataCountParser {
	@Override
	public Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList) {
		Map<String, List<Double>> valueList = new HashMap<>();

		if (kpiDataList == null || kpiDataList.isEmpty()) {
			return valueList;
		}
		Optional<List<DataCountGroup>> dataCountGroupverAll =
				((List<IterationKpiValue>) kpiDataList)
						.stream()
								.filter(
										dataCountGroup ->
												dataCountGroup.getFilter1() != null
														&& dataCountGroup.getFilter1().equalsIgnoreCase("Overall"))
								.map(IterationKpiValue::getDataGroup)
								.findFirst();

		if (dataCountGroupverAll.isPresent()
				&& CollectionUtils.isNotEmpty(dataCountGroupverAll.get())) {
			List<DataCount> dataCountList =
					dataCountGroupverAll.get().stream()
							.flatMap(dataCountGroup -> dataCountGroup.getValue().stream())
							.toList();
			return extractDataPoints(dataCountList);
		}
		return valueList;
	}
}
