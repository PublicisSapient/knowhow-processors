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

package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.stategy;

import java.util.List;
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

	public KpiBenchmarkCalculationJobStrategy(
			JobRepository jobRepository,
			PlatformTransactionManager platformTransactionManager,
			KpiBenchmarkCalculationConfig kpiBenchmarkCalculationConfig,
			KpiMasterBatchService kpiMasterBatchService,
			KpiBenchmarkProcessorServiceImpl processorService,
			KpiBenchmarkValuesPersistentService persistentService,
			JobExecutionTraceLogService jobExecutionTraceLogService,
			TaskExecutor taskExecutor) {
		this.jobRepository = jobRepository;
		this.platformTransactionManager = platformTransactionManager;
		this.kpiBenchmarkCalculationConfig = kpiBenchmarkCalculationConfig;
		this.kpiMasterBatchService = kpiMasterBatchService;
		this.processorService = processorService;
		this.persistentService = persistentService;
		this.jobExecutionTraceLogService = jobExecutionTraceLogService;
		this.taskExecutor = taskExecutor;
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
								.<List<KpiDataDTO>, Future<List<KpiBenchmarkValues>>>chunk(
										kpiBenchmarkCalculationConfig.getBatching().getChunkSize(),
										platformTransactionManager)
								.reader(new KpiItemReader(kpiMasterBatchService))
								.processor(asyncProjectProcessor())
								.writer(asyncItemWriter())
								.build())
				.listener(
						new KpiBenchmarkCalculationListener(jobExecutionTraceLogService, kpiMasterBatchService))
				.build();
	}

	private AsyncItemProcessor<List<KpiDataDTO>, List<KpiBenchmarkValues>> asyncProjectProcessor() {
		AsyncItemProcessor<List<KpiDataDTO>, List<KpiBenchmarkValues>> asyncItemProcessor =
				new AsyncItemProcessor<>();
		asyncItemProcessor.setDelegate(new KpiBenchmarkProcessor(processorService));
		asyncItemProcessor.setTaskExecutor(taskExecutor);
		return asyncItemProcessor;
	}

	private AsyncItemWriter<List<KpiBenchmarkValues>> asyncItemWriter() {
		AsyncItemWriter<List<KpiBenchmarkValues>> writer = new AsyncItemWriter<>();
		writer.setDelegate(new KpiBenchmarkValuesWriter(persistentService));
		return writer;
	}

	@Override
	public Optional<SchedulingConfig> getSchedulingConfig() {
		return Optional.of(kpiBenchmarkCalculationConfig.getScheduling());
	}
}
