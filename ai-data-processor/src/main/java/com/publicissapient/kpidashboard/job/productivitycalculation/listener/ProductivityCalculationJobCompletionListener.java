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

import java.util.Objects;

import org.bson.types.ObjectId;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogServiceImpl;
import com.publicissapient.kpidashboard.job.productivitycalculation.service.ProjectBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ProductivityCalculationJobCompletionListener implements JobExecutionListener {

    private final ProcessorExecutionTraceLogServiceImpl processorExecutionTraceLogServiceImpl;
    private final ProjectBatchService projectBatchService;

    @Override
    public void afterJob(@NonNull JobExecution jobExecution) {
        projectBatchService.initializeBatchProcessingParametersForTheNextProcess();
        JobParameters jobParameters = jobExecution.getJobParameters();
        this.processorExecutionTraceLogServiceImpl.markJobExecutionAsEnded(jobParameters.getString("jobName"),
                (ObjectId) Objects.requireNonNull(jobParameters.getParameter("executionId")).getValue());
    }
}
