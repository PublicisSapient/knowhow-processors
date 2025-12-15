package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.writer;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.BenchmarkPercentiles;
import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiBenchmarkValuesPersistentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.item.Chunk;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class KpiBenchmarkValuesWriterTest {

    @Mock
    private KpiBenchmarkValuesPersistentService persistentService;

    private KpiBenchmarkValuesWriter writer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        writer = new KpiBenchmarkValuesWriter(persistentService);
    }

    @Test
    void testWrite_WithValidChunk() throws Exception {
        BenchmarkPercentiles percentiles = BenchmarkPercentiles.builder()
                .filter("Overall")
                .seventyPercentile(70.0)
                .eightyPercentile(80.0)
                .nintyPercentile(90.0)
                .build();

        KpiBenchmarkValues values1 = KpiBenchmarkValues.builder()
                .kpiId("kpi1")
                .filterWiseBenchmarkValues(Arrays.asList(percentiles))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .build();

        KpiBenchmarkValues values2 = KpiBenchmarkValues.builder()
                .kpiId("kpi2")
                .filterWiseBenchmarkValues(Arrays.asList(percentiles))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .build();

        List<KpiBenchmarkValues> list1 = Arrays.asList(values1);
        List<KpiBenchmarkValues> list2 = Arrays.asList(values2);

        Chunk<List<KpiBenchmarkValues>> chunk = new Chunk<>(Arrays.asList(list1, list2));

        writer.write(chunk);

        verify(persistentService).saveKpiBenchmarkValues(list1);
        verify(persistentService).saveKpiBenchmarkValues(list2);
    }

    @Test
    void testWrite_WithEmptyChunk() throws Exception {
        Chunk<List<KpiBenchmarkValues>> emptyChunk = new Chunk<>(Collections.emptyList());

        writer.write(emptyChunk);

        verifyNoInteractions(persistentService);
    }

    @Test
    void testWrite_WithEmptyLists() throws Exception {
        List<KpiBenchmarkValues> emptyList1 = Collections.emptyList();
        List<KpiBenchmarkValues> emptyList2 = Collections.emptyList();

        Chunk<List<KpiBenchmarkValues>> chunk = new Chunk<>(Arrays.asList(emptyList1, emptyList2));

        writer.write(chunk);

        verify(persistentService, times(2)).saveKpiBenchmarkValues(emptyList1);
        verify(persistentService, times(2)).saveKpiBenchmarkValues(emptyList2);
    }

    @Test
    void testWrite_ServiceThrowsException() throws Exception {
        List<KpiBenchmarkValues> list = Arrays.asList(
                KpiBenchmarkValues.builder().kpiId("kpi1").build()
        );

        Chunk<List<KpiBenchmarkValues>> chunk = new Chunk<>(Arrays.asList(list));

        doThrow(new RuntimeException("Persistence error"))
                .when(persistentService).saveKpiBenchmarkValues(list);

        assertThrows(RuntimeException.class, () -> writer.write(chunk));
        verify(persistentService).saveKpiBenchmarkValues(list);
    }

    @Test
    void testWrite_WithMultipleLists() throws Exception {
        KpiBenchmarkValues values1 = KpiBenchmarkValues.builder().kpiId("kpi1").build();
        KpiBenchmarkValues values2 = KpiBenchmarkValues.builder().kpiId("kpi2").build();
        KpiBenchmarkValues values3 = KpiBenchmarkValues.builder().kpiId("kpi3").build();

        List<KpiBenchmarkValues> list1 = Arrays.asList(values1, values2);
        List<KpiBenchmarkValues> list2 = Arrays.asList(values3);

        Chunk<List<KpiBenchmarkValues>> chunk = new Chunk<>(Arrays.asList(list1, list2));

        writer.write(chunk);

        verify(persistentService).saveKpiBenchmarkValues(list1);
        verify(persistentService).saveKpiBenchmarkValues(list2);
        verifyNoMoreInteractions(persistentService);
    }
}