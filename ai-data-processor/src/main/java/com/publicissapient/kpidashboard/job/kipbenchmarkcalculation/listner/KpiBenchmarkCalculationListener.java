package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.listner;

import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.ErrorDetail;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class KpiBenchmarkCalculationListener implements JobExecutionListener {

	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	public KpiBenchmarkCalculationListener(
			ProcessorExecutionTraceLogService processorExecutionTraceLogService) {
		this.processorExecutionTraceLogService = processorExecutionTraceLogService;
	}

	@Override
	public void afterJob(@NotNull JobExecution jobExecution) {
		JobParameters jobParameters = jobExecution.getJobParameters();
		String jobName = jobParameters.getString("jobName");
		ObjectId executionId = (ObjectId) Objects.requireNonNull(jobParameters.getParameter("executionId")).getValue();

		Optional<ProcessorExecutionTraceLog> processorExecutionTraceLogOptional = this.processorExecutionTraceLogService
				.findById(executionId);
		if (processorExecutionTraceLogOptional.isPresent()) {
			ProcessorExecutionTraceLog executionTraceLog = processorExecutionTraceLogOptional.get();
			executionTraceLog.setExecutionOngoing(false);
			executionTraceLog.setExecutionEndedAt(Instant.now().toEpochMilli());
			executionTraceLog.setExecutionSuccess(jobExecution.getStatus() == BatchStatus.COMPLETED);
			executionTraceLog
					.setErrorDetailList(jobExecution.getAllFailureExceptions().stream().map(failureException -> {
						ErrorDetail errorDetail = new ErrorDetail();
						errorDetail.setError(failureException.getMessage());
						return errorDetail;
					}).toList());
			this.processorExecutionTraceLogService.saveAiDataProcessorExecutions(executionTraceLog);
		} else {
			log.error("Could not store job execution ending status for job with name {} and execution id {}. Job "
					+ "execution could not be found", jobName, executionId);
		}
	}

}
