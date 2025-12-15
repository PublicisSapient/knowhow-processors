package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.stategy;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.config.KpiBenchmarkCalculationConfig;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.listner.KpiBenchmarkCalculationListener;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.processor.KpiBenchmarkProcessor;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.reader.KpiItemReader;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiBenchmarkValuesPersistentService;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.KpiMasterBatchService;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service.impl.KpiBenchmarkProcessorServiceImpl;
import com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.writer.KpiBenchmarkValuesWriter;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

@Component
public class KpiBenchmarkCalculationJobStrategy implements JobStrategy {

    private final JobRepository jobRepository;

    private final PlatformTransactionManager platformTransactionManager;
    private final KpiBenchmarkCalculationConfig kpiBenchmarkCalculationConfig;
    private final KpiMasterBatchService kpiMasterBatchService;
    private final KpiBenchmarkProcessorServiceImpl processorService;
    private final KpiBenchmarkValuesPersistentService persistentService;
    private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

    public KpiBenchmarkCalculationJobStrategy(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager,
                                              KpiBenchmarkCalculationConfig kpiBenchmarkCalculationConfig,
                                              KpiMasterBatchService kpiMasterBatchService,
                                              KpiBenchmarkProcessorServiceImpl processorService,
                                              KpiBenchmarkValuesPersistentService persistentService, ProcessorExecutionTraceLogService processorExecutionTraceLogService) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.kpiBenchmarkCalculationConfig = kpiBenchmarkCalculationConfig;
        this.kpiMasterBatchService = kpiMasterBatchService;
        this.processorService = processorService;
        this.persistentService = persistentService;
        this.processorExecutionTraceLogService = processorExecutionTraceLogService;
    }

    @Override
    public String getJobName() {
        return kpiBenchmarkCalculationConfig.getName();
    }

    @Override
    public Job getJob() {
        return new JobBuilder(kpiBenchmarkCalculationConfig.getName(), jobRepository)
                .start(new StepBuilder("kpi-benchmark-step", jobRepository)
                        .<List<KpiDataDTO>, List<KpiBenchmarkValues>>chunk(
                                kpiBenchmarkCalculationConfig.getBatching().getChunkSize(), platformTransactionManager)
                        .reader(new KpiItemReader(kpiMasterBatchService))
                        .processor(new KpiBenchmarkProcessor(processorService))
                        .writer(new KpiBenchmarkValuesWriter(persistentService))
                        .build())
                .listener(new KpiBenchmarkCalculationListener(this.processorExecutionTraceLogService))
                .build();
    }

    @Override
    public Optional<SchedulingConfig> getSchedulingConfig() {
        return Optional.of(kpiBenchmarkCalculationConfig.getScheduling());
    }
}
