package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

import java.util.List;

public interface KpiBenchmarkProcessorService {

    List<KpiBenchmarkValues> getKpiWiseBenchmarkValues(List<KpiDataDTO> kpiDataDTOList);

}
