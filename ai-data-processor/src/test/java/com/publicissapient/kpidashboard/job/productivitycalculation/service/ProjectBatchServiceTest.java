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

package com.publicissapient.kpidashboard.job.productivitycalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.generic.BasicModel;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepositoryCustomImpl;
import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.CalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.ProductivityCalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
class ProjectBatchServiceTest {

    @Mock
    private ProductivityCalculationConfig productivityCalculationConfig;

    @Mock
    private SprintRepositoryCustomImpl sprintRepositoryCustomImpl;

    @Mock
    private ProjectBasicConfigRepository projectBasicConfigRepository;

    @Mock
    private BatchConfig batchConfig;

    @Mock
    private CalculationConfig calculationConfig;

    @Mock
    private CalculationConfig.DataPoints dataPoints;

    private ProjectBatchService projectBatchService;

    @BeforeEach
    void setUp() {
        projectBatchService = new ProjectBatchService(productivityCalculationConfig,
                sprintRepositoryCustomImpl, projectBasicConfigRepository);

        when(productivityCalculationConfig.getBatching()).thenReturn(batchConfig);
        when(productivityCalculationConfig.getCalculationConfig()).thenReturn(calculationConfig);
        when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
        when(batchConfig.getChunkSize()).thenReturn(10);
        when(dataPoints.getCount()).thenReturn(5);
    }

    @Test
    void when_FirstCallWithValidDataThen_ReturnsFirstProjectInput() {
        // Arrange
        List<ProjectBasicConfig> projects = createMockProjects(2);
        List<SprintDetails> sprints = createMockSprints(projects);
        Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 10), 2);

        when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);
        when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
                .thenReturn(sprints);

        // Initialize the service
        ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

        // Act
        ProjectInputDTO result = projectBatchService.getNextProjectInputData();

        // Assert
        assertNotNull(result);
        assertEquals("Project1", result.name());
        assertEquals("project1", result.nodeId());
    }

    @Test
    void when_BatchExhaustedWithMorePagesThen_LoadsNextBatchAndReturnsData() {
        // Arrange
        List<ProjectBasicConfig> firstBatch = createMockProjects(1);
        List<ProjectBasicConfig> secondBatch = createMockProjects(1, 2);
        List<SprintDetails> firstSprints = createMockSprints(firstBatch);
        List<SprintDetails> secondSprints = createMockSprints(secondBatch);

        Page<ProjectBasicConfig> firstPage = new PageImpl<>(firstBatch, PageRequest.of(0, 10), 2);
        firstPage = spy(firstPage);
        when(firstPage.hasNext()).thenReturn(true);

        Page<ProjectBasicConfig> secondPage = new PageImpl<>(secondBatch, PageRequest.of(1, 10), 2);
        secondPage = spy(secondPage);
        when(secondPage.hasNext()).thenReturn(false);

        when(projectBasicConfigRepository.findAll(PageRequest.of(0, 10))).thenReturn(firstPage);
        when(projectBasicConfigRepository.findAll(PageRequest.of(1, 10))).thenReturn(secondPage);
        when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(
                eq(firstBatch.stream().map(BasicModel::getId).toList()), anyInt())).thenReturn(firstSprints);
        when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(
                eq(secondBatch.stream().map(BasicModel::getId).toList()), anyInt())).thenReturn(secondSprints);

        // Initialize the service
        ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

        // Act - Get first item from first batch
        ProjectInputDTO firstResult = projectBatchService.getNextProjectInputData();
        // Act - Get first item from second batch (should trigger next batch loading)
        ProjectInputDTO secondResult = projectBatchService.getNextProjectInputData();

        // Assert
        assertNotNull(firstResult);
        assertNotNull(secondResult);
        assertEquals("Project1", firstResult.name());
        assertEquals("Project2", secondResult.name());
    }

    @Test
    void when_EmptyBatchAfterInitializationThen_ReturnsNull() {
        // Arrange
        Page<ProjectBasicConfig> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);
        when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
                .thenReturn(Collections.emptyList());

        // Initialize the service
        ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

        // Act
        ProjectInputDTO result = projectBatchService.getNextProjectInputData();

        // Assert
        assertNull(result);
    }

    @Test
    void when_MultipleCallsWithinSameBatchThen_ReturnsSequentialItems() {
        // Arrange
        List<ProjectBasicConfig> projects = createMockProjects(3);
        List<SprintDetails> sprints = createMockSprints(projects);
        Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 10), 3);

        when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);
        when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
                .thenReturn(sprints);

        // Initialize the service
        ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

        // Act
        ProjectInputDTO first = projectBatchService.getNextProjectInputData();
        ProjectInputDTO second = projectBatchService.getNextProjectInputData();
        ProjectInputDTO third = projectBatchService.getNextProjectInputData();

        // Assert
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        assertEquals("Project1", first.name());
        assertEquals("Project2", second.name());
        assertEquals("Project3", third.name());
    }

    // Helper methods
    private List<ProjectBasicConfig> createMockProjects(int count) {
        return createMockProjects(count, 1);
    }

    private List<ProjectBasicConfig> createMockProjects(int count, int startIndex) {
        List<ProjectBasicConfig> projects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ProjectBasicConfig project = new ProjectBasicConfig();
            project.setId(new ObjectId());
            project.setProjectName("Project" + (startIndex + i));
            project.setProjectNodeId("project" + (startIndex + i));
            projects.add(project);
        }
        return projects;
    }

    private List<SprintDetails> createMockSprints(List<ProjectBasicConfig> projects) {
        List<SprintDetails> sprints = new ArrayList<>();
        for (ProjectBasicConfig project : projects) {
            SprintDetails sprint = new SprintDetails();
            sprint.setBasicProjectConfigId(project.getId());
            sprint.setSprintName("Sprint1");
            sprint.setSprintID("sprint1");
            sprints.add(sprint);
        }
        return sprints;
    }

    private Object createMockBatchProcessingParameters(int currentPageNumber, int currentIndex, boolean repositoryHasMoreData) {
        try {
            Class<?> parameterClass = Class.forName(
                    "com.publicissapient.kpidashboard.job.productivitycalculation.service.ProjectBatchService$ProjectBatchProcessingParameters");
            Object parameters = parameterClass.getDeclaredConstructor().newInstance();

            ReflectionTestUtils.setField(parameters, "currentPageNumber", currentPageNumber);
            ReflectionTestUtils.setField(parameters, "currentIndex", currentIndex);
            ReflectionTestUtils.setField(parameters, "numberOfPages", 1);
            ReflectionTestUtils.setField(parameters, "repositoryHasMoreData", repositoryHasMoreData);
            ReflectionTestUtils.setField(parameters, "shouldStartANewBatchProcess", false);
            ReflectionTestUtils.setField(parameters, "currentProjectBatch", new ArrayList<>());

            return parameters;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock parameters", e);
        }
    }
}

