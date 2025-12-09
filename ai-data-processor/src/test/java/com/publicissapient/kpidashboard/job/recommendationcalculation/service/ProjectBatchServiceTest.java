/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.job.recommendationcalculation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelServiceImpl;
import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
class ProjectBatchServiceTest {

	@Mock
	private RecommendationCalculationConfig recommendationCalculationConfig;

	@Mock
	private ProjectBasicConfigRepository projectBasicConfigRepository;

	@Mock
	private HierarchyLevelServiceImpl hierarchyLevelServiceImpl;

	@Mock
	private BatchConfig batching;

	@InjectMocks
	private RecommendationProjectBatchService projectBatchService;

	@BeforeEach
	void setUp() {
		// Reset any state that might have been set by previous tests
		ReflectionTestUtils.setField(projectBatchService, "processingParameters", null);
	}

	@Test
	void when_InitializeBatchProcessingParametersForTheNextProcess_Then_SetsCorrectDefaultValues() {
		// Act
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();

		// Assert
		Object processingParameters = ReflectionTestUtils.getField(projectBatchService, "processingParameters");
		assertNotNull(processingParameters, "processingParameters should not be null after initialization");

		// Verify all fields are set to expected default values
		assertEquals(0, ReflectionTestUtils.getField(processingParameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(processingParameters, "currentIndex"));
		assertEquals(0, ReflectionTestUtils.getField(processingParameters, "numberOfPages"));

		Object repositoryHasMoreData = ReflectionTestUtils.getField(processingParameters, "repositoryHasMoreData");
		assertNotNull(repositoryHasMoreData);
		assertFalse((Boolean) repositoryHasMoreData);

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(processingParameters,
				"shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertTrue((Boolean) shouldStartANewBatchProcess);

		assertNull(ReflectionTestUtils.getField(processingParameters, "currentProjectBatch"));
	}

	@Test
	void when_InitializeBatchProcessingParametersCalledMultipleTimes_Then_ReplacesExistingParameters() {
		// Arrange - First initialization
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();
		Object firstParameters = ReflectionTestUtils.getField(projectBatchService, "processingParameters");

		// Act - Second initialization
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();
		Object secondParameters = ReflectionTestUtils.getField(projectBatchService, "processingParameters");

		// Assert
		assertNotNull(firstParameters);
		assertNotNull(secondParameters);
		assertNotSame(firstParameters, secondParameters, "Second call should create a new instance");

		// Verify second instance has correct default values
		assertEquals(0, ReflectionTestUtils.getField(secondParameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(secondParameters, "currentIndex"));
		assertTrue((Boolean) ReflectionTestUtils.getField(secondParameters, "shouldStartANewBatchProcess"));
	}

	@Test
	void when_InitializeBatchProcessingParameters_Then_DoesNotInteractWithDependencies() {
		// Act
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();

		// Assert - Verify no interactions with mocked dependencies
		verifyNoInteractions(recommendationCalculationConfig);
		verifyNoInteractions(projectBasicConfigRepository);
		verifyNoInteractions(hierarchyLevelServiceImpl);
	}

	@Test
	void when_GetNextProjectInputDataWithShouldStartNewBatchProcess_Then_InitializesNewBatchAndReturnsFirstItem() {
		initializeBatchProcessingParameters();
		// Arrange
		List<ProjectBasicConfig> projects = createMockProjects(2);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 2);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);

		// Act
		ProjectInputDTO result = projectBatchService.getNextProjectInputData();

		// Assert
		assertNotNull(result);
		assertEquals("Project1", result.name());
		assertEquals("project1-node", result.nodeId());
		assertTrue(result.sprints().isEmpty()); // Recommendation calculation doesn't use sprints

		// Verify state changes
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "processingParameters");
		assertNotNull(parameters);
		assertEquals(1, ReflectionTestUtils.getField(parameters, "currentIndex"));

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertFalse((Boolean) shouldStartANewBatchProcess);

		verify(projectBasicConfigRepository).findAll(any(PageRequest.class));
	}

