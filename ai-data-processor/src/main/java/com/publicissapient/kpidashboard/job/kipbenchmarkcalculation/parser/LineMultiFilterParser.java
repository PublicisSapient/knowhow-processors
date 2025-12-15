package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LineMultiFilterParser extends KpiDataCountParser {
    @Override
    public Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList) {
        Map<String, List<Double>> valueList = new HashMap<>();

        if (kpiDataList == null || kpiDataList.isEmpty()) {
            return valueList;
        }
        ((List<DataCountGroup>) kpiDataList).forEach(dataCountGroup -> {
            if (dataCountGroup != null && dataCountGroup.getValue() != null) {
                List<DataCount> dataCountList = (List<DataCount>) dataCountGroup.getValue();
                if(CollectionUtils.isNotEmpty(dataCountList)) {
                    Map<String, List<Double>> dataPoints = extractDataPoints((List<DataCount>) dataCountList.get(0).getValue());
                    dataPoints.forEach((key, value) -> valueList.put(key + "#" + dataCountGroup.getFilter1() + "#" + dataCountGroup.getFilter2(), value));
                }
            }
        });

        return valueList;
    }
}
