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

package com.publicissapient.kpidashboard.job.epichygienecalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("EpicHygieneProjectBatchService Tests")
class EpicHygieneProjectBatchServiceTest {

	private static final int CHUNK_SIZE = 10;

	@Mock private ProjectBasicConfigRepository projectBasicConfigRepository;

	@Mock private HierarchyLevelServiceImpl hierarchyLevelService;

	private EpicHygieneCalculationJobConfig jobConfig;
	private EpicHygieneProjectBatchService batchService;

	@BeforeEach
	void setUp() {
		BatchConfig batchConfig = new BatchConfig();
		batchConfig.setChunkSize(CHUNK_SIZE);

		jobConfig = new EpicHygieneCalculationJobConfig();
		jobConfig.setName("epic-hygiene-calculation");
		jobConfig.setBatching(batchConfig);

		batchService =
				new EpicHygieneProjectBatchService(
						jobConfig, projectBasicConfigRepository, hierarchyLevelService);
		batchService.initializeBatchProcessingParametersForTheNextProcess();
	}

	private ProjectBasicConfig project(String nodeId, String displayName) {
		ProjectBasicConfig project = new ProjectBasicConfig();
		project.setId(new ObjectId());
		project.setProjectNodeId(nodeId);
		project.setProjectDisplayName(displayName);
		return project;
	}

	private HierarchyLevel projectHierarchyLevel() {
		HierarchyLevel hierarchyLevel = new HierarchyLevel();
		hierarchyLevel.setLevel(5);
		hierarchyLevel.setHierarchyLevelId("project");
		return hierarchyLevel;
	}

	private void stubPage(Page<ProjectBasicConfig> page) {
		when(projectBasicConfigRepository.findByKanbanAndProjectOnHold(
						eq(false), eq(false), any(PageRequest.class)))
				.thenReturn(page);
		when(hierarchyLevelService.getProjectHierarchyLevel()).thenReturn(projectHierarchyLevel());
	}

	@Nested
	@DisplayName("Streaming projects")
	class StreamingProjects {

		@Test
		@DisplayName("Should map a project onto the batch DTO")
		void getNextProjectInputData_SingleProject_MapsFields() {
			ProjectBasicConfig project = project("node-1", "Project One");
			stubPage(new PageImpl<>(List.of(project), PageRequest.of(0, CHUNK_SIZE), 1));

			ProjectInputDTO result = batchService.getNextProjectInputData();

			assertNotNull(result);
			assertEquals("node-1", result.nodeId());
			assertEquals("Project One", result.name());
			assertEquals(String.valueOf(project.getId()), result.basicProjectConfigId());
			assertEquals(5, result.hierarchyLevel());
			assertEquals("project", result.hierarchyLevelId());
			assertNotNull(result.sprints());
		}

		@Test
		@DisplayName("Should return every project of the page and then null")
		void getNextProjectInputData_SinglePage_ReturnsAllThenNull() {
			stubPage(
					new PageImpl<>(
							List.of(project("node-1", "One"), project("node-2", "Two")),
							PageRequest.of(0, CHUNK_SIZE),
							2));

			ProjectInputDTO first = batchService.getNextProjectInputData();
			ProjectInputDTO second = batchService.getNextProjectInputData();

			assertNotNull(first);
			assertNotNull(second);
			assertEquals("node-1", first.nodeId());
			assertEquals("node-2", second.nodeId());
			assertNull(batchService.getNextProjectInputData());
		}

		@Test
		@DisplayName("Should request pages sized like the configured chunk")
		void getNextProjectInputData_UsesChunkSizeAsPageSize() {
			stubPage(new PageImpl<>(List.of(project("node-1", "One")), PageRequest.of(0, CHUNK_SIZE), 1));

			batchService.getNextProjectInputData();

			verify(projectBasicConfigRepository)
					.findByKanbanAndProjectOnHold(false, false, PageRequest.of(0, CHUNK_SIZE));
		}

