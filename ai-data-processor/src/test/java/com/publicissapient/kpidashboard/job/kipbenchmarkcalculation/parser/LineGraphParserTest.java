package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import com.publicissapient.kpidashboard.common.model.application.DataCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LineGraphParserTest {

    private LineGraphParser parser;

    @BeforeEach
    void setUp() {
        parser = new LineGraphParser();
    }

    @Test
    void testGetKpiDataPoints_WithValidData() {
        DataCount innerDataCount1 = new DataCount();
        innerDataCount1.setValue(10.5);
        innerDataCount1.setLineValue(20.3);
        
        DataCount innerDataCount2 = new DataCount();
        innerDataCount2.setValue(15.7);
        
        List<DataCount> innerDataCountList = Arrays.asList(innerDataCount1, innerDataCount2);
        
        DataCount outerDataCount = new DataCount();
        outerDataCount.setValue(innerDataCountList);
        
        List<DataCount> kpiDataTrendValueList = Arrays.asList(outerDataCount);
        
        Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataTrendValueList);
        
        assertEquals(2, result.size());
        assertTrue(result.containsKey("value"));
        assertTrue(result.containsKey("line_value"));
        assertEquals(Arrays.asList(10.5, 15.7), result.get("value"));
        assertEquals(Arrays.asList(20.3), result.get("line_value"));
    }

    @Test
    void testGetKpiDataPoints_WithNullList() {
        Map<String, List<Double>> result = parser.getKpiDataPoints(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetKpiDataPoints_WithEmptyList() {
        Map<String, List<Double>> result = parser.getKpiDataPoints(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetKpiDataPoints_WithEmptyInnerData() {
        DataCount outerDataCount = new DataCount();
        outerDataCount.setValue(Collections.emptyList());
        
        List<DataCount> kpiDataTrendValueList = Arrays.asList(outerDataCount);
        
        Map<String, List<Double>> result = parser.getKpiDataPoints(kpiDataTrendValueList);
        assertTrue(result.isEmpty());
    }
}