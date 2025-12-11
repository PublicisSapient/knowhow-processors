/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.job.recommendationcalculation.strategy;

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

import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.listener.RecommendationCalculationJobExecutionListener;
import com.publicissapient.kpidashboard.job.recommendationcalculation.processor.ProjectItemProcessor;
import com.publicissapient.kpidashboard.job.recommendationcalculation.reader.ProjectItemReader;
import com.publicissapient.kpidashboard.job.recommendationcalculation.service.RecommendationCalculationService;
import com.publicissapient.kpidashboard.job.recommendationcalculation.service.RecommendationProjectBatchService;
import com.publicissapient.kpidashboard.job.recommendationcalculation.writer.ProjectItemWriter;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;

import lombok.RequiredArgsConstructor;

/**
 * Job strategy for recommendation calculation batch job.
 *
 */
@Component
@RequiredArgsConstructor
public class RecommendationCalculationJobStrategy implements JobStrategy {

	private final JobRepository jobRepository;
	private final TaskExecutor taskExecutor;
	private final PlatformTransactionManager platformTransactionManager;
	private final RecommendationCalculationConfig recommendationCalculationConfig;
	private final RecommendationProjectBatchService projectBatchService;
	private final RecommendationCalculationService recommendationCalculationService;
	private final JobExecutionTraceLogService jobExecutionTraceLogService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;
	private final RecommendationRepository recommendationRepository;

	@Override
	public String getJobName() {
		return recommendationCalculationConfig.getName();
	}

	@Override
	public Optional<SchedulingConfig> getSchedulingConfig() {
		return Optional.of(recommendationCalculationConfig.getScheduling());
	}

	@Override
	public Job getJob() {
		return new JobBuilder(recommendationCalculationConfig.getName(), jobRepository).start(chunkProcessProjects())
				.listener(new RecommendationCalculationJobExecutionListener(this.projectBatchService,
						this.jobExecutionTraceLogService))
				.build();
	}

	private Step chunkProcessProjects() {
		return new StepBuilder(String.format("%s-chunk-process", recommendationCalculationConfig.getName()),
				jobRepository)
				.<ProjectInputDTO, Future<RecommendationsActionPlan>>chunk(
						recommendationCalculationConfig.getBatching().getChunkSize(), platformTransactionManager)
				.reader(new ProjectItemReader(this.projectBatchService)).processor(asyncProjectProcessor())
				.writer(asyncItemWriter()).build();
	}

	private AsyncItemProcessor<ProjectInputDTO, RecommendationsActionPlan> asyncProjectProcessor() {
		AsyncItemProcessor<ProjectInputDTO, RecommendationsActionPlan> asyncItemProcessor = new AsyncItemProcessor<>();
		asyncItemProcessor.setDelegate(new ProjectItemProcessor(this.recommendationCalculationService,
				this.processorExecutionTraceLogService));
		asyncItemProcessor.setTaskExecutor(taskExecutor);
		return asyncItemProcessor;
	}

	private AsyncItemWriter<RecommendationsActionPlan> asyncItemWriter() {
		AsyncItemWriter<RecommendationsActionPlan> writer = new AsyncItemWriter<>();
		writer.setDelegate(
				new ProjectItemWriter(this.recommendationRepository, this.processorExecutionTraceLogService));
		return writer;
	}

}
