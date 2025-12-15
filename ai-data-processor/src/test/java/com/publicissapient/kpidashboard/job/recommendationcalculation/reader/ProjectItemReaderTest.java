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

package com.publicissapient.kpidashboard.job.recommendationcalculation.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.job.recommendationcalculation.service.RecommendationProjectBatchService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectItemReader Tests")
class ProjectItemReaderTest {

	@Mock
	private RecommendationProjectBatchService projectBatchService;

	private ProjectItemReader reader;

	private ProjectInputDTO project1;
	private ProjectInputDTO project2;
	private ProjectInputDTO project3;

	@BeforeEach
	void setUp() {
		// Create test projects
		project1 = ProjectInputDTO.builder().nodeId("project-1").name("Project Alpha").hierarchyLevel(5)
				.hierarchyLevelId("project").sprints(Collections.emptyList()).build();

		project2 = ProjectInputDTO.builder().nodeId("project-2").name("Project Beta").hierarchyLevel(5)
				.hierarchyLevelId("project").sprints(Collections.emptyList()).build();

		project3 = ProjectInputDTO.builder().nodeId("project-3").name("Project Gamma").hierarchyLevel(5)
				.hierarchyLevelId("project").sprints(Collections.emptyList()).build();

		// Initialize reader
		reader = new ProjectItemReader(projectBatchService);
	}

