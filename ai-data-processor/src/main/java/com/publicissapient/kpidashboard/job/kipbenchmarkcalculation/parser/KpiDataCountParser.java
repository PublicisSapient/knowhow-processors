package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import com.publicissapient.kpidashboard.common.model.application.DataCount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class KpiDataCountParser {

    private static final String VALUE = "value";
    private static final String LINE_VALUE = "line_value";

    public abstract Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList);

    protected Map<String, List<Double>> extractDataPoints(List<DataCount> dataCountList) {
        Map<String, List<Double>> valueList = new HashMap<>();
        dataCountList.forEach(dataCount -> {
            if (dataCount != null && dataCount.getValue() != null) {
                // Extract the numeric value from DataCount
                Object value = dataCount.getValue();
                Object lineValue = dataCount.getLineValue();
                valueList.computeIfAbsent(VALUE, k -> new ArrayList<>()).add(parseValue(value));
                if(lineValue != null)
                    valueList.computeIfAbsent(LINE_VALUE, k -> new ArrayList<>()).add(parseValue(lineValue));
            }
        });
        return valueList;
    }

    private Double parseValue(Object value) {
		if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                // Handle the case where the string cannot be parsed as a number
                return null;
            }
        }
        return null;
    }

}
