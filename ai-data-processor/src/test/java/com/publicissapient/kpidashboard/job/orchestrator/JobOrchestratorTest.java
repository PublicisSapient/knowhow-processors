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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.publicissapient.kpidashboard.common.model.tracelog.JobExecutionTraceLog;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.common.constant.ProcessorType;
import com.publicissapient.kpidashboard.common.model.generic.Processor;
import com.publicissapient.kpidashboard.exception.ConcurrentJobExecutionException;
import com.publicissapient.kpidashboard.exception.InternalServerErrorException;
import com.publicissapient.kpidashboard.exception.JobNotEnabledException;
import com.publicissapient.kpidashboard.exception.ResourceNotFoundException;
import com.publicissapient.kpidashboard.job.dto.JobExecutionResponseRecord;
import com.publicissapient.kpidashboard.job.dto.JobResponseRecord;
import com.publicissapient.kpidashboard.job.processor.AiDataProcessor;
import com.publicissapient.kpidashboard.job.registry.AiDataJobRegistry;
import com.publicissapient.kpidashboard.job.repository.AiDataProcessorRepository;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;

@ExtendWith(MockitoExtension.class)
class JobOrchestratorTest {

	@Mock
	private JobLauncher jobLauncher;

	@Mock
	private AiDataJobRegistry aiDataJobRegistry;

	@Mock
	private AiDataProcessorRepository aiDataProcessorRepository;

	@Mock
	private JobExecutionTraceLogService jobExecutionTraceLogService;

	@InjectMocks
	private JobOrchestrator jobOrchestrator;

	@Test
	void when_RegistryHasJobsNotInDatabase_Then_SavesNewProcessorsToDatabase() {
		// Arrange
		Set<String> registeredJobNames = Set.of("job1", "job2", "job3");
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		registeredJobNames.forEach(jobName -> jobStrategyMap.put(jobName, null));

		List<AiDataProcessor> existingProcessors = List.of(createAiDataProcessor("job1", true));

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findAllByProcessorNameIn(registeredJobNames)).thenReturn(existingProcessors);

		// Act
		ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

		// Assert
		ArgumentCaptor<List<AiDataProcessor>> captor = ArgumentCaptor.forClass(List.class);
		verify(aiDataProcessorRepository).saveAll(captor.capture());

