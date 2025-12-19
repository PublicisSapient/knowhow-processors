/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.stategy;

import java.util.Optional;
import java.util.concurrent.Future;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.publicissapient.kpidashboard.common.model.kpibenchmark.KpiBenchmarkValues;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.config.KpiBenchmarkCalculationConfig;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.listner.KpiBenchmarkCalculationListener;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.processor.KpiBenchmarkProcessor;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.reader.KpiItemReader;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KnowHowCacheEvictorService;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiBenchmarkValuesPersistentService;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiMasterBatchService;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.impl.KpiBenchmarkProcessorServiceImpl;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.writer.KpiBenchmarkValuesWriter;
import com.publicissapient.kpidashboard.job.shared.dto.KpiDataDTO;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;

@Component
public class KpiBenchmarkCalculationJobStrategy implements JobStrategy {

	private final JobRepository jobRepository;

	private final PlatformTransactionManager platformTransactionManager;
	private final KpiBenchmarkCalculationConfig kpiBenchmarkCalculationConfig;
	private final KpiMasterBatchService kpiMasterBatchService;
	private final KpiBenchmarkProcessorServiceImpl processorService;
	private final KpiBenchmarkValuesPersistentService persistentService;
	private final JobExecutionTraceLogService jobExecutionTraceLogService;
	private final TaskExecutor taskExecutor;
	private final KnowHowCacheEvictorService knowHowCacheEvictorService;

	public KpiBenchmarkCalculationJobStrategy(
			JobRepository jobRepository,
			PlatformTransactionManager platformTransactionManager,
			KpiBenchmarkCalculationConfig kpiBenchmarkCalculationConfig,
			KpiMasterBatchService kpiMasterBatchService,
			KpiBenchmarkProcessorServiceImpl processorService,
			KpiBenchmarkValuesPersistentService persistentService,
			JobExecutionTraceLogService jobExecutionTraceLogService,
			TaskExecutor taskExecutor,
			KnowHowCacheEvictorService knowHowCacheEvictorService) {
		this.jobRepository = jobRepository;
		this.platformTransactionManager = platformTransactionManager;
		this.kpiBenchmarkCalculationConfig = kpiBenchmarkCalculationConfig;
		this.kpiMasterBatchService = kpiMasterBatchService;
		this.processorService = processorService;
		this.persistentService = persistentService;
		this.jobExecutionTraceLogService = jobExecutionTraceLogService;
		this.taskExecutor = taskExecutor;
		this.knowHowCacheEvictorService = knowHowCacheEvictorService;
	}

	@Override
	public String getJobName() {
		return kpiBenchmarkCalculationConfig.getName();
	}

	@Override
	public Job getJob() {
		return new JobBuilder(kpiBenchmarkCalculationConfig.getName(), jobRepository)
				.start(
						new StepBuilder("kpi-benchmark-step", jobRepository)
								.<KpiDataDTO, Future<KpiBenchmarkValues>>chunk(
										kpiBenchmarkCalculationConfig.getBatching().getChunkSize(),
										platformTransactionManager)
								.reader(new KpiItemReader(kpiMasterBatchService))
								.processor(asyncProjectProcessor())
								.writer(asyncItemWriter())
								.build())
				.listener(
						new KpiBenchmarkCalculationListener(
								jobExecutionTraceLogService, kpiMasterBatchService, knowHowCacheEvictorService))
				.build();
	}

	private AsyncItemProcessor<KpiDataDTO, KpiBenchmarkValues> asyncProjectProcessor() {
		AsyncItemProcessor<KpiDataDTO, KpiBenchmarkValues> asyncItemProcessor =
				new AsyncItemProcessor<>();
		asyncItemProcessor.setDelegate(new KpiBenchmarkProcessor(processorService));
		asyncItemProcessor.setTaskExecutor(taskExecutor);
		return asyncItemProcessor;
	}

	private AsyncItemWriter<KpiBenchmarkValues> asyncItemWriter() {
		AsyncItemWriter<KpiBenchmarkValues> writer = new AsyncItemWriter<>();
		writer.setDelegate(new KpiBenchmarkValuesWriter(persistentService));
		return writer;
	}

	@Override
	public Optional<SchedulingConfig> getSchedulingConfig() {
		return Optional.of(kpiBenchmarkCalculationConfig.getScheduling());
	}
}
