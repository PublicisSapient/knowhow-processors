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

package com.publicissapient.kpidashboard.job.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.orchestrator.JobOrchestrator;
import com.publicissapient.kpidashboard.job.registry.AiDataJobRegistry;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;

@ExtendWith(MockitoExtension.class)
class AiDataJobSchedulerTest {

    @Mock
    private AiDataJobRegistry aiDataJobRegistry;

    @Mock
    private JobOrchestrator jobOrchestrator;

    @Mock
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    @Mock
    private JobStrategy jobStrategyWithScheduling;

    @Mock
    private JobStrategy jobStrategyWithoutScheduling;

    @Mock
    private SchedulingConfig schedulingConfig;

    @InjectMocks
    private AiDataJobScheduler aiDataJobScheduler;

    @Test
    void when_ScheduleAllJobsWithJobsHavingSchedulingConfig_Then_SchedulesJobsWithCronTrigger() {
        // Arrange
        String jobName1 = "scheduledJob1";
        String jobName2 = "scheduledJob2";
        String cronExpression1 = "0 0 12 * * ?";
        String cronExpression2 = "0 0 6 * * ?";

        Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
        jobStrategyMap.put(jobName1, jobStrategyWithScheduling);
        jobStrategyMap.put(jobName2, jobStrategyWithScheduling);

        when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
        when(jobStrategyWithScheduling.getSchedulingConfig())
                .thenReturn(Optional.of(schedulingConfig))
                .thenReturn(Optional.of(schedulingConfig));
        when(schedulingConfig.getCron())
                .thenReturn(cronExpression1)
                .thenReturn(cronExpression2);

        // Act
        ReflectionTestUtils.invokeMethod(aiDataJobScheduler, "scheduleAllJobs");

        // Assert
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<CronTrigger> cronTriggerCaptor = ArgumentCaptor.forClass(CronTrigger.class);

        verify(threadPoolTaskScheduler, times(2)).schedule(runnableCaptor.capture(), cronTriggerCaptor.capture());

        // Verify cron expressions are correctly set
        assert cronTriggerCaptor.getAllValues().size() == 2;
        verify(aiDataJobRegistry, times(1)).getJobStrategyMap();
        verify(jobStrategyWithScheduling, times(4)).getSchedulingConfig();
        verify(schedulingConfig, times(2)).getCron();
    }

    @Test
    void when_ScheduleAllJobsWithJobsWithoutSchedulingConfig_Then_DoesNotScheduleThoseJobs() {
        // Arrange
        String jobName1 = "scheduledJob";
        String jobName2 = "unscheduledJob";

        Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
        jobStrategyMap.put(jobName1, jobStrategyWithScheduling);
        jobStrategyMap.put(jobName2, jobStrategyWithoutScheduling);

        when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
        when(jobStrategyWithScheduling.getSchedulingConfig()).thenReturn(Optional.of(schedulingConfig));
        when(jobStrategyWithoutScheduling.getSchedulingConfig()).thenReturn(Optional.empty());
        when(schedulingConfig.getCron()).thenReturn("0 0 12 * * ?");

        // Act
        ReflectionTestUtils.invokeMethod(aiDataJobScheduler, "scheduleAllJobs");

        // Assert
        verify(threadPoolTaskScheduler, times(1)).schedule(any(Runnable.class), any(CronTrigger.class));
        verify(jobStrategyWithScheduling, times(2)).getSchedulingConfig();
        verify(jobStrategyWithoutScheduling, times(1)).getSchedulingConfig();
        verify(schedulingConfig, times(1)).getCron();
    }

    @Test
    void when_ScheduleAllJobsWithEmptyJobRegistry_Then_DoesNotScheduleAnyJobs() {
        // Arrange
        Map<String, JobStrategy> emptyJobStrategyMap = new HashMap<>();
        when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(emptyJobStrategyMap);

        // Act
        ReflectionTestUtils.invokeMethod(aiDataJobScheduler, "scheduleAllJobs");

        // Assert
        verify(threadPoolTaskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
        verify(aiDataJobRegistry, times(1)).getJobStrategyMap();
    }

    @Test
    void when_ScheduleAllJobsAndExceptionOccurs_Then_LogsErrorAndContinues() {
        // Arrange
        String jobName = "failingJob";
        Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
        jobStrategyMap.put(jobName, jobStrategyWithScheduling);

        when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
        when(jobStrategyWithScheduling.getSchedulingConfig()).thenReturn(Optional.of(schedulingConfig));
        when(schedulingConfig.getCron()).thenThrow(new RuntimeException("Invalid cron expression"));

        // Act
        ReflectionTestUtils.invokeMethod(aiDataJobScheduler, "scheduleAllJobs");

        // Assert
        verify(aiDataJobRegistry, times(1)).getJobStrategyMap();
        verify(jobStrategyWithScheduling, times(2)).getSchedulingConfig();
        verify(threadPoolTaskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    void when_ScheduleAllJobsWithNullSchedulingConfig_Then_DoesNotScheduleJob() {
        // Arrange
        String jobName = "jobWithNullConfig";
        Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
        jobStrategyMap.put(jobName, jobStrategyWithoutScheduling);

        when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
        when(jobStrategyWithoutScheduling.getSchedulingConfig()).thenReturn(Optional.empty());

        // Act
        ReflectionTestUtils.invokeMethod(aiDataJobScheduler, "scheduleAllJobs");

        // Assert
        verify(threadPoolTaskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
        verify(jobStrategyWithoutScheduling, times(1)).getSchedulingConfig();
    }

    @Test
    void when_ScheduleAllJobsExecutesScheduledTask_Then_CallsJobOrchestratorRunJob() {
        // Arrange
        String jobName = "testExecutionJob";
        Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
        jobStrategyMap.put(jobName, jobStrategyWithScheduling);

        when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
        when(jobStrategyWithScheduling.getSchedulingConfig()).thenReturn(Optional.of(schedulingConfig));
        when(schedulingConfig.getCron()).thenReturn("0 0 12 * * ?");

        // Act
        ReflectionTestUtils.invokeMethod(aiDataJobScheduler, "scheduleAllJobs");

        // Capture the scheduled runnable and execute it
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(threadPoolTaskScheduler).schedule(runnableCaptor.capture(), any(CronTrigger.class));

        Runnable scheduledTask = runnableCaptor.getValue();
        scheduledTask.run();

        // Assert
        verify(jobOrchestrator, times(1)).runJob(jobName);
    }

    @Test
    void when_ScheduleAllJobsWithThreadPoolTaskSchedulerException_Then_HandlesExceptionGracefully() {
        // Arrange
        String jobName = "schedulerExceptionJob";
        Map<String, JobStrategy> jobStrategyMap = new HashMap<>();
        jobStrategyMap.put(jobName, jobStrategyWithScheduling);

        when(aiDataJobRegistry.getJobStrategyMap()).thenReturn(jobStrategyMap);
        when(jobStrategyWithScheduling.getSchedulingConfig()).thenReturn(Optional.of(schedulingConfig));
        when(schedulingConfig.getCron()).thenReturn("0 0 12 * * ?");
        when(threadPoolTaskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenThrow(new RuntimeException("Scheduler error"));

        // Act
        ReflectionTestUtils.invokeMethod(aiDataJobScheduler, "scheduleAllJobs");

        // Assert
        verify(threadPoolTaskScheduler, times(1)).schedule(any(Runnable.class), any(CronTrigger.class));
        verify(aiDataJobRegistry, times(1)).getJobStrategyMap();
    }
}
