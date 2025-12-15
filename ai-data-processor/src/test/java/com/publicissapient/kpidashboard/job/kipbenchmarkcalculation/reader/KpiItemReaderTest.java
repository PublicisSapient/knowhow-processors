package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.reader;

import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiMasterBatchService;
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

class KpiItemReaderTest {

    @Mock
    private KpiMasterBatchService kpiMasterBatchService;

    private KpiItemReader reader;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reader = new KpiItemReader(kpiMasterBatchService);
    }

    @Test
    void testRead_WithValidBatch() {
        KpiDataDTO dto1 = KpiDataDTO.builder()
                .kpiId("kpi1")
                .kpiName("KPI 1")
                .chartType("line")
                .kpiFilter("dropdown")
                .build();
        
        KpiDataDTO dto2 = KpiDataDTO.builder()
                .kpiId("kpi2")
                .kpiName("KPI 2")
                .chartType("grouped_column_plus_line")
                .kpiFilter("radiobutton")
                .build();

        List<KpiDataDTO> expectedBatch = Arrays.asList(dto1, dto2);
        when(kpiMasterBatchService.getNextKpiDataBatch()).thenReturn(expectedBatch);

        List<KpiDataDTO> result = reader.read();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("kpi1", result.get(0).kpiId());
        assertEquals("kpi2", result.get(1).kpiId());
        verify(kpiMasterBatchService).getNextKpiDataBatch();
    }

    @Test
    void testRead_WithEmptyBatch() {
        List<KpiDataDTO> emptyBatch = Collections.emptyList();
        when(kpiMasterBatchService.getNextKpiDataBatch()).thenReturn(emptyBatch);

        List<KpiDataDTO> result = reader.read();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(kpiMasterBatchService).getNextKpiDataBatch();
    }

    @Test
    void testRead_WithNullBatch() {
        when(kpiMasterBatchService.getNextKpiDataBatch()).thenReturn(null);

        List<KpiDataDTO> result = reader.read();

        assertNull(result);
        verify(kpiMasterBatchService).getNextKpiDataBatch();
    }

    @Test
    void testRead_ServiceThrowsException() {
        when(kpiMasterBatchService.getNextKpiDataBatch()).thenThrow(new RuntimeException("Service error"));

        assertThrows(RuntimeException.class, () -> reader.read());
        verify(kpiMasterBatchService).getNextKpiDataBatch();
    }
}