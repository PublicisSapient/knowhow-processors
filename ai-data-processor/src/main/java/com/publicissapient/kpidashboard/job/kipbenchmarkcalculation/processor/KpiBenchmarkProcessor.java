package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.processor;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiBenchmarkProcessorService;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl.KpiBenchmarkProcessorServiceImpl;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;
import org.springframework.batch.item.ItemProcessor;

import java.util.List;

public class KpiBenchmarkProcessor implements ItemProcessor<List<KpiDataDTO>, List<KpiBenchmarkValues>> {

    private final KpiBenchmarkProcessorService processorService;

    public KpiBenchmarkProcessor(KpiBenchmarkProcessorServiceImpl processorService) {
        this.processorService = processorService;
    }

    @Override
    public List<KpiBenchmarkValues> process(List<KpiDataDTO> item) throws Exception {
        List<KpiBenchmarkValues> kpiBenchmarkValuesList = processorService.getKpiWiseBenchmarkValues(item);
        return kpiBenchmarkValuesList;
    }
}
