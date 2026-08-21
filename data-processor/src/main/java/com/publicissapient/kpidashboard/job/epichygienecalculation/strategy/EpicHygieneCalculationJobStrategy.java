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

package com.publicissapient.kpidashboard.job.epichygienecalculation.strategy;

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

import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.common.repository.jira.EpicHygieneDataRepository;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.listener.EpicHygieneJobExecutionListener;
import com.publicissapient.kpidashboard.job.epichygienecalculation.processor.EpicHygieneProjectItemProcessor;
import com.publicissapient.kpidashboard.job.epichygienecalculation.reader.EpicHygieneProjectItemReader;
import com.publicissapient.kpidashboard.job.epichygienecalculation.service.EpicHygieneCalculationService;
import com.publicissapient.kpidashboard.job.epichygienecalculation.service.EpicHygieneProjectBatchService;
import com.publicissapient.kpidashboard.job.epichygienecalculation.writer.EpicHygieneDataWriter;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;

import lombok.RequiredArgsConstructor;

/**
 * Wires together the epic-hygiene-calculation Spring Batch job.
 *
 * <p>Single chunk oriented step over projects (chunk size comes from {@code
 * jobs.epic-hygiene-calculation.batching.chunk-size}, configured to 10). Each project is processed
 * asynchronously because the KPI call is remote and slow.
 */
@Component
@RequiredArgsConstructor
public class EpicHygieneCalculationJobStrategy implements JobStrategy {

	private final JobRepository jobRepository;
	private final TaskExecutor taskExecutor;
	private final PlatformTransactionManager platformTransactionManager;
	private final EpicHygieneCalculationJobConfig jobConfig;
	private final EpicHygieneProjectBatchService projectBatchService;
	private final EpicHygieneCalculationService calculationService;
	private final JobExecutionTraceLogService jobExecutionTraceLogService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;
	private final EpicHygieneDataRepository epicHygieneDataRepository;

	@Override
	public String getJobName() {
		return jobConfig.getName();
	}

	@Override
	public Optional<SchedulingConfig> getSchedulingConfig() {
		return Optional.of(jobConfig.getScheduling());
	}

	@Override
	public Job getJob() {
		return new JobBuilder(jobConfig.getName(), jobRepository)
				.start(chunkProcessProjects())
				.listener(
						new EpicHygieneJobExecutionListener(projectBatchService, jobExecutionTraceLogService))
				.build();
	}

	private Step chunkProcessProjects() {
		return new StepBuilder(String.format("%s-chunk-process", jobConfig.getName()), jobRepository)
				.<ProjectInputDTO, Future<EpicHygieneData>>chunk(
						jobConfig.getBatching().getChunkSize(), platformTransactionManager)
				.reader(new EpicHygieneProjectItemReader(projectBatchService))
				.processor(asyncProjectProcessor())
				.writer(asyncItemWriter())
				.build();
	}

	private AsyncItemProcessor<ProjectInputDTO, EpicHygieneData> asyncProjectProcessor() {
		AsyncItemProcessor<ProjectInputDTO, EpicHygieneData> asyncProcessor =
				new AsyncItemProcessor<>();
		asyncProcessor.setDelegate(
				new EpicHygieneProjectItemProcessor(calculationService, processorExecutionTraceLogService));
		asyncProcessor.setTaskExecutor(taskExecutor);
		return asyncProcessor;
	}

	private AsyncItemWriter<EpicHygieneData> asyncItemWriter() {
		AsyncItemWriter<EpicHygieneData> writer = new AsyncItemWriter<>();
		writer.setDelegate(
				new EpicHygieneDataWriter(epicHygieneDataRepository, processorExecutionTraceLogService));
		return writer;
	}
}
