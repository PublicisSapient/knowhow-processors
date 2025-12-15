package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class LineGraphParser extends KpiDataCountParser {

    @Override
	public Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataTrendValueList) {

		if (kpiDataTrendValueList == null || kpiDataTrendValueList.isEmpty()) {
			return Collections.emptyMap();
		}

		// Find the "Overall" data group and extract its values
		Optional<DataCount> kpiDataListOptional = ((List<DataCount>) kpiDataTrendValueList).stream().findFirst();

		List<DataCount> dataCountList = (List<DataCount>) kpiDataListOptional.get().getValue();
		// Process the data points if found
		return extractDataPoints(dataCountList);

	}
}
