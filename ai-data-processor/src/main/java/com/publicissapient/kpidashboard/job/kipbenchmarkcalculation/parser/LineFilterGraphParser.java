package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class LineFilterGraphParser extends KpiDataCountParser {

    @Override
    public Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList) {
        Map<String, List<Double>> valueList = new HashMap<>();

        if (kpiDataList == null || kpiDataList.isEmpty()) {
            return valueList;
        }

        Optional<List<DataCount>> dataCountOverAll = ((List<DataCountGroup>) kpiDataList).stream().filter(dataCountGroup -> dataCountGroup.getFilter()!=null && dataCountGroup.getFilter().equalsIgnoreCase("Overall")).map(DataCountGroup::getValue).findFirst();

        if(dataCountOverAll.isPresent() && CollectionUtils.isNotEmpty(dataCountOverAll.get())) {
            List<DataCount> dataCountList = (List<DataCount>) dataCountOverAll.get().get(0).getValue();
            return extractDataPoints(dataCountList);
        }

        return valueList;
    }
}
