package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser.KpiDataCountParser;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser.KpiParserStrategy;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KpiBenchmarkProcessorServiceImplTest {

    @Mock
    private KpiParserStrategy kpiParserStrategy;
    @Mock
    private KnowHOWClient knowHOWClient;
    @Mock
    private ProjectBasicConfigRepository projectBasicConfigRepository;
    @Mock
    private KpiDataCountParser parser;

    private KpiBenchmarkProcessorServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new KpiBenchmarkProcessorServiceImpl(kpiParserStrategy, knowHOWClient, projectBasicConfigRepository);
    }

    @Test
    void testGetKpiWiseBenchmarkValues_WithValidData() {
        // Setup test data
        KpiDataDTO dto1 = KpiDataDTO.builder()
                .kpiId("kpi1")
                .kpiName("KPI 1")
                .chartType("line")
                .kpiFilter("dropdown")
                .build();

        List<KpiDataDTO> kpiDataDTOList = Arrays.asList(dto1);

        ProjectBasicConfig config = new ProjectBasicConfig();
        config.setProjectNodeId("project1");

        KpiElement kpiElement = new KpiElement();
        kpiElement.setKpiId("kpi1");
        kpiElement.setTrendValueList(Arrays.asList("data"));

        Map<String, List<Double>> dataPoints = new HashMap<>();
        dataPoints.put("value", Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0));

        // Setup mocks
        when(projectBasicConfigRepository.findAll()).thenReturn(Arrays.asList(config));
        when(knowHOWClient.getKpiIntegrationValues(any())).thenReturn(Arrays.asList(kpiElement));
        when(kpiParserStrategy.getParser(anyString())).thenReturn(parser);
        when(parser.getKpiDataPoints(any())).thenReturn(dataPoints);

        // Execute
        List<KpiBenchmarkValues> result = service.getKpiWiseBenchmarkValues(kpiDataDTOList);

        // Verify
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("kpi1", result.get(0).getKpiId());
        assertNotNull(result.get(0).getFilterWiseBenchmarkValues());
        assertTrue(result.get(0).getLastUpdatedTimestamp() > 0);
    }

    @Test
    void testGetKpiWiseBenchmarkValues_WithEmptyProjects() {
        KpiDataDTO dto = KpiDataDTO.builder().kpiId("kpi1").build();
        List<KpiDataDTO> kpiDataDTOList = Arrays.asList(dto);

        when(projectBasicConfigRepository.findAll()).thenReturn(Collections.emptyList());

        List<KpiBenchmarkValues> result = service.getKpiWiseBenchmarkValues(kpiDataDTOList);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetKpiWiseBenchmarkValues_WithNullTrendValueList() {
        KpiDataDTO dto = KpiDataDTO.builder()
                .kpiId("kpi1")
                .kpiFilter("dropdown")
                .build();

        List<KpiDataDTO> kpiDataDTOList = Arrays.asList(dto);

        ProjectBasicConfig config = new ProjectBasicConfig();
        config.setProjectNodeId("project1");

        KpiElement kpiElement = new KpiElement();
        kpiElement.setKpiId("kpi1");
        kpiElement.setTrendValueList(null);

        when(projectBasicConfigRepository.findAll()).thenReturn(Arrays.asList(config));
        when(knowHOWClient.getKpiIntegrationValues(any())).thenReturn(Arrays.asList(kpiElement));
        when(kpiParserStrategy.getParser("dropdown")).thenReturn(parser);

        List<KpiBenchmarkValues> result = service.getKpiWiseBenchmarkValues(kpiDataDTOList);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("kpi1", result.get(0).getKpiId());
        assertTrue(result.get(0).getFilterWiseBenchmarkValues().isEmpty());
    }

    @Test
    void testGetKpiWiseBenchmarkValues_WithMultipleKpis() {
        KpiDataDTO dto1 = KpiDataDTO.builder().kpiId("kpi1").kpiFilter("dropdown").build();
        KpiDataDTO dto2 = KpiDataDTO.builder().kpiId("kpi2").kpiFilter("radiobutton").build();
        List<KpiDataDTO> kpiDataDTOList = Arrays.asList(dto1, dto2);

        ProjectBasicConfig config = new ProjectBasicConfig();
        config.setProjectNodeId("project1");

        KpiElement element1 = new KpiElement();
        element1.setKpiId("kpi1");
        element1.setTrendValueList(Arrays.asList("data1"));

        KpiElement element2 = new KpiElement();
        element2.setKpiId("kpi2");
        element2.setTrendValueList(Arrays.asList("data2"));

        Map<String, List<Double>> dataPoints = new HashMap<>();
        dataPoints.put("value", Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0));

        when(projectBasicConfigRepository.findAll()).thenReturn(Arrays.asList(config));
        when(knowHOWClient.getKpiIntegrationValues(any())).thenReturn(Arrays.asList(element1, element2));
        when(kpiParserStrategy.getParser(anyString())).thenReturn(parser);
        when(parser.getKpiDataPoints(any())).thenReturn(dataPoints);

        List<KpiBenchmarkValues> result = service.getKpiWiseBenchmarkValues(kpiDataDTOList);

        assertNotNull(result);
        assertEquals(2, result.size());
    }
}