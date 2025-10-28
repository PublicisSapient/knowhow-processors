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

package com.publicissapient.kpidashboard.job.orchestrator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.publicissapient.kpidashboard.common.constant.ProcessorType;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogServiceImpl;
import com.publicissapient.kpidashboard.exception.InternalServerErrorException;
import com.publicissapient.kpidashboard.exception.JobIsAlreadyRunningException;
import com.publicissapient.kpidashboard.exception.JobNotEnabledException;
import com.publicissapient.kpidashboard.exception.ResourceNotFoundException;
import com.publicissapient.kpidashboard.job.dto.JobExecutionResponseRecord;
import com.publicissapient.kpidashboard.job.dto.JobResponseRecord;
import com.publicissapient.kpidashboard.job.processor.AiDataProcessor;
import com.publicissapient.kpidashboard.job.registry.AiDataJobRegistry;
import com.publicissapient.kpidashboard.job.repository.AiDataProcessorRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobOrchestrator {
	private static final String JOB_IS_NOT_REGISTERED_EXCEPTION_MESSAGE = "Job '%s' is not registered";

	private final JobLauncher jobLauncher;

	private final AiDataJobRegistry aiDataJobRegistry;

	private final AiDataProcessorRepository aiDataProcessorRepository;

	private final ProcessorExecutionTraceLogServiceImpl processorExecutionTraceLogServiceImpl;

	@PostConstruct
	private void loadAllRegisteredJobs() {
		Set<String> allRegisteredJobNames = this.aiDataJobRegistry.getJobStrategyMap().keySet();

		Set<String> storedProcessorNames = this.aiDataProcessorRepository
				.findAllByProcessorNameIn(allRegisteredJobNames).stream().map(AiDataProcessor::getProcessorName)
				.collect(Collectors.toSet());

		List<String> registeredJobNamesNotPresentInTheDatabase = allRegisteredJobNames.stream()
				.filter(registeredProcessorName -> !storedProcessorNames.contains(registeredProcessorName)).toList();
		this.aiDataProcessorRepository.saveAll(registeredJobNamesNotPresentInTheDatabase.stream().map(processorName -> {
			AiDataProcessor aiDataProcessor = new AiDataProcessor();
			aiDataProcessor.setActive(true);
			aiDataProcessor.setProcessorName(processorName);
			aiDataProcessor.setProcessorType(ProcessorType.AI_DATA);
			return aiDataProcessor;
		}).toList());
	}

	public JobExecutionResponseRecord runJob(String jobName) {
		if (jobIsNotRegistered(jobName)) {
			throw new ResourceNotFoundException(String.format(JOB_IS_NOT_REGISTERED_EXCEPTION_MESSAGE, jobName));
		}
		if (!jobIsEnabled(jobName)) {
			throw new JobNotEnabledException(String.format("Job '%s' did not run because is disabled", jobName));
		}
		if(jobIsCurrentlyRunning(jobName)) {
			throw new JobIsAlreadyRunningException(String.format("Job '%s' is already running", jobName));
		}
		AiDataProcessor aiDataProcessor = aiDataProcessorRepository.findByProcessorName(jobName);
		ProcessorExecutionTraceLog executionTraceLog =
				this.processorExecutionTraceLogServiceImpl.createNewProcessorJobExecution(jobName);

		try {
			JobParameters jobParameters = new JobParametersBuilder()
					.addJobParameter("jobName", jobName, String.class)
					.addJobParameter("executionId", executionTraceLog.getId(), ObjectId.class)
					.toJobParameters();
			this.jobLauncher.run(aiDataJobRegistry.getJobStrategy(jobName).getJob(), jobParameters);
			return JobExecutionResponseRecord.builder()
					.isRunning(true)
					.startedAt(Instant.ofEpochMilli(executionTraceLog.getExecutionStartedAt()))
					.jobName(jobName)
					.jobId(aiDataProcessor.getId())
					.executionId(aiDataProcessor.getId())
					.executionId(executionTraceLog.getId())
					.build();
		} catch (JobExecutionAlreadyRunningException | JobParametersInvalidException | JobRestartException
				| JobInstanceAlreadyCompleteException e) {
			log.error("Could not run the job {} -> {}", jobName, e.getMessage());
			throw new InternalServerErrorException(
					String.format("Encountered unexpected error while trying to run job with name '%s'", jobName));
		}
	}

	public boolean jobIsCurrentlyRunning(String jobName) {
		Optional<ProcessorExecutionTraceLog> processorExecutionTraceLogOptional = processorExecutionTraceLogServiceImpl.findLastExecutionTraceLogByProcessorName(jobName);
		
		return processorExecutionTraceLogOptional.isPresent()
				&& processorExecutionTraceLogOptional.get().isExecutionOngoing();
	}

	@Transactional
	public JobResponseRecord disableJob(String jobName) {
		if (jobIsNotRegistered(jobName)) {
			throw new IllegalArgumentException(String.format(JOB_IS_NOT_REGISTERED_EXCEPTION_MESSAGE, jobName));
		}
		AiDataProcessor job = this.aiDataProcessorRepository.findByProcessorName(jobName);
		job.setActive(false);
		job = this.aiDataProcessorRepository.save(job);
		return JobResponseRecord.builder()
				.isEnabled(job.isActive())
				.jobName(job.getProcessorName())
				.processorType(job.getProcessorType())
				.build();
	}

	@Transactional
	public JobResponseRecord enableJob(String jobName) {
		if (jobIsNotRegistered(jobName)) {
			throw new IllegalArgumentException(String.format(JOB_IS_NOT_REGISTERED_EXCEPTION_MESSAGE, jobName));
		}
		AiDataProcessor job = this.aiDataProcessorRepository.findByProcessorName(jobName);
		job.setActive(true);
		job = this.aiDataProcessorRepository.save(job);
		return JobResponseRecord.builder()
				.isEnabled(job.isActive())
				.jobName(job.getProcessorName())
				.processorType(job.getProcessorType())
				.build();
	}

	private boolean jobIsNotRegistered(String jobName) {
		return !this.aiDataJobRegistry.getJobStrategyMap().containsKey(jobName);
	}

	private boolean jobIsEnabled(String jobName) {
		AiDataProcessor processor = this.aiDataProcessorRepository.findByProcessorName(jobName);

		return processor != null && processor.isActive();
	}
}
