package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service;

import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;

import java.util.List;

public interface KpiMasterBatchService {
     List<KpiDataDTO> getNextKpiDataBatch();
}
