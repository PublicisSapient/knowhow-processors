package com.publicissapient.kpidashboard.job.storyhygienecalculation.listener;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.model.application.ErrorDetail;
import com.publicissapient.kpidashboard.common.model.tracelog.JobExecutionTraceLog;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.service.StoryHygieneProjectBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Records job start/end trace and resets batch state after each run. */
@Slf4j
@RequiredArgsConstructor
public class StoryHygieneJobExecutionListener implements JobExecutionListener {

	private final StoryHygieneProjectBatchService batchService;
	private final JobExecutionTraceLogService jobExecutionTraceLogService;

	@Override
	public void beforeJob(@NonNull JobExecution jobExecution) {
		log.info("{} Starting job", JobConstants.LOG_PREFIX_STORY_HYGIENE);
	}

	@Override
	public void afterJob(@NonNull JobExecution jobExecution) {
		log.info(
				"{} Job completed with status: {}",
				JobConstants.LOG_PREFIX_STORY_HYGIENE,
				jobExecution.getStatus());
		batchService.initializeBatchProcessingParametersForTheNextProcess();
		storeJobExecutionStatus(jobExecution);
	}

	private void storeJobExecutionStatus(JobExecution jobExecution) {
		JobParameters jobParameters = jobExecution.getJobParameters();
		String jobName = jobParameters.getString("jobName");
		ObjectId executionId =
				(ObjectId) Objects.requireNonNull(jobParameters.getParameter("executionId")).getValue();

		Optional<JobExecutionTraceLog> traceOpt = jobExecutionTraceLogService.findById(executionId);
		if (traceOpt.isPresent()) {
			JobExecutionTraceLog traceLog = traceOpt.get();
			traceLog.setExecutionOngoing(false);
			traceLog.setExecutionEndedAt(Instant.now());
			traceLog.setExecutionSuccess(jobExecution.getStatus() == BatchStatus.COMPLETED);
			traceLog.setErrorDetailList(
					jobExecution.getAllFailureExceptions().stream()
							.map(
									ex -> {
										ErrorDetail detail = new ErrorDetail();
										detail.setError(ex.getMessage());
										return detail;
									})
							.toList());
			jobExecutionTraceLogService.updateJobExecution(traceLog);
		} else {
			log.error(
					"{} Could not find trace log for job '{}' execution id {}",
					JobConstants.LOG_PREFIX_STORY_HYGIENE,
					jobName,
					executionId);
		}
	}
}