		@Test
		@DisplayName("Should read a second page when more data is available")
		void getNextProjectInputData_MultiplePages_ReadsThemAll() {
			Page<ProjectBasicConfig> firstPage =
					new PageImpl<>(List.of(project("node-1", "One")), PageRequest.of(0, 1), 2);
			Page<ProjectBasicConfig> secondPage =
					new PageImpl<>(List.of(project("node-2", "Two")), PageRequest.of(1, 1), 2);

			when(projectBasicConfigRepository.findByKanbanAndProjectOnHold(
							eq(false), eq(false), any(PageRequest.class)))
					.thenReturn(firstPage)
					.thenReturn(secondPage);
			when(hierarchyLevelService.getProjectHierarchyLevel()).thenReturn(projectHierarchyLevel());

			ProjectInputDTO first = batchService.getNextProjectInputData();
			ProjectInputDTO second = batchService.getNextProjectInputData();

			assertNotNull(first);
			assertNotNull(second);
			assertEquals("node-1", first.nodeId());
			assertEquals("node-2", second.nodeId());
			assertNull(batchService.getNextProjectInputData());

			verify(projectBasicConfigRepository, times(2))
					.findByKanbanAndProjectOnHold(eq(false), eq(false), any(PageRequest.class));
		}

		@Test
		@DisplayName("Should only consider Scrum projects that are not on hold")
		void getNextProjectInputData_FiltersKanbanAndOnHoldProjects() {
			stubPage(new PageImpl<>(List.of(project("node-1", "One")), PageRequest.of(0, CHUNK_SIZE), 1));

			batchService.getNextProjectInputData();

			verify(projectBasicConfigRepository)
					.findByKanbanAndProjectOnHold(eq(false), eq(false), any(PageRequest.class));
		}
	}

	@Nested
	@DisplayName("Edge cases")
	class EdgeCases {

		@Test
		@DisplayName("Should return null when no project is eligible")
		void getNextProjectInputData_NoProjects_ReturnsNull() {
			stubPage(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, CHUNK_SIZE), 0));

			assertNull(batchService.getNextProjectInputData());
		}

		@Test
		@DisplayName("Should skip projects that have no node id")
		void getNextProjectInputData_ProjectWithoutNodeId_IsSkipped() {
			ProjectBasicConfig invalid = new ProjectBasicConfig();
			invalid.setId(new ObjectId());

			List<ProjectBasicConfig> projects = new ArrayList<>();
			projects.add(invalid);
			projects.add(project("node-2", "Two"));
			stubPage(new PageImpl<>(projects, PageRequest.of(0, CHUNK_SIZE), 2));

			ProjectInputDTO onlyValid = batchService.getNextProjectInputData();

			assertNotNull(onlyValid);
			assertEquals("node-2", onlyValid.nodeId());
			assertNull(batchService.getNextProjectInputData());
		}

		@Test
		@DisplayName("Should skip projects that have no id")
		void getNextProjectInputData_ProjectWithoutId_IsSkipped() {
			ProjectBasicConfig invalid = new ProjectBasicConfig();
			invalid.setProjectNodeId("node-x");
			stubPage(new PageImpl<>(List.of(invalid), PageRequest.of(0, CHUNK_SIZE), 1));

			assertNull(batchService.getNextProjectInputData());
		}

		@Test
		@DisplayName("Should keep returning null once the stream is exhausted")
		void getNextProjectInputData_CalledAfterExhaustion_KeepsReturningNull() {
			stubPage(new PageImpl<>(List.of(project("node-1", "One")), PageRequest.of(0, CHUNK_SIZE), 1));

			assertNotNull(batchService.getNextProjectInputData());
			assertNull(batchService.getNextProjectInputData());
			assertNull(batchService.getNextProjectInputData());
		}
	}

	@Nested
	@DisplayName("State reset")
	class StateReset {

		@Test
		@DisplayName("Should create a fresh state on reset")
		void initializeBatchProcessingParameters_CreatesNewState() {
			Object first = ReflectionTestUtils.getField(batchService, "processingParameters");

			batchService.initializeBatchProcessingParametersForTheNextProcess();
			Object second = ReflectionTestUtils.getField(batchService, "processingParameters");

			assertNotNull(first);
			assertNotNull(second);
			assertNotSame(first, second);
			assertEquals(0, ReflectionTestUtils.getField(second, "currentPageNumber"));
			assertEquals(0, ReflectionTestUtils.getField(second, "currentIndex"));
		}

		@Test
		@DisplayName("Should replay projects from the beginning after a reset")
		void initializeBatchProcessingParameters_AllowsAnotherRun() {
			stubPage(new PageImpl<>(List.of(project("node-1", "One")), PageRequest.of(0, CHUNK_SIZE), 1));

			assertNotNull(batchService.getNextProjectInputData());
			assertNull(batchService.getNextProjectInputData());

			batchService.initializeBatchProcessingParametersForTheNextProcess();

			assertNotNull(batchService.getNextProjectInputData());
		}
	}
}
