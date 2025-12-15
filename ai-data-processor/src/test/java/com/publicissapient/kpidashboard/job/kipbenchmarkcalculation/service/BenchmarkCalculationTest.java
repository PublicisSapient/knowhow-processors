package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkCalculationTest {

    @Test
    void testPercentile_WithValidData() {
        List<Double> values = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);
        
        assertEquals(7.0, BenchmarkCalculation.percentile(values, 70));
        assertEquals(8.0, BenchmarkCalculation.percentile(values, 80));
        assertEquals(9.0, BenchmarkCalculation.percentile(values, 90));
    }

    @Test
    void testPercentile_WithSingleValue() {
        List<Double> values = Arrays.asList(5.0);
        
        assertEquals(5.0, BenchmarkCalculation.percentile(values, 50));
        assertEquals(5.0, BenchmarkCalculation.percentile(values, 90));
    }

    @Test
    void testPercentile_WithUnsortedData() {
        List<Double> values = Arrays.asList(10.0, 1.0, 5.0, 3.0, 8.0);
        
        assertEquals(8.0, BenchmarkCalculation.percentile(values, 80));
    }

    @Test
    void testPercentile_WithDuplicateValues() {
        List<Double> values = Arrays.asList(1.0, 2.0, 2.0, 3.0, 3.0, 3.0, 4.0);
        
        assertEquals(3.0, BenchmarkCalculation.percentile(values, 70));
    }

    @Test
    void testPercentile_WithNullList() {
        assertThrows(IllegalArgumentException.class, () -> 
            BenchmarkCalculation.percentile(null, 50));
    }

    @Test
    void testPercentile_WithEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> 
            BenchmarkCalculation.percentile(Collections.emptyList(), 50));
    }

    @Test
    void testPercentile_WithHundredPercentile() {
        List<Double> values = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        
        assertEquals(5.0, BenchmarkCalculation.percentile(values, 100));
    }
}