	/*
	 * @Nested
	 * 
	 * @DisplayName("Reading Projects") class ReadingProjects {
	 * 
	 * @Test
	 * 
	 * @DisplayName("Should read all projects sequentially") void
	 * read_MultipleProjects_ReturnsSequentially() throws Exception { // Arrange
	 * when(projectBatchService.getNextProjectInputData()) .thenReturn(project1,
	 * project2, project3, null);
	 * 
	 * // Act ProjectInputDTO first = reader.read(); ProjectInputDTO second =
	 * reader.read(); ProjectInputDTO third = reader.read(); ProjectInputDTO fourth
	 * = reader.read(); // Should be null after exhausted
	 * 
	 * // Assert assertNotNull(first); assertEquals("project-1", first.nodeId());
	 * assertEquals("Project Alpha", first.name());
	 * 
	 * assertNotNull(second); assertEquals("project-2", second.nodeId());
	 * assertEquals("Project Beta", second.name());
	 * 
	 * assertNotNull(third); assertEquals("project-3", third.nodeId());
	 * assertEquals("Project Gamma", third.name());
	 * 
	 * assertNull(fourth); // No more items }
	 * 
	 * @Test
	 * 
	 * @DisplayName("Should return null when no projects exist") void
	 * read_NoProjects_ReturnsNull() throws Exception { // Arrange
	 * when(projectBatchService.getNextProjectInputData()).thenReturn(null);
	 * 
	 * // Act ProjectInputDTO result = reader.read();
	 * 
	 * // Assert assertNull(result); }
	 * 
	 * @Test
	 * 
	 * @DisplayName("Should return null after all projects read") void
	 * read_AfterExhausted_ReturnsNull() throws Exception { // Arrange
	 * when(projectBatchService.getNextProjectInputData()) .thenReturn(project1,
	 * null, null);
	 * 
	 * // Act ProjectInputDTO first = reader.read(); ProjectInputDTO second =
	 * reader.read(); ProjectInputDTO third = reader.read();
	 * 
	 * // Assert assertNotNull(first); assertNull(second); assertNull(third); }
	 * 
	 * @Test
	 * 
	 * @DisplayName("Should map project fields correctly") void
	 * read_ProjectFields_MappedCorrectly() throws Exception { // Arrange
	 * ProjectInputDTO expectedProject = ProjectInputDTO.builder()
	 * .nodeId("test-id-123") .name("Test Project Name") .hierarchyLevel(5)
	 * .hierarchyLevelId("project") .sprints(Collections.emptyList()) .build();
	 * 
	 * when(projectBatchService.getNextProjectInputData()).thenReturn(
	 * expectedProject);
	 * 
	 * // Act ProjectInputDTO result = reader.read();
	 * 
	 * // Assert assertNotNull(result); assertEquals("test-id-123",
	 * result.nodeId()); assertEquals("Test Project Name", result.name()); }
	 * 
	 * @Test
	 * 
	 * @DisplayName("Should handle single project correctly") void
	 * read_SingleProject_Success() throws Exception { // Arrange
	 * when(projectBatchService.getNextProjectInputData()).thenReturn(project1,
	 * null);
	 * 
	 * // Act ProjectInputDTO first = reader.read(); ProjectInputDTO second =
	 * reader.read();
	 * 
	 * // Assert assertNotNull(first); assertEquals("project-1", first.nodeId());
	 * assertNull(second); }
	 * 
	 * @Test
	 * 
	 * @DisplayName("Should handle large number of projects") void
	 * read_LargeProjectList_Success() throws Exception { // Arrange int
	 * projectCount = 100; ProjectInputDTO[] projects = new
	 * ProjectInputDTO[projectCount + 1]; // +1 for null terminator for (int i = 0;
	 * i < projectCount; i++) { projects[i] = ProjectInputDTO.builder()
	 * .nodeId("project-" + i) .name("Project " + i) .hierarchyLevel(5)
	 * .hierarchyLevelId("project") .sprints(Collections.emptyList()) .build(); }
	 * projects[projectCount] = null; // null terminator
	 * 
	 * when(projectBatchService.getNextProjectInputData()).thenReturn(projects[0],
	 * projects);
	 * 
	 * // Act & Assert for (int i = 0; i < projectCount; i++) { ProjectInputDTO
	 * result = reader.read(); assertNotNull(result, "Project " + i +
	 * " should not be null"); assertEquals("project-" + i, result.nodeId()); }
	 * 
	 * // Should return null after all projects read assertNull(reader.read()); } }
	 */

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCases {

		@Test
		@DisplayName("Should handle project with null ID")
		void read_ProjectWithNullId_MapsCorrectly() throws Exception {
			// Arrange
			ProjectInputDTO projectWithNullId = ProjectInputDTO.builder().nodeId(null).name("Project with null ID")
					.hierarchyLevel(5).hierarchyLevelId("project").sprints(Collections.emptyList()).build();

			when(projectBatchService.getNextProjectInputData()).thenReturn(projectWithNullId);

			// Act
			ProjectInputDTO result = reader.read();

			// Assert
			assertNotNull(result);
			assertNull(result.nodeId());
			assertEquals("Project with null ID", result.name());
		}

		@Test
		@DisplayName("Should handle project with null name")
		void read_ProjectWithNullName_MapsCorrectly() throws Exception {
			// Arrange
			ProjectInputDTO projectWithNullName = ProjectInputDTO.builder().nodeId("project-1").name(null)
					.hierarchyLevel(5).hierarchyLevelId("project").sprints(Collections.emptyList()).build();

			when(projectBatchService.getNextProjectInputData()).thenReturn(projectWithNullName);

			// Act
			ProjectInputDTO result = reader.read();

			// Assert
			assertNotNull(result);
			assertEquals("project-1", result.nodeId());
			assertNull(result.name());
		}

		@Test
		@DisplayName("Should handle project with empty name")
		void read_ProjectWithEmptyName_MapsCorrectly() throws Exception {
			// Arrange
			ProjectInputDTO projectWithEmptyName = ProjectInputDTO.builder().nodeId("project-1").name("")
					.hierarchyLevel(5).hierarchyLevelId("project").sprints(Collections.emptyList()).build();

			when(projectBatchService.getNextProjectInputData()).thenReturn(projectWithEmptyName);

			// Act
			ProjectInputDTO result = reader.read();

			// Assert
			assertNotNull(result);
			assertEquals("project-1", result.nodeId());
			assertEquals("", result.name());
		}

		@Test
		@DisplayName("Should handle project with special characters in name")
		void read_ProjectWithSpecialCharacters_MapsCorrectly() throws Exception {
			// Arrange
			ProjectInputDTO projectWithSpecialChars = ProjectInputDTO.builder().nodeId("project-1")
					.name("Project <Test> & \"Quotes\" 'Single' !@#$%").hierarchyLevel(5).hierarchyLevelId("project")
					.sprints(Collections.emptyList()).build();

			when(projectBatchService.getNextProjectInputData()).thenReturn(projectWithSpecialChars);

			// Act
			ProjectInputDTO result = reader.read();

			// Assert
			assertNotNull(result);
			assertEquals("Project <Test> & \"Quotes\" 'Single' !@#$%", result.name());
		}
	}

	@Nested
	@DisplayName("Reader Lifecycle")
	class ReaderLifecycle {

		@Test
		@DisplayName("Should support multiple read cycles after reset")
		void read_MultipleReadCycles_Success() throws Exception {
			// Arrange
			when(projectBatchService.getNextProjectInputData()).thenReturn(project1, project2, null) // First cycle
					.thenReturn(project1, project2, null); // Second cycle after reset

			// First read cycle
			ProjectInputDTO first1 = reader.read();
			ProjectInputDTO second1 = reader.read();
			ProjectInputDTO third1 = reader.read();

			// Re-initialize reader for second cycle
			reader = new ProjectItemReader(projectBatchService);

			// Second read cycle
			ProjectInputDTO first2 = reader.read();
			ProjectInputDTO second2 = reader.read();
			ProjectInputDTO third2 = reader.read();

			// Assert
			assertNotNull(first1);
			assertNotNull(second1);
			assertNull(third1);

			assertNotNull(first2);
			assertNotNull(second2);
			assertNull(third2);

			assertEquals(first1.nodeId(), first2.nodeId());
			assertEquals(second1.nodeId(), second2.nodeId());
		}
	}
}
