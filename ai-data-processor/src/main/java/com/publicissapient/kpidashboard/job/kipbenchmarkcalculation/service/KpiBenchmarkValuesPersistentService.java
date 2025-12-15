package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;

import java.util.List;

public interface KpiBenchmarkValuesPersistentService {

    void saveKpiBenchmarkValues(List<KpiBenchmarkValues> kpiBenchmarkValuesList);

}
