package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.kpibenchmark.KpiBenchmarkValuesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class KpiBenchmarkValuesPersistentServiceImplTest {

    @Mock
    private KpiBenchmarkValuesRepository repository;

    private KpiBenchmarkValuesPersistentServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new KpiBenchmarkValuesPersistentServiceImpl(repository);
    }

    @Test
    void testSaveKpiBenchmarkValues_NewRecord() {
        KpiBenchmarkValues newValue = createKpiBenchmarkValues("kpi1");
        List<KpiBenchmarkValues> valuesList = Arrays.asList(newValue);

        when(repository.findByKpiId("kpi1")).thenReturn(Optional.empty());

        service.saveKpiBenchmarkValues(valuesList);

        verify(repository).findByKpiId("kpi1");
        verify(repository).save(newValue);
    }

    @Test
    void testSaveKpiBenchmarkValues_ExistingRecord() {
        KpiBenchmarkValues existingValue = createKpiBenchmarkValues("kpi1");
        existingValue.setLastUpdatedTimestamp(1000L);
        
        KpiBenchmarkValues newValue = createKpiBenchmarkValues("kpi1");
        newValue.setLastUpdatedTimestamp(2000L);
        
        List<KpiBenchmarkValues> valuesList = Arrays.asList(newValue);

        when(repository.findByKpiId("kpi1")).thenReturn(Optional.of(existingValue));

        service.saveKpiBenchmarkValues(valuesList);

        verify(repository).findByKpiId("kpi1");
        verify(repository).save(existingValue);
        
        // Verify that existing record was updated
        assert existingValue.getLastUpdatedTimestamp() == 2000L;
        assert existingValue.getFilterWiseBenchmarkValues().equals(newValue.getFilterWiseBenchmarkValues());
    }

    @Test
    void testSaveKpiBenchmarkValues_MultipleRecords() {
        KpiBenchmarkValues value1 = createKpiBenchmarkValues("kpi1");
        KpiBenchmarkValues value2 = createKpiBenchmarkValues("kpi2");
        List<KpiBenchmarkValues> valuesList = Arrays.asList(value1, value2);

        when(repository.findByKpiId("kpi1")).thenReturn(Optional.empty());
        when(repository.findByKpiId("kpi2")).thenReturn(Optional.empty());

        service.saveKpiBenchmarkValues(valuesList);

        verify(repository).findByKpiId("kpi1");
        verify(repository).findByKpiId("kpi2");
        verify(repository).save(value1);
        verify(repository).save(value2);
    }

    @Test
    void testSaveKpiBenchmarkValues_EmptyList() {
        service.saveKpiBenchmarkValues(Arrays.asList());

        verifyNoInteractions(repository);
    }

    private KpiBenchmarkValues createKpiBenchmarkValues(String kpiId) {
        BenchmarkPercentiles percentiles = BenchmarkPercentiles.builder()
                .filter("Overall")
                .seventyPercentile(70.0)
                .eightyPercentile(80.0)
                .nintyPercentile(90.0)
                .build();

        return KpiBenchmarkValues.builder()
                .kpiId(kpiId)
                .filterWiseBenchmarkValues(Arrays.asList(percentiles))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .build();
    }
}