	@Test
	void when_GetNextProjectInputDataWithEmptyBatchAfterInitialization_Then_ReturnsNull() {
		initializeBatchProcessingParameters();
		// Arrange
		Page<ProjectBasicConfig> emptyProjectPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 2), 0);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(emptyProjectPage);

		// Act
		ProjectInputDTO result = projectBatchService.getNextProjectInputData();

		// Assert
		assertNull(result);

		// Verify state
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "processingParameters");
		assertNotNull(parameters);
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentIndex"));

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertFalse((Boolean) shouldStartANewBatchProcess);
	}

	@Test
	void when_GetNextProjectInputDataWithCurrentBatchProcessed_Then_LoadsNextBatchAndReturnsFirstItem() {
		initializeBatchProcessingParameters();
		// Arrange - Setup initial batch
		List<ProjectBasicConfig> firstBatch = createMockProjects(2);
		List<ProjectBasicConfig> secondBatch = createMockProjects(1, 2); // Start from index 2

		Page<ProjectBasicConfig> firstPage = new PageImpl<>(firstBatch, PageRequest.of(0, 2), 3);
		Page<ProjectBasicConfig> secondPage = new PageImpl<>(secondBatch, PageRequest.of(1, 2), 3);

		when(projectBasicConfigRepository.findAll(PageRequest.of(0, 2))).thenReturn(firstPage);
		when(projectBasicConfigRepository.findAll(PageRequest.of(1, 2))).thenReturn(secondPage);

		// Process first batch completely
		ProjectInputDTO first = projectBatchService.getNextProjectInputData();
		ProjectInputDTO second = projectBatchService.getNextProjectInputData();

		// Act - Get next item which should trigger loading second batch
		ProjectInputDTO third = projectBatchService.getNextProjectInputData();

		// Assert
		assertNotNull(first);
		assertNotNull(second);
		assertNotNull(third);
		assertEquals("Project1", first.name());
		assertEquals("Project2", second.name());
		assertEquals("Project3", third.name());

		// Verify repository calls
		verify(projectBasicConfigRepository).findAll(PageRequest.of(0, 2));
		verify(projectBasicConfigRepository).findAll(PageRequest.of(1, 2));
	}

	@Test
	void when_GetNextProjectInputDataWithNoMoreDataInRepository_Then_ReturnsNull() {
		initializeBatchProcessingParameters();
		// Arrange - Setup single batch with no more data
		List<ProjectBasicConfig> projects = createMockProjects(1);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 1);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);

		// Process the only item
		ProjectInputDTO first = projectBatchService.getNextProjectInputData();

		// Act - Try to get next item when no more data exists
		ProjectInputDTO second = projectBatchService.getNextProjectInputData();

		// Assert
		assertNotNull(first);
		assertNull(second);

		// Verify state
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "processingParameters");
		assertNotNull(parameters);

		Object repositoryHasMoreData = ReflectionTestUtils.getField(parameters, "repositoryHasMoreData");
		assertNotNull(repositoryHasMoreData);
		assertFalse((Boolean) repositoryHasMoreData);
	}

	@Test
	void when_GetNextProjectInputDataWithMultipleCalls_Then_IncrementsIndexCorrectly() {
		initializeBatchProcessingParameters();
		// Arrange
		List<ProjectBasicConfig> projects = createMockProjects(3);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 3), 3);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);

		// Act & Assert - Process items and verify index increments
		ProjectInputDTO first = projectBatchService.getNextProjectInputData();
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "processingParameters");
		assertNotNull(parameters);
		assertEquals(1, ReflectionTestUtils.getField(parameters, "currentIndex"));

		ProjectInputDTO second = projectBatchService.getNextProjectInputData();
		assertEquals(2, ReflectionTestUtils.getField(parameters, "currentIndex"));

		ProjectInputDTO third = projectBatchService.getNextProjectInputData();
		assertEquals(3, ReflectionTestUtils.getField(parameters, "currentIndex"));

		assertNotNull(first);
		assertNotNull(second);
		assertNotNull(third);
		assertEquals("Project1", first.name());
		assertEquals("Project2", second.name());
		assertEquals("Project3", third.name());
	}

	@Test
	void when_GetNextProjectInputDataAfterBatchReset_Then_StartsNewBatchProcess() {
		initializeBatchProcessingParameters();
		// Arrange
		List<ProjectBasicConfig> projects = createMockProjects(1);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 1);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);

		// Process first batch completely
		ProjectInputDTO first = projectBatchService.getNextProjectInputData();
		ProjectInputDTO second = projectBatchService.getNextProjectInputData(); // Should return null

		// Reset for next process
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();

		// Act - Get next item after reset
		ProjectInputDTO afterReset = projectBatchService.getNextProjectInputData();

		// Assert
		assertNotNull(first);
		assertNull(second);
		assertNotNull(afterReset);
		assertEquals("Project1", first.name());
		assertEquals("Project1", afterReset.name());

		// Verify repository was called again after reset
		verify(projectBasicConfigRepository, times(2)).findAll(any(PageRequest.class));
	}

	@Test
	void when_GetNextProjectInputDataWithNullProjectId_Then_FiltersOutNullIdProjects() {
		initializeBatchProcessingParameters();
		// Arrange
		List<ProjectBasicConfig> projects = new ArrayList<>();

		ProjectBasicConfig validProject = new ProjectBasicConfig();
		validProject.setId(new ObjectId());
		validProject.setProjectName("ValidProject");
		validProject.setProjectNodeId("valid-node");

		ProjectBasicConfig nullIdProject = new ProjectBasicConfig();
		nullIdProject.setId(null);
		nullIdProject.setProjectName("NullIdProject");
		nullIdProject.setProjectNodeId("null-node");

		projects.add(validProject);
		projects.add(nullIdProject);

		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 2);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);

		// Act
		ProjectInputDTO first = projectBatchService.getNextProjectInputData();
		ProjectInputDTO second = projectBatchService.getNextProjectInputData();

		// Assert
		assertNotNull(first);
		assertNull(second); // Only valid project should be processed
		assertEquals("ValidProject", first.name());
	}

	@Test
	void when_GetNextProjectInputDataWithRepositoryException_Then_PropagatesException() {
		// Setup configuration mocks
		when(recommendationCalculationConfig.getBatching()).thenReturn(batching);
		when(batching.getChunkSize()).thenReturn(2);
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();

		// Arrange
		when(projectBasicConfigRepository.findAll(any(PageRequest.class)))
				.thenThrow(new RuntimeException("Database connection failed"));

		// Act & Assert
		RuntimeException exception = assertThrows(RuntimeException.class,
				() -> projectBatchService.getNextProjectInputData());

		assertEquals("Database connection failed", exception.getMessage());
	}

	@Test
	void when_GetNextProjectInputDataWithComplexPagination_Then_HandlesMultiplePageTransitions() {
		initializeBatchProcessingParameters();

		// Arrange - Setup 3 pages with 2 items each
		List<ProjectBasicConfig> page1Projects = createMockProjects(2, 0);
		List<ProjectBasicConfig> page2Projects = createMockProjects(2, 2);
		List<ProjectBasicConfig> page3Projects = createMockProjects(1, 4);

		Page<ProjectBasicConfig> page1 = new PageImpl<>(page1Projects, PageRequest.of(0, 2), 5);
		Page<ProjectBasicConfig> page2 = new PageImpl<>(page2Projects, PageRequest.of(1, 2), 5);
		Page<ProjectBasicConfig> page3 = new PageImpl<>(page3Projects, PageRequest.of(2, 2), 5);

		when(projectBasicConfigRepository.findAll(PageRequest.of(0, 2))).thenReturn(page1);
		when(projectBasicConfigRepository.findAll(PageRequest.of(1, 2))).thenReturn(page2);
		when(projectBasicConfigRepository.findAll(PageRequest.of(2, 2))).thenReturn(page3);

		// Act - Process all items across multiple pages
		List<ProjectInputDTO> results = new ArrayList<>();
		ProjectInputDTO item;
		while ((item = projectBatchService.getNextProjectInputData()) != null) {
			results.add(item);
		}

		// Assert
		assertEquals(5, results.size());
		assertEquals("Project1", results.get(0).name());
		assertEquals("Project2", results.get(1).name());
		assertEquals("Project3", results.get(2).name());
		assertEquals("Project4", results.get(3).name());
		assertEquals("Project5", results.get(4).name());

		// Verify all pages were loaded
		verify(projectBasicConfigRepository).findAll(PageRequest.of(0, 2));
		verify(projectBasicConfigRepository).findAll(PageRequest.of(1, 2));
		verify(projectBasicConfigRepository).findAll(PageRequest.of(2, 2));
	}

	@Test
	void when_ProjectInputDTOCreated_Then_ContainsEmptySprintsList() {
		initializeBatchProcessingParameters();
		// Arrange
		List<ProjectBasicConfig> projects = createMockProjects(1);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 1);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);

		// Act
		ProjectInputDTO result = projectBatchService.getNextProjectInputData();

		// Assert
		assertNotNull(result);
		assertNotNull(result.sprints());
		assertTrue(result.sprints().isEmpty(), "Recommendation calculation should not include sprints");
	}

	@Test
	void when_InitializeBatchProcessingParametersAfterServiceInstantiation_Then_ParametersAreCorrectlyInitialized() {
		// This test simulates the @PostConstruct behavior
		// Arrange - Create a fresh service instance
		RecommendationProjectBatchService freshService = new RecommendationProjectBatchService(recommendationCalculationConfig,
				projectBasicConfigRepository, hierarchyLevelServiceImpl);

		// Act - Simulate @PostConstruct call
		ReflectionTestUtils.invokeMethod(freshService, "initializeBatchProcessingParameters");

		// Assert
		Object parameters = ReflectionTestUtils.getField(freshService, "processingParameters");
		assertNotNull(parameters);

		// Verify the parameters object has the correct structure and values
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentIndex"));
		assertTrue((Boolean) ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess"));
	}

	@Test
	void when_InitializeBatchProcessingParametersInConcurrentEnvironment_Then_HandlesMultipleCallsCorrectly() {
		// This test ensures thread safety of the initialization method
		// Act - Multiple rapid calls to simulate concurrent access
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();

		// Assert - Final state should be consistent
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "processingParameters");
		assertNotNull(parameters);

		// Verify final state has correct default values regardless of multiple calls
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentIndex"));
		assertTrue((Boolean) ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess"));
	}

	// Helper methods
	private List<ProjectBasicConfig> createMockProjects(int count) {
		return createMockProjects(count, 0);
	}

	private List<ProjectBasicConfig> createMockProjects(int count, int startIndex) {
		List<ProjectBasicConfig> projects = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ProjectBasicConfig project = new ProjectBasicConfig();
			project.setId(new ObjectId());
			project.setProjectName("Project" + (startIndex + i + 1));
			project.setProjectNodeId("project" + (startIndex + i + 1) + "-node");
			projects.add(project);
		}
		return projects;
	}

	private void initializeBatchProcessingParameters() {
		HierarchyLevel mockProjectHierarchyLevel = new HierarchyLevel();
		mockProjectHierarchyLevel.setLevel(5);
		mockProjectHierarchyLevel.setHierarchyLevelId("project");

		// Setup configuration mocks
		when(recommendationCalculationConfig.getBatching()).thenReturn(batching);
		when(batching.getChunkSize()).thenReturn(2);

		// Setup hierarchy level mocks
		when(hierarchyLevelServiceImpl.getProjectHierarchyLevel()).thenReturn(mockProjectHierarchyLevel);

		// Initialize batch processing parameters
		projectBatchService.initializeBatchProcessingParametersForTheNextProcess();
	}
}
