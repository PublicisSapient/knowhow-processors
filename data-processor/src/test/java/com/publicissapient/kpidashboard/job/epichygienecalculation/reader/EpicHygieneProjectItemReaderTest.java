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

package com.publicissapient.kpidashboard.job.epichygienecalculation.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.job.epichygienecalculation.service.EpicHygieneProjectBatchService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("EpicHygieneProjectItemReader Tests")
class EpicHygieneProjectItemReaderTest {

	@Mock private EpicHygieneProjectBatchService projectBatchService;

	private EpicHygieneProjectItemReader reader;

	@BeforeEach
	void setUp() {
		reader = new EpicHygieneProjectItemReader(projectBatchService);
	}

	private ProjectInputDTO project(String id) {
		return ProjectInputDTO.builder()
				.nodeId(id)
				.name("Project " + id)
				.hierarchyLevel(5)
				.hierarchyLevelId("project")
				.basicProjectConfigId(id)
				.sprints(Collections.emptyList())
				.build();
	}

	@Test
	@DisplayName("Should return the project supplied by the batch service")
	void read_ProjectAvailable_ReturnsProject() {
		ProjectInputDTO expected = project("project-1");
		when(projectBatchService.getNextProjectInputData()).thenReturn(expected);

		ProjectInputDTO result = reader.read();

		assertNotNull(result);
		assertEquals("project-1", result.basicProjectConfigId());
		verify(projectBatchService, times(1)).getNextProjectInputData();
	}

	@Test
	@DisplayName("Should return null to signal the end of the stream")
	void read_NoMoreProjects_ReturnsNull() {
		when(projectBatchService.getNextProjectInputData()).thenReturn(null);

		assertNull(reader.read());
	}

	@Test
	@DisplayName("Should stream projects one by one until exhausted")
	void read_MultipleProjects_ReturnsThemInOrderThenNull() {
		when(projectBatchService.getNextProjectInputData())
				.thenReturn(project("project-1"), project("project-2"), null);

		ProjectInputDTO first = reader.read();
		ProjectInputDTO second = reader.read();

		assertNotNull(first);
		assertNotNull(second);
		assertEquals("project-1", first.basicProjectConfigId());
		assertEquals("project-2", second.basicProjectConfigId());
		assertNull(reader.read());
		verify(projectBatchService, times(3)).getNextProjectInputData();
	}

	@Test
	@DisplayName("Should not fail when the project has no display name")
	void read_ProjectWithoutName_ReturnsProject() {
		ProjectInputDTO projectWithoutName =
				ProjectInputDTO.builder()
						.nodeId("node-1")
						.hierarchyLevel(5)
						.hierarchyLevelId("project")
						.basicProjectConfigId("project-1")
						.sprints(Collections.emptyList())
						.build();
		when(projectBatchService.getNextProjectInputData()).thenReturn(projectWithoutName);

		assertNotNull(reader.read());
	}

	@Test
	@DisplayName("Should propagate a batch service failure")
	void read_BatchServiceThrows_PropagatesException() {
		when(projectBatchService.getNextProjectInputData())
				.thenThrow(new IllegalStateException("mongo unavailable"));

		assertThrows(IllegalStateException.class, () -> reader.read());
	}
}
