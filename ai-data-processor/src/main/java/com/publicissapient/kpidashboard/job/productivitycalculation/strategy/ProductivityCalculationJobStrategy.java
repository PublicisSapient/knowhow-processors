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

package com.publicissapient.kpidashboard.job.productivitycalculation.strategy;

import java.util.Optional;
import java.util.concurrent.Future;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.publicissapient.kpidashboard.common.model.productivity.calculation.Productivity;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogServiceImpl;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.ProductivityCalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.productivitycalculation.listener.ProductivityCalculationJobCompletionListener;
import com.publicissapient.kpidashboard.job.productivitycalculation.processor.ProjectItemProcessor;
import com.publicissapient.kpidashboard.job.productivitycalculation.reader.ProjectItemReader;
import com.publicissapient.kpidashboard.job.productivitycalculation.service.ProductivityCalculationService;
import com.publicissapient.kpidashboard.job.productivitycalculation.service.ProjectBatchService;
import com.publicissapient.kpidashboard.job.productivitycalculation.writer.ProjectItemWriter;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductivityCalculationJobStrategy implements JobStrategy {

	private final ProductivityCalculationConfig productivityCalculationJobConfig;

	private final ProcessorExecutionTraceLogServiceImpl processorExecutionTraceLogServiceImpl;
	private final ProjectBatchService projectBatchService;
	private final ProductivityCalculationService productivityCalculationService;

	private final PlatformTransactionManager platformTransactionManager;

	private final JobRepository jobRepository;

	private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

	@Override
	public String getJobName() {
		return productivityCalculationJobConfig.getName();
	}

	@Override
	public Optional<SchedulingConfig> getSchedulingConfig() {
		return Optional.of(productivityCalculationJobConfig.getScheduling());
	}

	@Override
	public Job getJob() {
		return new JobBuilder(productivityCalculationJobConfig.getName(), jobRepository).start(chunkProcessProjects())
				.listener(new ProductivityCalculationJobCompletionListener(this.projectBatchService,
						this.processorExecutionTraceLogServiceImpl))
				.build();
	}

	private Step chunkProcessProjects() {
		return new StepBuilder(String.format("%s-chunk-process", productivityCalculationJobConfig.getName()),
				jobRepository)
				.<ProjectInputDTO, Future<Productivity>>chunk(
						productivityCalculationJobConfig.getBatching().getChunkSize(), platformTransactionManager)
				.reader(new ProjectItemReader(this.projectBatchService)).processor(asyncProjectProcessor())
				.writer(asyncItemWriter()).build();
	}

	private AsyncItemProcessor<ProjectInputDTO, Productivity> asyncProjectProcessor() {
		AsyncItemProcessor<ProjectInputDTO, Productivity> asyncItemProcessor = new AsyncItemProcessor<>();
		asyncItemProcessor.setDelegate(new ProjectItemProcessor(this.productivityCalculationService));
		asyncItemProcessor.setTaskExecutor(threadPoolTaskExecutor);
		return asyncItemProcessor;
	}

	private AsyncItemWriter<Productivity> asyncItemWriter() {
		AsyncItemWriter<Productivity> writer = new AsyncItemWriter<>();
		writer.setDelegate(new ProjectItemWriter(this.productivityCalculationService));
		return writer;
	}
}
