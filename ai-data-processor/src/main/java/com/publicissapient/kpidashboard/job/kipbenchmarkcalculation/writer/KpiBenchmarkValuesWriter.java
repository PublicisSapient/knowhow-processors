package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.writer;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiBenchmarkProcessorService;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiBenchmarkValuesPersistentService;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.List;

public class KpiBenchmarkValuesWriter implements ItemWriter<List<KpiBenchmarkValues>> {

    private final KpiBenchmarkValuesPersistentService kpiBenchmarkValuesPersistentService;

    public KpiBenchmarkValuesWriter(KpiBenchmarkValuesPersistentService kpiBenchmarkValuesPersistentService) {
        this.kpiBenchmarkValuesPersistentService = kpiBenchmarkValuesPersistentService;
    }

    @Override
    public void write(Chunk<? extends List<KpiBenchmarkValues>> chunk) throws Exception {
        chunk.forEach(kpiBenchmarkValuesPersistentService::saveKpiBenchmarkValues);
    }
}
