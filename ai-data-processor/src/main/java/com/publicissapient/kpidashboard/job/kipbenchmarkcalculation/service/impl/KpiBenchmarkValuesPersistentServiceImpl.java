package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.repository.kpibenchmark.KpiBenchmarkValuesRepository;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiBenchmarkValuesPersistentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class KpiBenchmarkValuesPersistentServiceImpl implements KpiBenchmarkValuesPersistentService {

    private final KpiBenchmarkValuesRepository repository;

    public KpiBenchmarkValuesPersistentServiceImpl(KpiBenchmarkValuesRepository repository) {
        this.repository = repository;
    }

    public void saveKpiBenchmarkValues(List<KpiBenchmarkValues> kpiBenchmarkValuesList) {
        kpiBenchmarkValuesList.forEach(kpiBenchmarkValues -> {
            if(kpiBenchmarkValues != null) {
                Optional<KpiBenchmarkValues> existing = repository.findByKpiId(kpiBenchmarkValues.getKpiId());
                if (existing.isPresent()) {
                    KpiBenchmarkValues existingValue = existing.get();
                    existingValue.setFilterWiseBenchmarkValues(kpiBenchmarkValues.getFilterWiseBenchmarkValues());
                    existingValue.setLastUpdatedTimestamp(kpiBenchmarkValues.getLastUpdatedTimestamp());
                    repository.save(existingValue);
                } else {
                    repository.save(kpiBenchmarkValues);
                }
            }
        });
    }
}
