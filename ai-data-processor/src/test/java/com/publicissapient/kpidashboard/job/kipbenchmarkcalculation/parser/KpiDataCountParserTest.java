package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import com.publicissapient.kpidashboard.common.model.application.DataCount;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KpiDataCountParserTest {

    private static class TestKpiDataCountParser extends KpiDataCountParser {
        @Override
        public Map<String, List<Double>> getKpiDataPoints(List<?> kpiDataList) {
            return extractDataPoints((List<DataCount>) kpiDataList);
        }
    }

    @Test
    void testExtractDataPoints_WithValidData() {
        TestKpiDataCountParser parser = new TestKpiDataCountParser();
        
        DataCount dataCount1 = new DataCount();
        dataCount1.setValue(10.5);
        dataCount1.setLineValue(20.3);
        
        DataCount dataCount2 = new DataCount();
        dataCount2.setValue("15.7");
        dataCount2.setLineValue("25.8");
        
        List<DataCount> dataCountList = Arrays.asList(dataCount1, dataCount2);
        Map<String, List<Double>> result = parser.extractDataPoints(dataCountList);
        
        assertEquals(2, result.size());
        assertTrue(result.containsKey("value"));
        assertTrue(result.containsKey("line_value"));
        assertEquals(Arrays.asList(10.5, 15.7), result.get("value"));
        assertEquals(Arrays.asList(20.3, 25.8), result.get("line_value"));
    }

    @Test
    void testExtractDataPoints_WithNullValues() {
        TestKpiDataCountParser parser = new TestKpiDataCountParser();
        
        DataCount dataCount1 = new DataCount();
        dataCount1.setValue(null);
        dataCount1.setLineValue(null);
        
        DataCount dataCount2 = null;
        
        List<DataCount> dataCountList = Arrays.asList(dataCount1, dataCount2);
        Map<String, List<Double>> result = parser.extractDataPoints(dataCountList);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractDataPoints_WithInvalidStringValue() {
        TestKpiDataCountParser parser = new TestKpiDataCountParser();
        
        DataCount dataCount = new DataCount();
        dataCount.setValue("invalid");
        dataCount.setLineValue("also_invalid");
        
        List<DataCount> dataCountList = Arrays.asList(dataCount);
        Map<String, List<Double>> result = parser.extractDataPoints(dataCountList);
        
        assertEquals(2, result.size());
        assertEquals(Arrays.asList((Double) null), result.get("value"));
        assertEquals(Arrays.asList((Double) null), result.get("line_value"));
    }

    @Test
    void testExtractDataPoints_WithOnlyValue() {
        TestKpiDataCountParser parser = new TestKpiDataCountParser();
        
        DataCount dataCount = new DataCount();
        dataCount.setValue(42.0);
        
        List<DataCount> dataCountList = Arrays.asList(dataCount);
        Map<String, List<Double>> result = parser.extractDataPoints(dataCountList);
        
        assertEquals(1, result.size());
        assertTrue(result.containsKey("value"));
        assertFalse(result.containsKey("line_value"));
        assertEquals(Arrays.asList(42.0), result.get("value"));
    }
}