		List<AiDataProcessor> savedProcessors = captor.getValue();
		assert savedProcessors.size() == 2;
		assert savedProcessors.stream().anyMatch(p -> "job2".equals(p.getProcessorName()));
		assert savedProcessors.stream().anyMatch(p -> "job3".equals(p.getProcessorName()));
		assert savedProcessors.stream().allMatch(Processor::isActive);
		assert savedProcessors.stream().allMatch(p -> ProcessorType.AI_DATA.equals(p.getProcessorType()));
	}

	@Test
	void when_AllRegisteredJobsExistInDatabase_Then_DoesNotSaveAnyNewProcessors() {
		// Arrange
		Set<String> registeredJobNames = Set.of("job1", "job2");
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		registeredJobNames.forEach(jobName -> jobStrategyMap.put(jobName, null));

		List<AiDataProcessor> existingProcessors = List.of(createAiDataProcessor("job1", true),
				createAiDataProcessor("job2", true));

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findAllByProcessorNameIn(registeredJobNames)).thenReturn(existingProcessors);

		// Act
		ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

		// Assert
		ArgumentCaptor<List<AiDataProcessor>> captor = ArgumentCaptor.forClass(List.class);
		verify(aiDataProcessorRepository).saveAll(captor.capture());

		List<AiDataProcessor> savedProcessors = captor.getValue();
		assert savedProcessors.isEmpty();
	}

	@Test
	void when_RegistryIsEmpty_Then_DoesNotSaveAnyProcessors() {
		// Arrange
		Map<String, JobStrategy> emptyJobStrategyMap = new HashMap<>();

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(emptyJobStrategyMap);
		when(aiDataProcessorRepository.findAllByProcessorNameIn(Collections.emptySet()))
				.thenReturn(Collections.emptyList());

		// Act
		ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

		// Assert
		ArgumentCaptor<List<AiDataProcessor>> captor = ArgumentCaptor.forClass(List.class);
		verify(aiDataProcessorRepository).saveAll(captor.capture());

		List<AiDataProcessor> savedProcessors = captor.getValue();
		assert savedProcessors.isEmpty();
	}

	@Test
	void when_NoJobsExistInDatabase_Then_SavesAllRegisteredJobs() {
		// Arrange
		Set<String> registeredJobNames = Set.of("job1", "job2", "job3");
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		registeredJobNames.forEach(jobName -> jobStrategyMap.put(jobName, null));

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findAllByProcessorNameIn(registeredJobNames))
				.thenReturn(Collections.emptyList());

		// Act
		ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

		// Assert
		ArgumentCaptor<List<AiDataProcessor>> captor = ArgumentCaptor.forClass(List.class);
		verify(aiDataProcessorRepository).saveAll(captor.capture());

		List<AiDataProcessor> savedProcessors = captor.getValue();
		assert savedProcessors.size() == 3;
		assert savedProcessors.stream().anyMatch(p -> "job1".equals(p.getProcessorName()));
		assert savedProcessors.stream().anyMatch(p -> "job2".equals(p.getProcessorName()));
		assert savedProcessors.stream().anyMatch(p -> "job3".equals(p.getProcessorName()));
		assert savedProcessors.stream().allMatch(Processor::isActive);
		assert savedProcessors.stream().allMatch(p -> ProcessorType.AI_DATA.equals(p.getProcessorType()));
	}

	@Test
	void when_PartialJobsExistInDatabase_Then_SavesOnlyMissingJobs() {
		// Arrange
		Set<String> registeredJobNames = Set.of("job1", "job2", "job3", "job4");
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		registeredJobNames.forEach(jobName -> jobStrategyMap.put(jobName, null));

		List<AiDataProcessor> existingProcessors = List.of(createAiDataProcessor("job1", true),
				createAiDataProcessor("job3", true));

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findAllByProcessorNameIn(registeredJobNames)).thenReturn(existingProcessors);

		// Act
		ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

		// Assert
		ArgumentCaptor<List<AiDataProcessor>> captor = ArgumentCaptor.forClass(List.class);
		verify(aiDataProcessorRepository).saveAll(captor.capture());

		List<AiDataProcessor> savedProcessors = captor.getValue();
		assert savedProcessors.size() == 2;
		assert savedProcessors.stream().anyMatch(p -> "job2".equals(p.getProcessorName()));
		assert savedProcessors.stream().anyMatch(p -> "job4".equals(p.getProcessorName()));
		assert savedProcessors.stream().noneMatch(p -> "job1".equals(p.getProcessorName()));
		assert savedProcessors.stream().noneMatch(p -> "job3".equals(p.getProcessorName()));
	}

	@Test
    void when_SingleJobInRegistryNotInDatabase_Then_SavesSingleProcessor() {
        // Arrange
        Set<String> registeredJobNames = Set.of("singleJob");
        Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
        jobStrategyMap.put("singleJob", null);

        when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
        when(aiDataProcessorRepository.findAllByProcessorNameIn(registeredJobNames))
                .thenReturn(Collections.emptyList());

        // Act
        ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

        // Assert
        ArgumentCaptor<List<AiDataProcessor>> captor = ArgumentCaptor.forClass(List.class);
		verify(aiDataProcessorRepository).saveAll(captor.capture());

        List<AiDataProcessor> savedProcessors = captor.getValue();
        assert savedProcessors.size() == 1;
        assert "singleJob".equals(savedProcessors.get(0).getProcessorName());
        assert savedProcessors.get(0).isActive();
        assert ProcessorType.AI_DATA.equals(savedProcessors.get(0).getProcessorType());
    }

	@Test
	void when_DatabaseHasMoreJobsThanRegistry_Then_OnlySavesRegisteredJobs() {
		// Arrange
		Set<String> registeredJobNames = Set.of("job1", "job2");
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		registeredJobNames.forEach(jobName -> jobStrategyMap.put(jobName, null));

		List<AiDataProcessor> existingProcessors = List.of(createAiDataProcessor("job1", true), createAiDataProcessor(
				"job2", true),
				createAiDataProcessor("job3", true), // This job is in DB but not in registry
				createAiDataProcessor("job4", true) // This job is in DB but not in registry
		);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findAllByProcessorNameIn(registeredJobNames))
				.thenReturn(existingProcessors.subList(0, 2)); // Only return jobs that match registry

		// Act
		ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

		// Assert
		ArgumentCaptor<List<AiDataProcessor>> captor = ArgumentCaptor.forClass(List.class);
		verify(aiDataProcessorRepository).saveAll(captor.capture());

		List<AiDataProcessor> savedProcessors = captor.getValue();
		assert savedProcessors.isEmpty(); // No new jobs to save since all registered jobs exist
	}

	@Test
	void when_RepositoryReturnsEmptyList_Then_SavesAllRegisteredJobs() {
		// Arrange
		Set<String> registeredJobNames = Set.of("job1", "job2");
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		registeredJobNames.forEach(jobName -> jobStrategyMap.put(jobName, null));

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findAllByProcessorNameIn(registeredJobNames))
				.thenReturn(Collections.emptyList());

		// Act
		ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

		// Assert
		ArgumentCaptor<List<AiDataProcessor>> captor = ArgumentCaptor.forClass(List.class);
		verify(aiDataProcessorRepository).saveAll(captor.capture());

		List<AiDataProcessor> savedProcessors = captor.getValue();
		assert savedProcessors.size() == 2;
		assert savedProcessors.stream().anyMatch(p -> "job1".equals(p.getProcessorName()));
		assert savedProcessors.stream().anyMatch(p -> "job2".equals(p.getProcessorName()));
	}

	@Test
	void when_LoadAllRegisteredJobsCalled_Then_VerifiesCorrectRepositoryInteractions() {
		// Arrange
		Set<String> registeredJobNames = Set.of("testJob");
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put("testJob", null);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findAllByProcessorNameIn(registeredJobNames))
				.thenReturn(Collections.emptyList());

		// Act
		ReflectionTestUtils.invokeMethod(jobOrchestrator, "loadAllRegisteredJobs");

		// Assert
		verify(aiDataJobRegistry, times(1)).getJobStrategyMap();
		verify(aiDataProcessorRepository, times(1)).findAllByProcessorNameIn(registeredJobNames);
		verify(aiDataProcessorRepository, times(1)).saveAll(anyList());
	}

	@Test
	void when_DisableJobWithRegisteredJob_Then_SetsJobToInactiveAndReturnsJobResponseRecord() {
		// Arrange
		String jobName = "testJob";
		AiDataProcessor activeProcessor = createAiDataProcessor(jobName, true);
		AiDataProcessor inactiveProcessor = createAiDataProcessor(jobName, false);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, null);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(activeProcessor);
		when(aiDataProcessorRepository.save(any(AiDataProcessor.class))).thenReturn(inactiveProcessor);

		// Act
		JobResponseRecord result = jobOrchestrator.disableJob(jobName);

		// Assert
		assertNotNull(result);
		assertFalse(result.isEnabled());
		assertEquals(jobName, result.jobName());
		assertEquals(ProcessorType.AI_DATA, result.processorType());

		verify(aiDataProcessorRepository).findByProcessorName(jobName);
		verify(aiDataProcessorRepository).save(argThat(processor ->
				!processor.isActive() && jobName.equals(processor.getProcessorName())));
	}

	@Test
	void when_DisableJobWithUnregisteredJob_Then_ThrowsIllegalArgumentException() {
		// Arrange
		String jobName = "unregisteredJob";
		Map<String, JobStrategy> emptyJobStrategyMap = new HashMap<>();

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(emptyJobStrategyMap);

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jobOrchestrator.disableJob(jobName));

		assertTrue(exception.getMessage().contains("Job 'unregisteredJob' is not registered"));
		verify(aiDataProcessorRepository, never()).findByProcessorName(anyString());
		verify(aiDataProcessorRepository, never()).save(any(AiDataProcessor.class));
	}

	@Test
	void when_DisableJobWithAlreadyInactiveJob_Then_KeepsJobInactiveAndReturnsJobResponseRecord() {
		// Arrange
		String jobName = "inactiveJob";
		AiDataProcessor inactiveProcessor = createAiDataProcessor(jobName, false);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, null);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(inactiveProcessor);
		when(aiDataProcessorRepository.save(any(AiDataProcessor.class))).thenReturn(inactiveProcessor);

		// Act
		JobResponseRecord result = jobOrchestrator.disableJob(jobName);

		// Assert
		assertNotNull(result);
		assertFalse(result.isEnabled());
		assertEquals(jobName, result.jobName());
		assertEquals(ProcessorType.AI_DATA, result.processorType());

		verify(aiDataProcessorRepository).save(argThat(processor -> !processor.isActive()));
	}

	@Test
	void when_DisableJobWithNullJobName_Then_ThrowsIllegalArgumentException() {
		// Arrange
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jobOrchestrator.disableJob(null));

		assertTrue(exception.getMessage().contains("Job 'null' is not registered"));
	}

	@Test
	void when_DisableJobWithEmptyJobName_Then_ThrowsIllegalArgumentException() {
		// Arrange
		String emptyJobName = "";
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jobOrchestrator.disableJob(emptyJobName));

		assertTrue(exception.getMessage().contains("Job '' is not registered"));
	}

	@Test
	void when_EnableJobWithRegisteredJob_Then_SetsJobToActiveAndReturnsJobResponseRecord() {
		// Arrange
		String jobName = "testJob";
		AiDataProcessor inactiveProcessor = createAiDataProcessor(jobName, false);
		AiDataProcessor activeProcessor = createAiDataProcessor(jobName, true);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, null);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(inactiveProcessor);
		when(aiDataProcessorRepository.save(any(AiDataProcessor.class))).thenReturn(activeProcessor);

		// Act
		JobResponseRecord result = jobOrchestrator.enableJob(jobName);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEnabled());
		assertEquals(jobName, result.jobName());
		assertEquals(ProcessorType.AI_DATA, result.processorType());

		verify(aiDataProcessorRepository).findByProcessorName(jobName);
		verify(aiDataProcessorRepository).save(argThat(processor ->
				processor.isActive() && jobName.equals(processor.getProcessorName())));
	}

	@Test
	void when_EnableJobWithUnregisteredJob_Then_ThrowsIllegalArgumentException() {
		// Arrange
		String jobName = "unregisteredJob";
		Map<String, JobStrategy> emptyJobStrategyMap = new HashMap<>();

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(emptyJobStrategyMap);

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jobOrchestrator.enableJob(jobName));

		assertTrue(exception.getMessage().contains("Job 'unregisteredJob' is not registered"));
		verify(aiDataProcessorRepository, never()).findByProcessorName(anyString());
		verify(aiDataProcessorRepository, never()).save(any(AiDataProcessor.class));
	}

	@Test
	void when_EnableJobWithAlreadyActiveJob_Then_KeepsJobActiveAndReturnsJobResponseRecord() {
		// Arrange
		String jobName = "activeJob";
		AiDataProcessor activeProcessor = createAiDataProcessor(jobName, true);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, null);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(activeProcessor);
		when(aiDataProcessorRepository.save(any(AiDataProcessor.class))).thenReturn(activeProcessor);

		// Act
		JobResponseRecord result = jobOrchestrator.enableJob(jobName);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEnabled());
		assertEquals(jobName, result.jobName());
		assertEquals(ProcessorType.AI_DATA, result.processorType());

		verify(aiDataProcessorRepository).save(argThat(Processor::isActive));
	}

	@Test
	void when_EnableJobWithNullJobName_Then_ThrowsIllegalArgumentException() {
		// Arrange
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jobOrchestrator.enableJob(null));

		assertTrue(exception.getMessage().contains("Job 'null' is not registered"));
	}

	@Test
	void when_EnableJobWithEmptyJobName_Then_ThrowsIllegalArgumentException() {
		// Arrange
		String emptyJobName = "";
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jobOrchestrator.enableJob(emptyJobName));

		assertTrue(exception.getMessage().contains("Job '' is not registered"));
	}

	@Test
	void when_DisableJobCalledMultipleTimes_Then_EachCallSetsJobToInactive() {
		// Arrange
		String jobName = "testJob";
		AiDataProcessor processor = createAiDataProcessor(jobName, true);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, null);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(processor);
		when(aiDataProcessorRepository.save(any(AiDataProcessor.class))).thenReturn(processor);

		// Act
		jobOrchestrator.disableJob(jobName);
		jobOrchestrator.disableJob(jobName);

		// Assert
		verify(aiDataProcessorRepository, times(2)).save(argThat(p -> !p.isActive()));
	}

	@Test
	void when_EnableJobCalledMultipleTimes_Then_EachCallSetsJobToActive() {
		// Arrange
		String jobName = "testJob";
		AiDataProcessor processor = createAiDataProcessor(jobName, false);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, null);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(processor);
		when(aiDataProcessorRepository.save(any(AiDataProcessor.class))).thenReturn(processor);

		// Act
		jobOrchestrator.enableJob(jobName);
		jobOrchestrator.enableJob(jobName);

		// Assert
		verify(aiDataProcessorRepository, times(2)).save(argThat(Processor::isActive));
	}

	@Test
	void when_RunJobWithValidRegisteredEnabledJob_Then_ExecutesJobAndReturnsExecutionResponse() throws Exception {
		// Arrange
		String jobName = "testJob";
		AiDataProcessor processor = createAiDataProcessor(jobName, true);
		JobExecutionTraceLog traceLog = createProcessorExecutionTraceLog(jobName);

		JobStrategy mockJobStrategy = mock(JobStrategy.class);
		Job mockJob = mock(Job.class);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, mockJobStrategy);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(processor);
		when(jobExecutionTraceLogService.createJobExecution(jobName)).thenReturn(traceLog);
		when(jobExecutionTraceLogService.isJobCurrentlyRunning(jobName))
				.thenReturn(false);
		when(aiDataJobRegistry.getJobStrategy(jobName)).thenReturn(mockJobStrategy);
		when(mockJobStrategy.getJob()).thenReturn(mockJob);

		// Act
		JobExecutionResponseRecord result = jobOrchestrator.runJob(jobName);

		// Assert
		assertNotNull(result);
		assertTrue(result.isRunning());
		assertEquals(jobName, result.jobName());
		assertEquals(processor.getId(), result.jobId());
		assertEquals(traceLog.getId(), result.executionId());
		assertNotNull(result.startedAt());

		verify(jobLauncher).run(eq(mockJob), any(JobParameters.class));
		verify(jobExecutionTraceLogService).createJobExecution(jobName);
	}

	@Test
	void when_RunJobWithUnregisteredJob_Then_ThrowsResourceNotFoundException() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
		// Arrange
		String jobName = "unregisteredJob";
		Map<String, JobStrategy> emptyJobStrategyMap = new HashMap<>();

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(emptyJobStrategyMap);

		// Act & Assert
		ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> jobOrchestrator.runJob(jobName));

		assertTrue(exception.getMessage().contains("Job 'unregisteredJob' is not registered"));
		verify(jobLauncher, never()).run(any(Job.class), any(JobParameters.class));
		verify(jobExecutionTraceLogService, never()).createJobExecution(anyString());
	}

	@Test
	void when_RunJobWithDisabledJob_Then_ThrowsJobNotEnabledException() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
		// Arrange
		String jobName = "disabledJob";
		AiDataProcessor disabledProcessor = createAiDataProcessor(jobName, false);

		JobStrategy mockJobStrategy = mock(JobStrategy.class);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, mockJobStrategy);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(disabledProcessor);

		// Act & Assert
		JobNotEnabledException exception = assertThrows(JobNotEnabledException.class, () -> jobOrchestrator.runJob(jobName));

		assertTrue(exception.getMessage().contains("Job 'disabledJob' did not run because is disabled"));
		verify(jobLauncher, never()).run(any(Job.class), any(JobParameters.class));
		verify(jobExecutionTraceLogService, never()).createJobExecution(anyString());
	}

	@Test
	void when_RunJobWithAlreadyRunningJob_Then_ThrowsJobIsAlreadyRunningException() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
		// Arrange
		String jobName = "runningJob";
		AiDataProcessor processor = createAiDataProcessor(jobName, true);
		JobExecutionTraceLog runningTraceLog = createProcessorExecutionTraceLog(jobName);
		runningTraceLog.setExecutionEndedAt(0L); // Indicates ongoing execution
		runningTraceLog.setExecutionOngoing(true);

		JobStrategy mockJobStrategy = mock(JobStrategy.class);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, mockJobStrategy);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(processor);
		when(jobExecutionTraceLogService.isJobCurrentlyRunning(jobName))
				.thenReturn(true);

		// Act & Assert
		ConcurrentJobExecutionException exception = assertThrows(ConcurrentJobExecutionException.class, () -> jobOrchestrator.runJob(jobName));

		assertTrue(exception.getMessage().contains("Job 'runningJob' is already running"));
		verify(jobLauncher, never()).run(any(Job.class), any(JobParameters.class));
		verify(jobExecutionTraceLogService, never()).createJobExecution(anyString());
	}

	@Test
	void when_RunJobAndJobLauncherThrowsException_Then_UpdatesTraceLogAndThrowsInternalServerErrorException() throws Exception {
		// Arrange
		String jobName = "failingJob";
		AiDataProcessor processor = createAiDataProcessor(jobName, true);
		JobExecutionTraceLog traceLog = createProcessorExecutionTraceLog(jobName);
		RuntimeException jobLauncherException = new RuntimeException("Job execution failed");

		JobStrategy mockJobStrategy = mock(JobStrategy.class);
		Job mockJob = mock(Job.class);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, mockJobStrategy);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(processor);
		when(jobExecutionTraceLogService.createJobExecution(jobName)).thenReturn(traceLog);
        when(jobExecutionTraceLogService.isJobCurrentlyRunning(jobName))
                .thenReturn(false);
		when(aiDataJobRegistry.getJobStrategy(jobName)).thenReturn(mockJobStrategy);
		when(mockJobStrategy.getJob()).thenReturn(mockJob);
		when(jobLauncher.run(any(Job.class), any(JobParameters.class))).thenThrow(jobLauncherException);

		// Act & Assert
		InternalServerErrorException exception = assertThrows(InternalServerErrorException.class, () -> jobOrchestrator.runJob(jobName));

		assertTrue(exception.getMessage().contains("Encountered unexpected error while trying to run job with name 'failingJob'"));

		// Verify trace log was updated with error details
		ArgumentCaptor<JobExecutionTraceLog> traceLogCaptor = ArgumentCaptor.forClass(JobExecutionTraceLog.class);
		verify(jobExecutionTraceLogService).updateJobExecution(traceLogCaptor.capture());

		JobExecutionTraceLog savedTraceLog = traceLogCaptor.getValue();
        assertFalse(savedTraceLog.isExecutionSuccess());
		assertNotNull(savedTraceLog.getErrorDetailList());
		assertFalse(savedTraceLog.getErrorDetailList().isEmpty());
		assertTrue(savedTraceLog.getErrorDetailList().get(0).getError().contains("Could not run job 'failingJob'"));
	}

	@Test
	void when_RunJobWithValidJobParameters_Then_PassesCorrectParametersToJobLauncher() throws Exception {
		// Arrange
		String jobName = "parameterTestJob";
		AiDataProcessor processor = createAiDataProcessor(jobName, true);
		JobExecutionTraceLog traceLog = createProcessorExecutionTraceLog(jobName);

		JobStrategy mockJobStrategy = mock(JobStrategy.class);
		Job mockJob = mock(Job.class);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, mockJobStrategy);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(processor);
		when(jobExecutionTraceLogService.createJobExecution(jobName)).thenReturn(traceLog);
        when(jobExecutionTraceLogService.isJobCurrentlyRunning(jobName))
                .thenReturn(false);
		when(aiDataJobRegistry.getJobStrategy(jobName)).thenReturn(mockJobStrategy);
		when(mockJobStrategy.getJob()).thenReturn(mockJob);

		// Act
		jobOrchestrator.runJob(jobName);

		// Assert
		ArgumentCaptor<JobParameters> jobParametersCaptor = ArgumentCaptor.forClass(JobParameters.class);
		verify(jobLauncher).run(eq(mockJob), jobParametersCaptor.capture());

		JobParameters capturedParameters = jobParametersCaptor.getValue();
		assertEquals(jobName, capturedParameters.getString("jobName"));
		assertEquals(traceLog.getId(), capturedParameters.getParameters().get("executionId").getValue());
	}

	@Test
	void when_RunJobWithNullJobName_Then_ThrowsResourceNotFoundException() {
		// Arrange
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);

		// Act & Assert
		ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> jobOrchestrator.runJob(null));

		assertTrue(exception.getMessage().contains("Job 'null' is not registered"));
	}

	@Test
	void when_RunJobWithEmptyJobName_Then_ThrowsResourceNotFoundException() {
		// Arrange
		String emptyJobName = "";
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);

		// Act & Assert
		ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> jobOrchestrator.runJob(emptyJobName));

		assertTrue(exception.getMessage().contains("Job '' is not registered"));
	}

	@Test
	void when_RunJobWithNullProcessor_Then_ThrowsJobNotEnabledException() {
		// Arrange
		String jobName = "nullProcessorJob";
		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		JobStrategy mockJobStrategy = mock(JobStrategy.class);

		jobStrategyMap.put(jobName, mockJobStrategy);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(null);

		// Act & Assert
		JobNotEnabledException exception = assertThrows(JobNotEnabledException.class, () -> jobOrchestrator.runJob(jobName));

		assertTrue(exception.getMessage().contains("Job 'nullProcessorJob' did not run because is disabled"));
	}

	@Test
	void when_RunJobSuccessfully_Then_ReturnsCorrectExecutionResponseFields() {
		// Arrange
		String jobName = "successfulJob";
		ObjectId processorId = new ObjectId();
		ObjectId executionId = new ObjectId();
		long executionStartTime = Instant.now().toEpochMilli();

		AiDataProcessor processor = createAiDataProcessor(jobName, true);
		processor.setId(processorId);

		JobExecutionTraceLog traceLog = createProcessorExecutionTraceLog(jobName);
		traceLog.setId(executionId);
		traceLog.setExecutionStartedAt(executionStartTime);

		JobStrategy mockJobStrategy = mock(JobStrategy.class);
		Job mockJob = mock(Job.class);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, mockJobStrategy);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(processor);
		when(jobExecutionTraceLogService.createJobExecution(jobName)).thenReturn(traceLog);
        when(jobExecutionTraceLogService.isJobCurrentlyRunning(jobName))
                .thenReturn(false);
		when(aiDataJobRegistry.getJobStrategy(jobName)).thenReturn(mockJobStrategy);
		when(mockJobStrategy.getJob()).thenReturn(mockJob);

		// Act
		JobExecutionResponseRecord result = jobOrchestrator.runJob(jobName);

		// Assert
		assertEquals(jobName, result.jobName());
		assertEquals(processorId, result.jobId());
		assertEquals(executionId, result.executionId());
		assertEquals(Instant.ofEpochMilli(executionStartTime), result.startedAt());
		assertTrue(result.isRunning());
	}

	@Test
	void when_RunJobAndJobLauncherThrowsCheckedException_Then_HandlesExceptionCorrectly() throws Exception {
		// Arrange
		String jobName = "checkedExceptionJob";
		AiDataProcessor processor = createAiDataProcessor(jobName, true);
		JobExecutionTraceLog traceLog = createProcessorExecutionTraceLog(jobName);
		RuntimeException runtimeException = new RuntimeException("Runtime exception occurred");

		JobStrategy mockJobStrategy = mock(JobStrategy.class);
		Job mockJob = mock(Job.class);

		Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
		jobStrategyMap.put(jobName, mockJobStrategy);

		when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
		when(aiDataProcessorRepository.findByProcessorName(jobName)).thenReturn(processor);
		when(jobExecutionTraceLogService.createJobExecution(jobName)).thenReturn(traceLog);
		when(jobExecutionTraceLogService.isJobCurrentlyRunning(jobName))
				.thenReturn(false);
		when(aiDataJobRegistry.getJobStrategy(jobName)).thenReturn(mockJobStrategy);
		when(mockJobStrategy.getJob()).thenReturn(mockJob);
		when(jobLauncher.run(any(Job.class), any(JobParameters.class))).thenThrow(runtimeException);

		// Act & Assert
		InternalServerErrorException exception = assertThrows(InternalServerErrorException.class, () -> jobOrchestrator.runJob(jobName));

		assertTrue(exception.getMessage().contains("Encountered unexpected error while trying to run job with name 'checkedExceptionJob'"));

		// Verify error details contain the original exception message
		ArgumentCaptor<JobExecutionTraceLog> traceLogCaptor = ArgumentCaptor.forClass(JobExecutionTraceLog.class);
		verify(jobExecutionTraceLogService).updateJobExecution(traceLogCaptor.capture());

		JobExecutionTraceLog savedTraceLog = traceLogCaptor.getValue();
		assertTrue(savedTraceLog.getErrorDetailList().get(0).getError().contains("Runtime exception occurred"));
	}

	// Helper method
	private AiDataProcessor createAiDataProcessor(String processorName, boolean isActive) {
		AiDataProcessor processor = new AiDataProcessor();
		processor.setProcessorName(processorName);
		processor.setActive(isActive);
		processor.setProcessorType(ProcessorType.AI_DATA);
		return processor;
	}

	// Helper method
	private JobExecutionTraceLog createProcessorExecutionTraceLog(String processorName) {
		JobExecutionTraceLog traceLog = new JobExecutionTraceLog();
		traceLog.setId(new ObjectId());
		traceLog.setProcessorName(processorName);
		traceLog.setExecutionStartedAt(Instant.now().toEpochMilli());
		traceLog.setExecutionSuccess(true);
		return traceLog;
	}
}
