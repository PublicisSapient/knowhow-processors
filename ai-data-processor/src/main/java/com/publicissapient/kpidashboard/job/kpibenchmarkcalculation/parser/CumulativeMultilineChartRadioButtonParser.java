package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.client.customapi.dto.IterationKpiValue;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;

@Component
public class CumulativeMultilineChartRadioButtonParser extends KpiDataCountParser {
	@Override
	public Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList) {
		Map<String, List<Double>> valueList = new HashMap<>();

		if (kpiDataList == null || kpiDataList.isEmpty()) {
			return valueList;
		}
		((List<IterationKpiValue>) kpiDataList)
				.forEach(
						dataCountGroup -> {
							if (dataCountGroup != null && dataCountGroup.getDataGroup() != null) {
								List<DataCountGroup> dataCountGroupList = dataCountGroup.getDataGroup();
								if (CollectionUtils.isNotEmpty(dataCountGroupList)) {
									List<DataCount> dataCountList =
											dataCountGroupList.stream()
													.flatMap(dataGroup -> dataGroup.getValue().stream())
													.toList();
									if (!dataCountList.isEmpty()) {
										Map<String, List<Double>> dataPoints = extractDataPoints(dataCountList);
										dataPoints.forEach(
												(key, value) ->
														valueList.put(key + "#" + dataCountGroup.getFilter1(), value));
									}
								}
							}
						});

		return valueList;
	}
}
