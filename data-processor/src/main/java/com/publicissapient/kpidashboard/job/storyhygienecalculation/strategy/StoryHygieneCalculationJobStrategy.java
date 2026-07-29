package com.publicissapient.kpidashboard.job.storyhygienecalculation.strategy;

import java.util.List;
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

import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.StoryHygieneSprintResult;
import com.publicissapient.kpidashboard.common.repository.jira.StoryHygieneSprintResultRepository;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.config.StoryHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.listener.StoryHygieneJobExecutionListener;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.processor.HygieneProjectItemProcessor;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.reader.HygieneProjectItemReader;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.service.StoryHygieneCalculationService;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.service.StoryHygieneProjectBatchService;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.writer.SprintHygieneResultWriter;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;

import lombok.RequiredArgsConstructor;

/** Wires together the story-hygiene-calculation Spring Batch job. */
@Component
@RequiredArgsConstructor
public class StoryHygieneCalculationJobStrategy implements JobStrategy {

	private final JobRepository jobRepository;
	private final TaskExecutor taskExecutor;
	private final PlatformTransactionManager platformTransactionManager;
	private final StoryHygieneCalculationJobConfig jobConfig;
	private final StoryHygieneProjectBatchService projectBatchService;
	private final StoryHygieneCalculationService calculationService;
	private final JobExecutionTraceLogService jobExecutionTraceLogService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;
	private final StoryHygieneSprintResultRepository hygieneResultRepository;

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
						new StoryHygieneJobExecutionListener(projectBatchService, jobExecutionTraceLogService))
				.build();
	}

	private Step chunkProcessProjects() {
		return new StepBuilder(String.format("%s-chunk-process", jobConfig.getName()), jobRepository)
				.<FieldMapping, Future<List<StoryHygieneSprintResult>>>chunk(
						jobConfig.getBatching().getChunkSize(), platformTransactionManager)
				.reader(new HygieneProjectItemReader(projectBatchService))
				.processor(asyncProjectProcessor())
				.writer(asyncItemWriter())
				.build();
	}

	private AsyncItemProcessor<FieldMapping, List<StoryHygieneSprintResult>> asyncProjectProcessor() {
		AsyncItemProcessor<FieldMapping, List<StoryHygieneSprintResult>> asyncProcessor =
				new AsyncItemProcessor<>();
		asyncProcessor.setDelegate(
				new HygieneProjectItemProcessor(calculationService, processorExecutionTraceLogService));
		asyncProcessor.setTaskExecutor(taskExecutor);
		return asyncProcessor;
	}

	private AsyncItemWriter<List<StoryHygieneSprintResult>> asyncItemWriter() {
		AsyncItemWriter<List<StoryHygieneSprintResult>> writer = new AsyncItemWriter<>();
		writer.setDelegate(
				new SprintHygieneResultWriter(hygieneResultRepository, processorExecutionTraceLogService));
		return writer;
	}
}
