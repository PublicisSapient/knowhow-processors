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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.listner;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.validation.constraints.NotNull;

import org.bson.types.ObjectId;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ErrorDetail;
import com.publicissapient.kpidashboard.common.model.tracelog.JobExecutionTraceLog;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KnowHowCacheEvictorService;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KpiMasterBatchService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KpiBenchmarkCalculationListener implements JobExecutionListener {

	private final JobExecutionTraceLogService jobExecutionTraceLogService;
	private final KpiMasterBatchService kpiMasterBatchService;
	private final KnowHowCacheEvictorService knowHowCacheEvictorService;
	private final AtomicBoolean cacheCleared = new AtomicBoolean(false);

	public KpiBenchmarkCalculationListener(
			JobExecutionTraceLogService jobExecutionTraceLogService,
			KpiMasterBatchService kpiMasterBatchService,
			KnowHowCacheEvictorService knowHowCacheEvictorService) {
		this.jobExecutionTraceLogService = jobExecutionTraceLogService;
		this.kpiMasterBatchService = kpiMasterBatchService;
		this.knowHowCacheEvictorService = knowHowCacheEvictorService;
	}

	@Override
	public void afterJob(@NotNull JobExecution jobExecution) {

		boolean hasMoreBatches = kpiMasterBatchService.getNextKpiData() != null;

		if (!hasMoreBatches && !cacheCleared.getAndSet(true)) {
			knowHowCacheEvictorService.evictCache(CommonConstant.CACHE_KPI_BENCHMARK_TARGETS);
		}

		JobParameters jobParameters = jobExecution.getJobParameters();
		String jobName = jobParameters.getString("jobName");
		ObjectId executionId =
				(ObjectId) Objects.requireNonNull(jobParameters.getParameter("executionId")).getValue();

		Optional<JobExecutionTraceLog> executionTraceLogOptional =
				this.jobExecutionTraceLogService.findById(executionId);
		if (executionTraceLogOptional.isPresent()) {
			JobExecutionTraceLog executionTraceLog = executionTraceLogOptional.get();
			executionTraceLog.setExecutionOngoing(false);
			executionTraceLog.setExecutionEndedAt(Instant.now());
			executionTraceLog.setExecutionSuccess(jobExecution.getStatus() == BatchStatus.COMPLETED);
			executionTraceLog.setErrorDetailList(
					jobExecution.getAllFailureExceptions().stream()
							.map(
									failureException -> {
										ErrorDetail errorDetail = new ErrorDetail();
										errorDetail.setError(failureException.getMessage());
										return errorDetail;
									})
							.toList());
			this.jobExecutionTraceLogService.updateJobExecution(executionTraceLog);
		} else {
			log.error(
					"Could not store job execution ending status for job with name {} and execution id {}. Job "
							+ "execution could not be found",
					jobName,
					executionId);
		}
	}
}
