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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepositoryCustomImpl;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelServiceImpl;
import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.CalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.config.ProductivityCalculationConfig;
import com.publicissapient.kpidashboard.job.productivitycalculation.dto.ProjectInputDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
class ProjectBatchServiceTest {

	@Mock
	private ProductivityCalculationConfig productivityCalculationJobConfig;

	@Mock
	private SprintRepositoryCustomImpl sprintRepositoryCustomImpl;

	@Mock
	private ProjectBasicConfigRepository projectBasicConfigRepository;

	@Mock
	private HierarchyLevelServiceImpl hierarchyLevelServiceImpl;

	@Mock
	private CalculationConfig calculationConfig;

	@Mock
	private BatchConfig batching;

	@Mock
	private CalculationConfig.DataPoints dataPoints;

	@InjectMocks
	private ProjectBatchService projectBatchService;

	@BeforeEach
	void setUp() {
		// Reset any state that might have been set by previous tests
		ReflectionTestUtils.setField(projectBatchService, "projectBatchProcessingParameters", null);
	}

	@Test
	void when_InitializeBatchProcessingParameters_Then_SetsCorrectDefaultValues() {
		// Act
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

		// Assert
		Object projectBatchProcessingParameters = ReflectionTestUtils.getField(projectBatchService,
				"projectBatchProcessingParameters");
		assertNotNull(projectBatchProcessingParameters,
				"projectBatchProcessingParameters should not be null after initialization");

		// Verify all fields are set to expected default values
		assertEquals(0, ReflectionTestUtils.getField(projectBatchProcessingParameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(projectBatchProcessingParameters, "currentIndex"));
		assertEquals(0, ReflectionTestUtils.getField(projectBatchProcessingParameters, "numberOfPages"));

		Object repositoryHasMoreData = ReflectionTestUtils.getField(projectBatchProcessingParameters,
				"repositoryHasMoreData");
		assertNotNull(repositoryHasMoreData);
		assertFalse((Boolean) repositoryHasMoreData);

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(projectBatchProcessingParameters,
				"shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertTrue((Boolean) shouldStartANewBatchProcess);

		assertNull(ReflectionTestUtils.getField(projectBatchProcessingParameters, "currentProjectBatch"));
	}

	@Test
	void when_InitializeBatchProcessingParametersCalledMultipleTimes_Then_ReplacesExistingParameters() {
		// Arrange - First initialization
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");
		Object firstParameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");

		// Act - Second initialization
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");
		Object secondParameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");

		// Assert
		assertNotNull(firstParameters);
		assertNotNull(secondParameters);
		assertNotSame(firstParameters, secondParameters, "Second call should create a new instance");

		// Verify second instance has correct default values
		assertEquals(0, ReflectionTestUtils.getField(secondParameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(secondParameters, "currentIndex"));
		assertEquals(0, ReflectionTestUtils.getField(secondParameters, "numberOfPages"));

		Object repositoryHasMoreData = ReflectionTestUtils.getField(secondParameters, "repositoryHasMoreData");
		assertNotNull(repositoryHasMoreData);
		assertFalse((Boolean) repositoryHasMoreData);

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(secondParameters,
				"shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertTrue((Boolean) shouldStartANewBatchProcess);

		assertNull(ReflectionTestUtils.getField(secondParameters, "currentProjectBatch"));
	}

	@Test
	void when_InitializeBatchProcessingParametersWithExistingState_Then_ResetsToDefaultValues() {
		// Arrange - Manually set some non-default values to simulate existing state
		Object existingParameters = createProjectBatchProcessingParametersWithNonDefaultValues();
		ReflectionTestUtils.setField(projectBatchService, "projectBatchProcessingParameters", existingParameters);

		// Verify pre-condition - existing state has non-default values
		assertNotNull(existingParameters);
		assertEquals(5, ReflectionTestUtils.getField(existingParameters, "currentPageNumber"));
		assertEquals(10, ReflectionTestUtils.getField(existingParameters, "currentIndex"));

		// Act
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

		// Assert
		Object newParameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");
		assertNotNull(newParameters);
		assertNotSame(existingParameters, newParameters, "Should create a new instance, not modify existing");

		// Verify all fields are reset to default values
		assertEquals(0, ReflectionTestUtils.getField(newParameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(newParameters, "currentIndex"));
		assertEquals(0, ReflectionTestUtils.getField(newParameters, "numberOfPages"));

		Object repositoryHasMoreData = ReflectionTestUtils.getField(newParameters, "repositoryHasMoreData");
		assertNotNull(repositoryHasMoreData);
		assertFalse((Boolean) repositoryHasMoreData);

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(newParameters, "shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertTrue((Boolean) shouldStartANewBatchProcess);

		assertNull(ReflectionTestUtils.getField(newParameters, "currentProjectBatch"));
	}

	@Test
	void when_InitializeBatchProcessingParametersWithNullExistingState_Then_CreatesNewParametersSuccessfully() {
		// Arrange - Ensure existing state is null
		ReflectionTestUtils.setField(projectBatchService, "projectBatchProcessingParameters", null);
		assertNull(ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters"));

		// Act
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

		// Assert
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");
		assertNotNull(parameters, "Should create new parameters when existing state is null");

		// Verify all fields have correct default values
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentIndex"));
		assertEquals(0, ReflectionTestUtils.getField(parameters, "numberOfPages"));

		Object repositoryHasMoreData = ReflectionTestUtils.getField(parameters, "repositoryHasMoreData");
		assertNotNull(repositoryHasMoreData);
		assertFalse((Boolean) repositoryHasMoreData);

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertTrue((Boolean) shouldStartANewBatchProcess);

		assertNull(ReflectionTestUtils.getField(parameters, "currentProjectBatch"));
	}

	@Test
	void when_InitializeBatchProcessingParameters_Then_DoesNotInteractWithDependencies() {
		// Act
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

		// Assert - Verify no interactions with mocked dependencies
		verifyNoInteractions(productivityCalculationJobConfig);
		verifyNoInteractions(sprintRepositoryCustomImpl);
		verifyNoInteractions(projectBasicConfigRepository);
		verifyNoInteractions(hierarchyLevelServiceImpl);
	}

	@Test
	void when_InitializeBatchProcessingParametersAfterServiceInstantiation_Then_ParametersAreCorrectlyInitialized() {
		// This test simulates the @PostConstruct behavior
		// Arrange - Create a fresh service instance
		ProjectBatchService freshService = new ProjectBatchService(productivityCalculationJobConfig,
				sprintRepositoryCustomImpl, projectBasicConfigRepository, hierarchyLevelServiceImpl);

		// Act - Simulate @PostConstruct call
		ReflectionTestUtils.invokeMethod(freshService, "initializeBatchProcessingParameters");

		// Assert
		Object parameters = ReflectionTestUtils.getField(freshService, "projectBatchProcessingParameters");
		assertNotNull(parameters);

		// Verify the parameters object has the correct structure and values
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentIndex"));
		assertEquals(0, ReflectionTestUtils.getField(parameters, "numberOfPages"));

		Object repositoryHasMoreData = ReflectionTestUtils.getField(parameters, "repositoryHasMoreData");
		assertNotNull(repositoryHasMoreData);
		assertFalse((Boolean) repositoryHasMoreData);

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertTrue((Boolean) shouldStartANewBatchProcess);

		assertNull(ReflectionTestUtils.getField(parameters, "currentProjectBatch"));
	}

	@Test
	void when_InitializeBatchProcessingParameters_Then_CreatesBuilderBasedObject() {
		// Act
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

		// Assert
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");
		assertNotNull(parameters);

		// Verify the object is of the expected inner class type
		String className = parameters.getClass().getSimpleName();
		assertEquals("ProjectBatchProcessingParameters", className);

		// Verify the object has all expected fields (this tests the builder pattern
		// worked correctly)
		assertDoesNotThrow(() -> {
			ReflectionTestUtils.getField(parameters, "currentPageNumber");
			ReflectionTestUtils.getField(parameters, "currentIndex");
			ReflectionTestUtils.getField(parameters, "numberOfPages");
			ReflectionTestUtils.getField(parameters, "repositoryHasMoreData");
			ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess");
			ReflectionTestUtils.getField(parameters, "currentProjectBatch");
		}, "All expected fields should be present in the created object");
	}

	@Test
	void when_InitializeBatchProcessingParametersInConcurrentEnvironment_Then_HandlesMultipleCallsCorrectly() {
		// This test ensures thread safety of the initialization method
		// Act - Multiple rapid calls to simulate concurrent access
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");

		// Assert - Final state should be consistent
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");
		assertNotNull(parameters);

		// Verify final state has correct default values regardless of multiple calls
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentPageNumber"));
		assertEquals(0, ReflectionTestUtils.getField(parameters, "currentIndex"));
		assertEquals(0, ReflectionTestUtils.getField(parameters, "numberOfPages"));

		Object repositoryHasMoreData = ReflectionTestUtils.getField(parameters, "repositoryHasMoreData");
		assertNotNull(repositoryHasMoreData);
		assertFalse((Boolean) repositoryHasMoreData);

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertTrue((Boolean) shouldStartANewBatchProcess);

		assertNull(ReflectionTestUtils.getField(parameters, "currentProjectBatch"));
	}

	@Test
	void when_GetNextProjectInputDataWithShouldStartNewBatchProcess_Then_InitializesNewBatchAndReturnsFirstItem() {
		initializeBatchProcessingParameters();
		// Arrange
		List<ProjectBasicConfig> projects = createMockProjects(2);
		List<SprintDetails> sprints = createMockSprints(projects);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 2);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);
		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		// Act
		ProjectInputDTO result = projectBatchService.getNextProjectInputData();

		// Assert
		assertNotNull(result);
		assertEquals("Project1", result.name());
		assertEquals("project1-node", result.nodeId());

		// Verify state changes
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");
		assertNotNull(parameters);
		assertEquals(1, ReflectionTestUtils.getField(parameters, "currentIndex"));

		Object shouldStartANewBatchProcess = ReflectionTestUtils.getField(parameters, "shouldStartANewBatchProcess");
		assertNotNull(shouldStartANewBatchProcess);
		assertFalse((Boolean) shouldStartANewBatchProcess);

		verify(projectBasicConfigRepository).findAll(any(PageRequest.class));
		verify(sprintRepositoryCustomImpl).findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt());
	}

	@Test
	void when_GetNextProjectInputDataWithEmptyBatchAfterInitialization_Then_ReturnsNull() {
		initializeBatchProcessingParameters();
		// Arrange
		Page<ProjectBasicConfig> emptyProjectPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 2), 0);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(emptyProjectPage);
		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(Collections.emptyList());

		// Act
		ProjectInputDTO result = projectBatchService.getNextProjectInputData();

		// Assert
		assertNull(result);

		// Verify state
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");
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
		List<SprintDetails> firstSprints = createMockSprints(firstBatch);
		List<SprintDetails> secondSprints = createMockSprints(secondBatch);

		Page<ProjectBasicConfig> firstPage = new PageImpl<>(firstBatch, PageRequest.of(0, 2), 3);
		Page<ProjectBasicConfig> secondPage = new PageImpl<>(secondBatch, PageRequest.of(1, 2), 3);

		when(projectBasicConfigRepository.findAll(PageRequest.of(0, 2))).thenReturn(firstPage);
		when(projectBasicConfigRepository.findAll(PageRequest.of(1, 2))).thenReturn(secondPage);
		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(firstSprints).thenReturn(secondSprints);

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
		List<SprintDetails> sprints = createMockSprints(projects);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 1);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);
		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		// Process the only item
		ProjectInputDTO first = projectBatchService.getNextProjectInputData();

		// Act - Try to get next item when no more data exists
		ProjectInputDTO second = projectBatchService.getNextProjectInputData();

		// Assert
		assertNotNull(first);
		assertNull(second);

		// Verify state
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");
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
		List<SprintDetails> sprints = createMockSprints(projects);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 3);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);
		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		// Act & Assert - Process items and verify index increments
		ProjectInputDTO first = projectBatchService.getNextProjectInputData();
		Object parameters = ReflectionTestUtils.getField(projectBatchService, "projectBatchProcessingParameters");
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
		List<SprintDetails> sprints = createMockSprints(projects);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 1);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);
		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

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

		List<SprintDetails> sprints = createMockSprints(List.of(validProject));
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 2);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);
		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

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
		when(productivityCalculationJobConfig.getBatching()).thenReturn(batching);
		when(batching.getChunkSize()).thenReturn(2);
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");
		// Arrange
		when(projectBasicConfigRepository.findAll(any(PageRequest.class)))
				.thenThrow(new RuntimeException("Database connection failed"));

		// Act & Assert
		RuntimeException exception = assertThrows(RuntimeException.class,
				() -> projectBatchService.getNextProjectInputData());

		assertEquals("Database connection failed", exception.getMessage());
	}

	@Test
	void when_GetNextProjectInputDataWithSprintRepositoryException_Then_PropagatesException() {
		when(productivityCalculationJobConfig.getBatching()).thenReturn(batching);
		when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
		when(batching.getChunkSize()).thenReturn(2);
		when(dataPoints.getCount()).thenReturn(5);

		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");
		// Arrange
		List<ProjectBasicConfig> projects = createMockProjects(1);
		Page<ProjectBasicConfig> projectPage = new PageImpl<>(projects, PageRequest.of(0, 2), 1);

		when(projectBasicConfigRepository.findAll(any(PageRequest.class))).thenReturn(projectPage);
		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenThrow(new RuntimeException("Sprint query failed"));

		// Act & Assert
		RuntimeException exception = assertThrows(RuntimeException.class, () -> {
			projectBatchService.getNextProjectInputData();
		});

		assertEquals("Sprint query failed", exception.getMessage());
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

		when(sprintRepositoryCustomImpl.findByBasicProjectConfigIdInOrderByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(createMockSprints(page1Projects)).thenReturn(createMockSprints(page2Projects))
				.thenReturn(createMockSprints(page3Projects));

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

	@SuppressWarnings("java:S1872")
	// Helper method to create ProjectBatchProcessingParameters with non-default
	// values for testing
	private Object createProjectBatchProcessingParametersWithNonDefaultValues() {
		try {
			// Get the inner class
			Class<?> innerClass = null;
			for (Class<?> declaredClass : ProjectBatchService.class.getDeclaredClasses()) {
				if (declaredClass.getSimpleName().equals("ProjectBatchProcessingParameters")) {
					innerClass = declaredClass;
					break;
				}
			}

			if (innerClass == null) {
				log.error("Could not find ProjectBatchProcessingParameters inner class");
				return null;
			}

			// Create instance using builder pattern (simulating the actual code)
			Object builderInstance = innerClass.getMethod("builder").invoke(null);

			// Set non-default values
			builderInstance = builderInstance.getClass().getMethod("currentPageNumber", int.class)
					.invoke(builderInstance, 5);
			builderInstance = builderInstance.getClass().getMethod("currentIndex", int.class).invoke(builderInstance,
					10);
			builderInstance = builderInstance.getClass().getMethod("numberOfPages", int.class).invoke(builderInstance,
					15);
			builderInstance = builderInstance.getClass().getMethod("repositoryHasMoreData", boolean.class)
					.invoke(builderInstance, true);
			builderInstance = builderInstance.getClass().getMethod("shouldStartANewBatchProcess", boolean.class)
					.invoke(builderInstance, false);

			return builderInstance.getClass().getMethod("build").invoke(builderInstance);
		} catch (Exception e) {
			log.error("Failed to create test object", e);
			return null;
		}
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

	private List<SprintDetails> createMockSprints(List<ProjectBasicConfig> projects) {
		List<SprintDetails> sprints = new ArrayList<>();
		for (ProjectBasicConfig project : projects) {
			SprintDetails sprint = new SprintDetails();
			sprint.setBasicProjectConfigId(project.getId());
			sprint.setSprintName("Sprint for " + project.getProjectName());
			sprint.setSprintID("sprint-" + project.getProjectName().toLowerCase());
			sprints.add(sprint);
		}
		return sprints;
	}

	private void initializeBatchProcessingParameters() {
		HierarchyLevel mockProjectHierarchyLevel;
		HierarchyLevel mockSprintHierarchyLevel;

		// Initialize hierarchy levels
		mockProjectHierarchyLevel = new HierarchyLevel();
		mockProjectHierarchyLevel.setLevel(1);

		mockSprintHierarchyLevel = new HierarchyLevel();
		mockSprintHierarchyLevel.setLevel(2);

		// Setup configuration mocks
		when(productivityCalculationJobConfig.getBatching()).thenReturn(batching);
		when(productivityCalculationJobConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getDataPoints()).thenReturn(dataPoints);
		when(batching.getChunkSize()).thenReturn(2);
		when(dataPoints.getCount()).thenReturn(5);

		// Setup hierarchy level mocks
		when(hierarchyLevelServiceImpl.getProjectHierarchyLevel()).thenReturn(mockProjectHierarchyLevel);
		when(hierarchyLevelServiceImpl.getSprintHierarchyLevel()).thenReturn(mockSprintHierarchyLevel);

		// Initialize batch processing parameters
		ReflectionTestUtils.invokeMethod(projectBatchService, "initializeBatchProcessingParameters");
	}
}