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

package com.publicissapient.kpidashboard.job.productivitycalculation.listener;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.ErrorDetail;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogServiceImpl;
import com.publicissapient.kpidashboard.job.productivitycalculation.service.ProjectBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ProductivityCalculationJobCompletionListener implements JobExecutionListener {

	private final ProjectBatchService projectBatchService;
	private final ProcessorExecutionTraceLogServiceImpl processorExecutionTraceLogServiceImpl;

	@Override
	public void afterJob(@NonNull JobExecution jobExecution) {
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();
		storeJobExecutionStatus(jobExecution);
	}

	private void storeJobExecutionStatus(JobExecution jobExecution) {
		JobParameters jobParameters = jobExecution.getJobParameters();
		String jobName = jobParameters.getString("jobName");
		ObjectId executionId = (ObjectId) Objects.requireNonNull(jobParameters.getParameter("executionId")).getValue();

		Optional<ProcessorExecutionTraceLog> processorExecutionTraceLogOptional = this.processorExecutionTraceLogServiceImpl
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
			this.processorExecutionTraceLogServiceImpl.save(executionTraceLog);
		} else {
			log.error("Could not store job execution ending status for job with name {} and execution id {}. Job "
					+ "execution could not be found", jobName, executionId);
		}
	}
}
