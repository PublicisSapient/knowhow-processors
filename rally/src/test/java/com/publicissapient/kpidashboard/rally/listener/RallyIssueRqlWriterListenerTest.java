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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;

import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.CompositeResult;
import com.publicissapient.kpidashboard.rally.util.RallyProcessorUtil;

/**
 * Unit tests for RallyIssueRqlWriterListener class
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class RallyIssueRqlWriterListenerTest {

    @InjectMocks
    private RallyIssueRqlWriterListener rallyIssueRqlWriterListener;

    @Mock
    private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepo;

    @Mock
    private RallyProcessorConfig rallyProcessorConfig;

    @Mock
    private StepContext stepContext;

    @Mock
    private StepExecution stepExecution;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private ExecutionContext executionContext;

    private List<CompositeResult> compositeResults;
    private Chunk<CompositeResult> compositeResultChunk;
    private List<ProcessorExecutionTraceLog> procTraceLogList;
    private ProcessorExecutionTraceLog progressStatsTraceLog;
    private String basicProjectConfigId;
    private String changeDate;

    @Before
    public void setup() {
        // Set up test data
        basicProjectConfigId = "5e7c9d7a8c1c4a0001a1b2c3";
        changeDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern(RallyConstants.JIRA_ISSUE_CHANGE_DATE_FORMAT));

        // Create composite results with JiraIssue
        compositeResults = new ArrayList<>();
        CompositeResult compositeResult1 = new CompositeResult();
        JiraIssue jiraIssue1 = new JiraIssue();
        jiraIssue1.setBasicProjectConfigId(basicProjectConfigId);
        jiraIssue1.setChangeDate(changeDate);
        compositeResult1.setJiraIssue(jiraIssue1);
        compositeResults.add(compositeResult1);

        CompositeResult compositeResult2 = new CompositeResult();
        JiraIssue jiraIssue2 = new JiraIssue();
        jiraIssue2.setBasicProjectConfigId(basicProjectConfigId);
        jiraIssue2.setChangeDate(changeDate);
        compositeResult2.setJiraIssue(jiraIssue2);
        compositeResults.add(compositeResult2);

        compositeResultChunk = new Chunk<>(compositeResults);

        // Set up processor execution trace logs
        procTraceLogList = new ArrayList<>();
        progressStatsTraceLog = new ProcessorExecutionTraceLog();
        progressStatsTraceLog.setProgressStats(true);
        progressStatsTraceLog.setBasicProjectConfigId(basicProjectConfigId);
        progressStatsTraceLog.setProcessorName(RallyConstants.RALLY);
        progressStatsTraceLog.setLastSuccessfulRun("2025-05-01T10:00:00");
        procTraceLogList.add(progressStatsTraceLog);

        // Set up mock behavior for rallyProcessorConfig that's used in the tests
        when(rallyProcessorConfig.getPrevMonthCountToFetchData()).thenReturn(3);
        when(rallyProcessorConfig.getDaysToReduce()).thenReturn(1);
    }

    @Test
    public void testBeforeWrite() {
        // This method is empty in the implementation, just call it for coverage
        rallyIssueRqlWriterListener.beforeWrite(compositeResultChunk);
    }

    @Test
    public void testAfterWrite_WithExistingTraceLogs() {
        // Arrange
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                ProcessorConstants.JIRA, Collections.singletonList(basicProjectConfigId)))
                .thenReturn(procTraceLogList);

        // Act
        rallyIssueRqlWriterListener.afterWrite(compositeResultChunk);

        // Assert
        verify(processorExecutionTraceLogRepo, times(1)).findByProcessorNameAndBasicProjectConfigIdIn(
                ProcessorConstants.JIRA, Collections.singletonList(basicProjectConfigId));
        verify(processorExecutionTraceLogRepo, times(1)).saveAll(anyList());
    }

    @Test
    public void testAfterWrite_WithoutExistingTraceLogs() {
        // Arrange
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                ProcessorConstants.JIRA, Collections.singletonList(basicProjectConfigId)))
                .thenReturn(new ArrayList<>());

        // Act
        rallyIssueRqlWriterListener.afterWrite(compositeResultChunk);

        // Assert
        verify(processorExecutionTraceLogRepo, times(1)).findByProcessorNameAndBasicProjectConfigIdIn(
                ProcessorConstants.JIRA, Collections.singletonList(basicProjectConfigId));
        verify(processorExecutionTraceLogRepo, times(1)).saveAll(anyList());
    }

    @Test
    public void testAfterWrite_WithExistingTraceLogsButNoSuccessfulRun() {
        // Arrange
        ProcessorExecutionTraceLog traceLogWithoutSuccessfulRun = new ProcessorExecutionTraceLog();
        traceLogWithoutSuccessfulRun.setProgressStats(true);
        traceLogWithoutSuccessfulRun.setBasicProjectConfigId(basicProjectConfigId);
        traceLogWithoutSuccessfulRun.setProcessorName(RallyConstants.RALLY);
        traceLogWithoutSuccessfulRun.setLastSuccessfulRun(null);

        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                ProcessorConstants.JIRA, Collections.singletonList(basicProjectConfigId)))
                .thenReturn(Collections.singletonList(traceLogWithoutSuccessfulRun));

        // Act
        rallyIssueRqlWriterListener.afterWrite(compositeResultChunk);

        // Assert
        verify(processorExecutionTraceLogRepo, times(1)).findByProcessorNameAndBasicProjectConfigIdIn(
                ProcessorConstants.JIRA, Collections.singletonList(basicProjectConfigId));
        verify(processorExecutionTraceLogRepo, times(1)).saveAll(anyList());
    }

    @Test
    public void testAfterWrite_WithMultipleProjects() {
        // Arrange
        String secondProjectId = "5e7c9d7a8c1c4a0001a1b2c4";
        
        // Add a composite result with a different project ID
        CompositeResult compositeResult3 = new CompositeResult();
        JiraIssue jiraIssue3 = new JiraIssue();
        jiraIssue3.setBasicProjectConfigId(secondProjectId);
        jiraIssue3.setChangeDate(changeDate);
        compositeResult3.setJiraIssue(jiraIssue3);
        
        List<CompositeResult> multiProjectResults = new ArrayList<>(compositeResults);
        multiProjectResults.add(compositeResult3);
        Chunk<CompositeResult> multiProjectChunk = new Chunk<>(multiProjectResults);

        // Set up mock for first project
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(ProcessorConstants.JIRA), eq(Collections.singletonList(basicProjectConfigId))))
                .thenReturn(procTraceLogList);
        
        // Set up mock for second project
        ProcessorExecutionTraceLog secondProjectTraceLog = new ProcessorExecutionTraceLog();
        secondProjectTraceLog.setProgressStats(true);
        secondProjectTraceLog.setBasicProjectConfigId(secondProjectId);
        secondProjectTraceLog.setProcessorName(RallyConstants.RALLY);
        secondProjectTraceLog.setLastSuccessfulRun("2025-05-01T10:00:00");
        
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                eq(ProcessorConstants.JIRA), eq(Collections.singletonList(secondProjectId))))
                .thenReturn(Collections.singletonList(secondProjectTraceLog));

        // Act
        rallyIssueRqlWriterListener.afterWrite(multiProjectChunk);

        // Assert
        verify(processorExecutionTraceLogRepo, times(1)).saveAll(anyList());
    }

    @Test
    public void testAfterWrite_WithProgressStatusList() {
        // Arrange
        progressStatsTraceLog.setProgressStatusList(new ArrayList<>());
        
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdIn(
                ProcessorConstants.JIRA, Collections.singletonList(basicProjectConfigId)))
                .thenReturn(procTraceLogList);

        // Act
        rallyIssueRqlWriterListener.afterWrite(compositeResultChunk);

        // Assert
        verify(processorExecutionTraceLogRepo, times(1)).saveAll(anyList());
    }

    @Test
    public void testOnWriteError() {
        // Arrange
        Exception exception = new RuntimeException("Test exception");
        
        // Act
        rallyIssueRqlWriterListener.onWriteError(exception, compositeResultChunk);
        
        // No assertions needed as the method only logs the error
    }
}
