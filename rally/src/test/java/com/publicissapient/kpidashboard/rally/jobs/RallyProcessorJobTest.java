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

package com.publicissapient.kpidashboard.rally.jobs;

import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.helper.BuilderFactory;
import com.publicissapient.kpidashboard.rally.listener.*;
import com.publicissapient.kpidashboard.rally.tasklet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.*;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.builder.TaskletStepBuilder;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RallyProcessorJobTest {

    @Mock
    private SprintReportTasklet sprintReportTasklet;

    @Mock
    private ScrumReleaseDataTasklet scrumReleaseDataTasklet;

    @Mock
    private RallyIssueRqlWriterListener jiraIssueJqlWriterListener;

    @Mock
    private JobListenerScrum jobListenerScrum;

    @Mock
    private RallyIssueSprintJobListener rallyIssueSprintJobListener;

    @Mock
    private JobStepProgressListener jobStepProgressListener;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private BuilderFactory builderFactory;

    @Mock
    private RallyProcessorConfig rallyProcessorConfig;

    @InjectMocks
    private RallyProcessorJob rallyProcessorJob;

    @Mock
    private JobLauncher jobLauncher;
    
    @BeforeEach
    void setUp() {
        // Configure chunk size
        lenient().when(rallyProcessorConfig.getChunkSize()).thenReturn(10);
        
        // Create mock objects for steps
        TaskletStep mockTaskletStep = mock(TaskletStep.class);
        
        // Mock StepBuilder
        StepBuilder stepBuilder = mock(StepBuilder.class);
        lenient().when(builderFactory.getStepBuilder(anyString(), any(JobRepository.class)))
                .thenReturn(stepBuilder);
        
        // Create a properly typed mock for SimpleStepBuilder
        @SuppressWarnings({"unchecked"})
        SimpleStepBuilder<Object, Object> typedBuilder = mock(SimpleStepBuilder.class);
        lenient().when(stepBuilder.chunk(anyInt(), any(PlatformTransactionManager.class)))
                .thenReturn(typedBuilder);
        
        // Setup method chaining for SimpleStepBuilder with proper return type
        doReturn(typedBuilder).when(typedBuilder).reader(any());
        doReturn(typedBuilder).when(typedBuilder).processor(any());
        doReturn(typedBuilder).when(typedBuilder).writer(any());
        
        // Mock all listener methods using doReturn to avoid ambiguity
        doReturn(typedBuilder).when(typedBuilder).listener(any(ChunkListener.class));
        doReturn(typedBuilder).when(typedBuilder).listener(any(StepExecutionListener.class));
        doReturn(typedBuilder).when(typedBuilder).listener(any(RallyIssueRqlWriterListener.class));
        
        // Mock listener methods with specific type parameters
        doReturn(typedBuilder).when(typedBuilder).listener(any(ItemReadListener.class));
        doReturn(typedBuilder).when(typedBuilder).listener(any(ItemProcessListener.class));
        doReturn(typedBuilder).when(typedBuilder).listener(any(ItemWriteListener.class));
        
        // Mock TaskletStepBuilder for tasklet steps
        TaskletStepBuilder taskletStepBuilder = mock(TaskletStepBuilder.class);
        lenient().when(stepBuilder.tasklet(any(), any(PlatformTransactionManager.class)))
                .thenReturn(taskletStepBuilder);
        
        // Setup method chaining for TaskletStepBuilder with specific listener type
        // Use doReturn() to avoid ambiguity with overloaded methods
        doReturn(taskletStepBuilder).when(taskletStepBuilder).listener(any(StepExecutionListener.class));
        doReturn(taskletStepBuilder).when(taskletStepBuilder).listener(same(jobStepProgressListener));
        doReturn(taskletStepBuilder).when(taskletStepBuilder).listener(any(ChunkListener.class));
        
        // Mock step build
        doReturn(mockTaskletStep).when(typedBuilder).build();
        doReturn(mockTaskletStep).when(taskletStepBuilder).build();
        
        // Mock JobBuilder
        JobBuilder jobBuilder = mock(JobBuilder.class);
        lenient().when(builderFactory.getJobBuilder(anyString(), any(JobRepository.class)))
                .thenReturn(jobBuilder);
        lenient().when(jobBuilder.incrementer(any())).thenReturn(jobBuilder);
        
        // Mock SimpleJobBuilder
        SimpleJobBuilder simpleJobBuilder = mock(SimpleJobBuilder.class);
        lenient().when(jobBuilder.start(any(Step.class))).thenReturn(simpleJobBuilder);
        lenient().when(simpleJobBuilder.next(any(Step.class))).thenReturn(simpleJobBuilder);
        lenient().when(simpleJobBuilder.listener(any())).thenReturn(simpleJobBuilder);
        lenient().when(simpleJobBuilder.listener(any(JobExecutionListener.class))).thenReturn(simpleJobBuilder);
        
        // Mock job build
        Job mockJob = mock(Job.class);
        lenient().when(mockJob.getName()).thenReturn("MockJob");
        lenient().when(simpleJobBuilder.build()).thenReturn(mockJob);
        
        // Mock job launcher with exception handling for all possible exceptions
        JobExecution mockExecution = mock(JobExecution.class);
        try {
            lenient().when(jobLauncher.run(any(Job.class), any(JobParameters.class)))
                    .thenReturn(mockExecution);
        } catch (JobExecutionAlreadyRunningException | JobRestartException | 
                 JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {
            // This won't happen in the test since we're mocking
            fail("Exception should not occur during mocking: " + e.getMessage());
        }
    }

    @Test
    void testFetchIssueScrumRqlJob() {
        // When
        Job job = rallyProcessorJob.fetchIssueScrumRqlJob(null);
        
        // Then
        assertNotNull(job, "Job should not be null");
        
        // Verify job builder was created with correct name
        verify(builderFactory).getJobBuilder(eq("FetchIssueScrum RQL Job"), any(JobRepository.class));
    }

    @Test
    void testFetchIssueSprintJob() {
        // When
        Job job = rallyProcessorJob.fetchIssueSprintJob();
        
        // Then
        assertNotNull(job, "Job should not be null");
        
        // Verify job builder was created with correct name
        verify(builderFactory).getJobBuilder(eq("fetchIssueSprint Job"), any(JobRepository.class));
    }

    @Test
    void testRunMetaDataStep() {
        // When
        Job job = rallyProcessorJob.runMetaDataStep();
        
        // Then
        assertNotNull(job, "Job should not be null");
        
        // Verify job builder was created with correct name
        verify(builderFactory).getJobBuilder(eq("runMetaDataStep Job"), any(JobRepository.class));
    }

    @Test
    void testGetChunkSize() {
        // Given
        int expectedChunkSize = 20;
        when(rallyProcessorConfig.getChunkSize()).thenReturn(expectedChunkSize);
        
        // When - call the method via reflection since it's private
        try {
            java.lang.reflect.Method method = RallyProcessorJob.class.getDeclaredMethod("getChunkSize");
            method.setAccessible(true);
            int actualChunkSize = (int) method.invoke(rallyProcessorJob);
            
            // Then
            assertEquals(expectedChunkSize, actualChunkSize, "Chunk size should match configuration");
        } catch (Exception e) {
            fail("Failed to invoke getChunkSize method: " + e.getMessage());
        }
    }
    
    @Test
    void testFetchIssueScrumRqlChunkStep() {
        // When
        Job job = rallyProcessorJob.fetchIssueScrumRqlJob(null);
        
        // Then
        assertNotNull(job, "Job should not be null");
        
        // Verify the step builder was called with the correct name
        verify(builderFactory).getStepBuilder(eq("Fetch Issues Scrum Rql"), any(JobRepository.class));
    }
    
    @Test
    void testFetchIssueSprintChunkStep() {
        // When
        Job job = rallyProcessorJob.fetchIssueSprintJob();
        
        // Then
        assertNotNull(job, "Job should not be null");
        
        // Verify the step builder was called with the correct name
        verify(builderFactory).getStepBuilder(eq("Fetch Issue-Sprint"), any(JobRepository.class));
    }
}
