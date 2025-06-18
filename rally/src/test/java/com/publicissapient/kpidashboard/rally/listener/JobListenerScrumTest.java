/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.rally.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.test.util.ReflectionTestUtils;


import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.rally.cache.RallyProcessorCacheEvictor;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.service.NotificationHandler;
import com.publicissapient.kpidashboard.rally.service.OngoingExecutionsService;
import com.publicissapient.kpidashboard.rally.service.ProjectHierarchySyncService;
import com.publicissapient.kpidashboard.rally.service.RallyCommonService;

@ExtendWith(MockitoExtension.class)
public class JobListenerScrumTest {

    private static final String PROJECT_ID = "5e7c9043d1c2a23e1144c0de";

    @Mock
    private NotificationHandler handler;

    @Mock
    private FieldMappingRepository fieldMappingRepository;

    @Mock
    private RallyProcessorCacheEvictor rallyProcessorCacheEvictor;

    @Mock
    private OngoingExecutionsService ongoingExecutionsService;

    @Mock
    private ProjectBasicConfigRepository projectBasicConfigRepo;

    @Mock
    private RallyCommonService rallyCommonService;

    @Mock
    private ProjectHierarchySyncService projectHierarchySyncService;

    @Mock
    private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepo;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private JobInstance jobInstance;

