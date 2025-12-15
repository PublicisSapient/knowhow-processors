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

package com.publicissapient.kpidashboard.job.kpimaturitycalculation.strategy;

import java.util.Optional;
import java.util.concurrent.Future;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KpiMaturity;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.config.KpiMaturityCalculationConfig;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.listener.KpiMaturityCalculationJobExecutionListener;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.processor.ProjectItemProcessor;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.reader.ProjectItemReader;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.service.KpiMaturityCalculationService;
import com.publicissapient.kpidashboard.job.kpimaturitycalculation.writer.ProjectItemWriter;
import com.publicissapient.kpidashboard.job.productivitycalculation.service.ProjectBatchService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KpiMaturityCalculationJobStrategy implements JobStrategy {

	private final JobRepository jobRepository;

	private final TaskExecutor taskExecutor;

	private final PlatformTransactionManager platformTransactionManager;

	private final KpiMaturityCalculationConfig kpiMaturityCalculationConfig;

	private final ProjectBatchService projectBatchService;
	private final KpiMaturityCalculationService kpiMaturityCalculationService;
	private final JobExecutionTraceLogService jobExecutionTraceLogService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Override
	public String getJobName() {
		return this.kpiMaturityCalculationConfig.getName();
	}

	@Override
	public Job getJob() {
		return new JobBuilder(this.kpiMaturityCalculationConfig.getName(), this.jobRepository)
				.start(chunkProcessProjects())
				.listener(new KpiMaturityCalculationJobExecutionListener(this.projectBatchService,
						this.jobExecutionTraceLogService))
				.build();
	}

	@Override
	public Optional<SchedulingConfig> getSchedulingConfig() {
		return Optional.of(kpiMaturityCalculationConfig.getScheduling());
	}

	private Step chunkProcessProjects() {
		return new StepBuilder(String.format("%s-chunk-process", this.kpiMaturityCalculationConfig.getName()),
				this.jobRepository)
				.<ProjectInputDTO, Future<KpiMaturity>>chunk(
						this.kpiMaturityCalculationConfig.getBatching().getChunkSize(), this.platformTransactionManager)
				.reader(new ProjectItemReader(this.projectBatchService)).processor(asyncProjectProcessor())
				.writer(asyncItemWriter()).build();
	}

	private AsyncItemProcessor<ProjectInputDTO, KpiMaturity> asyncProjectProcessor() {
		AsyncItemProcessor<ProjectInputDTO, KpiMaturity> asyncItemProcessor = new AsyncItemProcessor<>();
		asyncItemProcessor.setDelegate(new ProjectItemProcessor(this.kpiMaturityCalculationService));
		asyncItemProcessor.setTaskExecutor(taskExecutor);
		return asyncItemProcessor;
	}

	private AsyncItemWriter<KpiMaturity> asyncItemWriter() {
		AsyncItemWriter<KpiMaturity> writer = new AsyncItemWriter<>();
		writer.setDelegate(new ProjectItemWriter(this.kpiMaturityCalculationService, this.processorExecutionTraceLogService));
		return writer;
	}
}
