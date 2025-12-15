package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl;

import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.repository.application.KpiMasterRepository;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;

class KpiMasterBatchServiceImplTest {

    @Mock
    private KpiMasterRepository kpiMasterRepository;

    private KpiMasterBatchServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new KpiMasterBatchServiceImpl(kpiMasterRepository);
        
        // Call private @PostConstruct method using reflection
        Method initMethod = KpiMasterBatchServiceImpl.class.getDeclaredMethod("initializeBatchProcessingParameters");
        initMethod.setAccessible(true);
        initMethod.invoke(service);
    }

    @Test
    void testGetNextKpiDataBatch_FirstCall() {
        KpiMaster kpiMaster1 = createKpiMaster("kpi1", "KPI 1", "line", "dropdown");
        KpiMaster kpiMaster2 = createKpiMaster("kpi2", "KPI 2", "grouped_column_plus_line", null);
        KpiMaster kpiMaster3 = createKpiMaster("kpi3", "KPI 3", "bar", "radiobutton");

        when(kpiMasterRepository.count()).thenReturn(3L);
        when(kpiMasterRepository.findAll()).thenReturn(Arrays.asList(kpiMaster1, kpiMaster2, kpiMaster3));

        List<KpiDataDTO> result = service.getNextKpiDataBatch();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("kpi1", result.get(0).kpiId());
        assertEquals("kpi2", result.get(1).kpiId());
    }

    @Test
    void testGetNextKpiDataBatch_SecondCall() {
        KpiMaster kpiMaster = createKpiMaster("kpi1", "KPI 1", "line", "dropdown");
        when(kpiMasterRepository.count()).thenReturn(1L);
        when(kpiMasterRepository.findAll()).thenReturn(Arrays.asList(kpiMaster));

        // First call
        service.getNextKpiDataBatch();
        
        // Second call should return null
        List<KpiDataDTO> result = service.getNextKpiDataBatch();
        assertNull(result);
    }

    @Test
    void testGetNextKpiDataBatch_EmptyRepository() {
        when(kpiMasterRepository.count()).thenReturn(0L);
        when(kpiMasterRepository.findAll()).thenReturn(Collections.emptyList());

        List<KpiDataDTO> result = service.getNextKpiDataBatch();
        assertNull(result);
    }

    @Test
    void testGetNextKpiDataBatch_OnlyUnsupportedChartTypes() {
        KpiMaster kpiMaster = createKpiMaster("kpi1", "KPI 1", "pie", "dropdown");
        when(kpiMasterRepository.count()).thenReturn(1L);
        when(kpiMasterRepository.findAll()).thenReturn(Arrays.asList(kpiMaster));

        List<KpiDataDTO> result = service.getNextKpiDataBatch();
        assertNull(result);
    }

    @Test
    void testGetNextKpiDataBatch_ExceptionHandling() {
        when(kpiMasterRepository.count()).thenThrow(new RuntimeException("Database error"));

        List<KpiDataDTO> result = service.getNextKpiDataBatch();
        assertNull(result);
    }

    @Test
    void testConvertToKpiData() {
        KpiMaster kpiMaster = createKpiMaster("kpi1", "KPI 1", "line", "dropdown");
        when(kpiMasterRepository.count()).thenReturn(1L);
        when(kpiMasterRepository.findAll()).thenReturn(Arrays.asList(kpiMaster));

        List<KpiDataDTO> result = service.getNextKpiDataBatch();

        assertNotNull(result);
        assertEquals(1, result.size());
        KpiDataDTO dto = result.get(0);
        assertEquals("kpi1", dto.kpiId());
        assertEquals("KPI 1", dto.kpiName());
        assertEquals("line", dto.chartType());
        assertEquals("dropdown", dto.kpiFilter());
    }

    private KpiMaster createKpiMaster(String kpiId, String kpiName, String chartType, String kpiFilter) {
        KpiMaster kpiMaster = new KpiMaster();
        kpiMaster.setKpiId(kpiId);
        kpiMaster.setKpiName(kpiName);
        kpiMaster.setChartType(chartType);
        kpiMaster.setKpiFilter(kpiFilter);
        return kpiMaster;
    }
}