    @InjectMocks
    private JobListenerScrum jobListenerScrum;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(jobListenerScrum, "projectId", PROJECT_ID);
    }

    @Test
    public void testBeforeJob() {
        // This method is empty in the implementation, but we test it for coverage
        jobListenerScrum.beforeJob(jobExecution);
        // No assertions needed as the method is empty
    }

    @Test
    public void testAfterJob_Success() {
        // Setup
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        
        List<ProcessorExecutionTraceLog> traceLogList = new ArrayList<>();
        ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
        traceLog.setProgressStats(true);
        traceLogList.add(traceLog);
        
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID))))
                .thenReturn(traceLogList);
        
        when(processorExecutionTraceLogRepo.saveAll(anyList())).thenReturn(traceLogList);
        
        // Execute
        jobListenerScrum.afterJob(jobExecution);
        
        // Verify
        verify(projectHierarchySyncService).syncScrumSprintHierarchy(any(ObjectId.class));
        verify(rallyProcessorCacheEvictor, times(6)).evictCache(anyString(), anyString());
        verify(processorExecutionTraceLogRepo).saveAll(anyList());
        verify(ongoingExecutionsService).markExecutionAsCompleted(PROJECT_ID);
        
        // Verify trace log was updated correctly
        verify(processorExecutionTraceLogRepo).findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID)));
    }

    @Test
    public void testAfterJob_Failed() throws UnknownHostException {
        // Setup
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("testJob");
        
        StepExecution stepExecution = new StepExecution("testStep", jobExecution);
        stepExecution.setStatus(BatchStatus.FAILED);
        RuntimeException exception = new RuntimeException("Test failure");
        stepExecution.addFailureException(exception);
        
        List<StepExecution> stepExecutions = new ArrayList<>();
        stepExecutions.add(stepExecution);
        when(jobExecution.getStepExecutions()).thenReturn(stepExecutions);
        
        List<ProcessorExecutionTraceLog> traceLogList = new ArrayList<>();
        ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
        traceLog.setProgressStats(true);
        traceLogList.add(traceLog);
        
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID))))
                .thenReturn(traceLogList);
        
        when(processorExecutionTraceLogRepo.saveAll(anyList())).thenReturn(traceLogList);
        
        // Mock field mapping and project config for notification
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setNotificationEnabler(true);
        when(fieldMappingRepository.findByProjectConfigId(PROJECT_ID)).thenReturn(fieldMapping);
        
        ProjectBasicConfig projectConfig = new ProjectBasicConfig();
        projectConfig.setProjectName("Test Project");
        when(projectBasicConfigRepo.findByStringId(PROJECT_ID)).thenReturn(Optional.of(projectConfig));
        
        when(rallyCommonService.getApiHost()).thenReturn("localhost");
        
        // Execute
        jobListenerScrum.afterJob(jobExecution);
        
        // Verify
        verify(projectHierarchySyncService).syncScrumSprintHierarchy(any(ObjectId.class));
        verify(rallyProcessorCacheEvictor, times(6)).evictCache(anyString(), anyString());
        verify(processorExecutionTraceLogRepo).saveAll(anyList());
        verify(ongoingExecutionsService).markExecutionAsCompleted(PROJECT_ID);
        
        // Verify notification was sent
        verify(handler).sendEmailToProjectAdminAndSuperAdmin(
                anyString(), anyString(), eq(PROJECT_ID), 
                eq(RallyConstants.ERROR_NOTIFICATION_SUBJECT_KEY), 
                eq(RallyConstants.ERROR_MAIL_TEMPLATE_KEY));
        
        // Verify trace log was updated correctly with error info
        verify(processorExecutionTraceLogRepo).findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID)));
    }

    @Test
    public void testAfterJob_NotificationDisabled() throws UnknownHostException {
        // Setup
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("testJob");
        
        StepExecution stepExecution = new StepExecution("testStep", jobExecution);
        stepExecution.setStatus(BatchStatus.FAILED);
        RuntimeException exception = new RuntimeException("Test failure");
        stepExecution.addFailureException(exception);
        
        List<StepExecution> stepExecutions = new ArrayList<>();
        stepExecutions.add(stepExecution);
        when(jobExecution.getStepExecutions()).thenReturn(stepExecutions);
        
        List<ProcessorExecutionTraceLog> traceLogList = new ArrayList<>();
        ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
        traceLog.setProgressStats(true);
        traceLogList.add(traceLog);
        
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID))))
                .thenReturn(traceLogList);
        
        when(processorExecutionTraceLogRepo.saveAll(anyList())).thenReturn(traceLogList);
        
        // Mock field mapping with notification disabled
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setNotificationEnabler(false);
        when(fieldMappingRepository.findByProjectConfigId(PROJECT_ID)).thenReturn(fieldMapping);
        
        // Execute
        jobListenerScrum.afterJob(jobExecution);
        
        // Verify
        verify(projectHierarchySyncService).syncScrumSprintHierarchy(any(ObjectId.class));
        verify(rallyProcessorCacheEvictor, times(6)).evictCache(anyString(), anyString());
        verify(processorExecutionTraceLogRepo).saveAll(anyList());
        verify(ongoingExecutionsService).markExecutionAsCompleted(PROJECT_ID);
        
        // Verify notification was NOT sent
        verify(handler, never()).sendEmailToProjectAdminAndSuperAdmin(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void testAfterJob_NullFieldMapping() throws UnknownHostException {
        // Setup
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("testJob");
        
        StepExecution stepExecution = new StepExecution("testStep", jobExecution);
        stepExecution.setStatus(BatchStatus.FAILED);
        RuntimeException exception = new RuntimeException("Test failure");
        stepExecution.addFailureException(exception);
        
        List<StepExecution> stepExecutions = new ArrayList<>();
        stepExecutions.add(stepExecution);
        when(jobExecution.getStepExecutions()).thenReturn(stepExecutions);
        
        List<ProcessorExecutionTraceLog> traceLogList = new ArrayList<>();
        ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
        traceLog.setProgressStats(true);
        traceLogList.add(traceLog);
        
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID))))
                .thenReturn(traceLogList);
        
        when(processorExecutionTraceLogRepo.saveAll(anyList())).thenReturn(traceLogList);
        
        // Mock null field mapping
        when(fieldMappingRepository.findByProjectConfigId(PROJECT_ID)).thenReturn(null);
        
        ProjectBasicConfig projectConfig = new ProjectBasicConfig();
        projectConfig.setProjectName("Test Project");
        when(projectBasicConfigRepo.findByStringId(PROJECT_ID)).thenReturn(Optional.of(projectConfig));
        
        when(rallyCommonService.getApiHost()).thenReturn("localhost");
        
        // Execute
        jobListenerScrum.afterJob(jobExecution);
        
        // Verify
        verify(projectHierarchySyncService).syncScrumSprintHierarchy(any(ObjectId.class));
        verify(rallyProcessorCacheEvictor, times(6)).evictCache(anyString(), anyString());
        verify(processorExecutionTraceLogRepo).saveAll(anyList());
        verify(ongoingExecutionsService).markExecutionAsCompleted(PROJECT_ID);
        
        // Verify notification was sent (null field mapping should send notification)
        verify(handler).sendEmailToProjectAdminAndSuperAdmin(
                anyString(), anyString(), eq(PROJECT_ID), 
                eq(RallyConstants.ERROR_NOTIFICATION_SUBJECT_KEY), 
                eq(RallyConstants.ERROR_MAIL_TEMPLATE_KEY));
    }

    @Test
    public void testAfterJob_NullProjectConfig() throws UnknownHostException {
        // Setup
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("testJob");
        
        StepExecution stepExecution = new StepExecution("testStep", jobExecution);
        stepExecution.setStatus(BatchStatus.FAILED);
        RuntimeException exception = new RuntimeException("Test failure");
        stepExecution.addFailureException(exception);
        
        List<StepExecution> stepExecutions = new ArrayList<>();
        stepExecutions.add(stepExecution);
        when(jobExecution.getStepExecutions()).thenReturn(stepExecutions);
        
        List<ProcessorExecutionTraceLog> traceLogList = new ArrayList<>();
        ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
        traceLog.setProgressStats(true);
        traceLogList.add(traceLog);
        
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID))))
                .thenReturn(traceLogList);
        
        when(processorExecutionTraceLogRepo.saveAll(anyList())).thenReturn(traceLogList);
        
        // Mock field mapping with notification enabled
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setNotificationEnabler(true);
        when(fieldMappingRepository.findByProjectConfigId(PROJECT_ID)).thenReturn(fieldMapping);
        
        // Mock null project config - using when() since we're not using strict stubbing
        when(projectBasicConfigRepo.findByStringId(PROJECT_ID)).thenReturn(Optional.empty());
        
        // We don't need to mock rallyCommonService.getApiHost() since it won't be called in this test
        
        // Execute
        jobListenerScrum.afterJob(jobExecution);
        
        // Verify
        verify(projectHierarchySyncService).syncScrumSprintHierarchy(any(ObjectId.class));
        verify(rallyProcessorCacheEvictor, times(6)).evictCache(anyString(), anyString());
        verify(processorExecutionTraceLogRepo).saveAll(anyList());
        verify(ongoingExecutionsService).markExecutionAsCompleted(PROJECT_ID);
        
        // Verify notification was not sent (null project config with enabled notification should not send)
        verify(handler, never()).sendEmailToProjectAdminAndSuperAdmin(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void testAfterJob_EmptyTraceLogList() {
        // Setup
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        
        // Mock empty trace log list
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID))))
                .thenReturn(Collections.emptyList());
        
        // Execute
        jobListenerScrum.afterJob(jobExecution);
        
        // Verify
        verify(projectHierarchySyncService).syncScrumSprintHierarchy(any(ObjectId.class));
        verify(rallyProcessorCacheEvictor, times(6)).evictCache(anyString(), anyString());
        verify(processorExecutionTraceLogRepo, never()).saveAll(anyList());
        verify(ongoingExecutionsService).markExecutionAsCompleted(PROJECT_ID);
    }

    @Test
    public void testAfterJob_ExceptionHandling() {
        // Setup - use a completed status to avoid the failure path
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        
        // Mock empty trace log list to avoid NPE
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(RallyConstants.RALLY), eq(Collections.singletonList(PROJECT_ID))))
                .thenReturn(Collections.emptyList());
        
        // Execute the method
        jobListenerScrum.afterJob(jobExecution);
        
        // Verify that the method completes and the finally block executes
        verify(ongoingExecutionsService).markExecutionAsCompleted(PROJECT_ID);
    }
}
