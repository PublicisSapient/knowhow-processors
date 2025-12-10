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

package com.publicissapient.kpidashboard.job.recommendationcalculation.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationMetadata;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.recommendationcalculation.service.RecommendationCalculationService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectItemProcessor Tests")
class ProjectItemProcessorTest {

	@Mock
	private RecommendationCalculationService recommendationCalculationService;

	@Mock
	private ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	private ProjectItemProcessor processor;

	private ProjectInputDTO projectInput;
	private RecommendationsActionPlan recommendation;

	@BeforeEach
	void setUp() {
		processor = new ProjectItemProcessor(recommendationCalculationService, processorExecutionTraceLogService);

		// Create test project input
		projectInput = ProjectInputDTO.builder().nodeId("project-1").name("Test Project").hierarchyLevel(5)
				.hierarchyLevelId("project").sprints(Collections.emptyList()).build();

		// Create test recommendation
		recommendation = new RecommendationsActionPlan();
		recommendation.setBasicProjectConfigId("project-1");
		RecommendationMetadata metadata = new RecommendationMetadata();
		metadata.setPersona(Persona.SCRUM_MASTER);
		recommendation.setMetadata(metadata);

		// Create a proper Recommendation object
		Recommendation rec = new Recommendation();
		rec.setTitle("Test Recommendation");
		rec.setDescription("Test Description");
		rec.setActionPlans(Collections.emptyList());
		recommendation.setRecommendations(rec);
	}

	@Nested
	@DisplayName("Successful Processing")
	class SuccessfulProcessing {

		@Test
		@DisplayName("Should process project successfully")
		void process_ValidProject_ReturnsRecommendation() throws Exception {
			// Arrange
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenReturn(recommendation);

			// Act
			RecommendationsActionPlan result = processor.process(projectInput);

			// Assert
			assertNotNull(result);
			assertEquals("project-1", result.getBasicProjectConfigId());
			assertEquals(Persona.SCRUM_MASTER, result.getMetadata().getPersona());
			verify(recommendationCalculationService, times(1)).calculateRecommendationsForProject(projectInput);
			verify(processorExecutionTraceLogService, never()).upsertTraceLog(anyString(), anyString(), anyBoolean(),
					anyString());
		}

		@Test
		@DisplayName("Should process project with different persona")
		void process_DifferentPersona_ReturnsCorrectRecommendation() throws Exception {
			// Arrange
			RecommendationMetadata metadata = new RecommendationMetadata();
			metadata.setPersona(Persona.PRODUCT_OWNER);
			recommendation.setMetadata(metadata);

			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenReturn(recommendation);

			// Act
			RecommendationsActionPlan result = processor.process(projectInput);

			// Assert
			assertNotNull(result);
			assertEquals(Persona.PRODUCT_OWNER, result.getMetadata().getPersona());
		}

		@Test
		@DisplayName("Should process multiple projects sequentially")
		void process_MultipleProjects_AllProcessedSuccessfully() throws Exception {
			// Arrange
			ProjectInputDTO project1 = ProjectInputDTO.builder().nodeId("project-1").name("Project 1").hierarchyLevel(5)
					.hierarchyLevelId("project").sprints(Collections.emptyList()).build();
			ProjectInputDTO project2 = ProjectInputDTO.builder().nodeId("project-2").name("Project 2").hierarchyLevel(5)
					.hierarchyLevelId("project").sprints(Collections.emptyList()).build();

			RecommendationsActionPlan rec1 = new RecommendationsActionPlan();
			rec1.setBasicProjectConfigId("project-1");
			rec1.setMetadata(new RecommendationMetadata());

			RecommendationsActionPlan rec2 = new RecommendationsActionPlan();
			rec2.setBasicProjectConfigId("project-2");
			rec2.setMetadata(new RecommendationMetadata());

			when(recommendationCalculationService.calculateRecommendationsForProject(project1)).thenReturn(rec1);
			when(recommendationCalculationService.calculateRecommendationsForProject(project2)).thenReturn(rec2);

			// Act
			RecommendationsActionPlan result1 = processor.process(project1);
			RecommendationsActionPlan result2 = processor.process(project2);

			// Assert
			assertNotNull(result1);
			assertNotNull(result2);
			assertEquals("project-1", result1.getBasicProjectConfigId());
			assertEquals("project-2", result2.getBasicProjectConfigId());
		}
	}

	@Nested
	@DisplayName("Exception Handling")
	class ExceptionHandling {

		@Test
		@DisplayName("Should return null and log trace when service throws exception")
		void process_ServiceException_ReturnsNullAndLogsTrace() throws Exception {
			// Arrange
			RuntimeException exception = new RuntimeException("AI Gateway unavailable");
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenThrow(exception);

			// Act
			RecommendationsActionPlan result = processor.process(projectInput);

			// Assert
			assertNull(result);
			verify(processorExecutionTraceLogService, times(1)).upsertTraceLog(eq("Recommendation"), eq("project-1"),
					eq(false), anyString());
		}

		@Test
		@DisplayName("Should capture detailed error message in trace log")
		void process_Exception_CapturesDetailedErrorMessage() throws Exception {
			// Arrange
			RuntimeException exception = new RuntimeException("Parsing failed");
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenThrow(exception);

			// Act
			processor.process(projectInput);

			// Assert
			ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);
			verify(processorExecutionTraceLogService).upsertTraceLog(eq("Recommendation"), eq("project-1"), eq(false),
					errorMessageCaptor.capture());

			String errorMessage = errorMessageCaptor.getValue();
			assertNotNull(errorMessage);
            assertTrue(errorMessage.contains("Test Project"));
            assertTrue(errorMessage.contains("RuntimeException"));
            assertTrue(errorMessage.contains("Parsing failed"));
		}

		@Test
		@DisplayName("Should handle NullPointerException gracefully")
		void process_NullPointerException_ReturnsNull() throws Exception {
			// Arrange
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenThrow(new NullPointerException("Required field is null"));

			// Act
			RecommendationsActionPlan result = processor.process(projectInput);

			// Assert
			assertNull(result);
			verify(processorExecutionTraceLogService, times(1)).upsertTraceLog(anyString(), anyString(), eq(false),
					anyString());
		}

		@Test
		@DisplayName("Should handle IllegalArgumentException gracefully")
		void process_IllegalArgumentException_ReturnsNull() throws Exception {
			// Arrange
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenThrow(new IllegalArgumentException("Invalid project configuration"));

			// Act
			RecommendationsActionPlan result = processor.process(projectInput);

			// Assert
			assertNull(result);
		}

		@Test
		@DisplayName("Should include root cause in error message")
		void process_NestedExceptions_IncludesRootCause() throws Exception {
			// Arrange
			Exception rootCause = new IllegalStateException("Connection timeout");
			RuntimeException wrappedException = new RuntimeException("Service call failed", rootCause);
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenThrow(wrappedException);

			// Act
			processor.process(projectInput);

			// Assert
			ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);
			verify(processorExecutionTraceLogService).upsertTraceLog(anyString(), anyString(), eq(false),
					errorMessageCaptor.capture());

			String errorMessage = errorMessageCaptor.getValue();
            assertTrue(errorMessage.contains("Root cause"));
		}
	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCases {

		@Test
		@DisplayName("Should process project with minimal data")
		void process_MinimalProjectData_Success() throws Exception {
			// Arrange
			ProjectInputDTO minimalProject = ProjectInputDTO.builder().nodeId("id").name("name").hierarchyLevel(5)
					.hierarchyLevelId("project").sprints(Collections.emptyList()).build();
			RecommendationsActionPlan minimalRec = new RecommendationsActionPlan();
			minimalRec.setBasicProjectConfigId("id");
			minimalRec.setMetadata(new RecommendationMetadata());

			when(recommendationCalculationService.calculateRecommendationsForProject(minimalProject))
					.thenReturn(minimalRec);

			// Act
			RecommendationsActionPlan result = processor.process(minimalProject);

			// Assert
			assertNotNull(result);
			assertEquals("id", result.getBasicProjectConfigId());
		}

		@Test
		@DisplayName("Should handle project with special characters in name")
		void process_SpecialCharactersInName_Success() throws Exception {
			// Arrange
			ProjectInputDTO specialProject = ProjectInputDTO.builder().nodeId("project-1")
					.name("Test <Project> & \"Name\"").hierarchyLevel(5).hierarchyLevelId("project")
					.sprints(Collections.emptyList()).build();
			when(recommendationCalculationService.calculateRecommendationsForProject(specialProject))
					.thenReturn(recommendation);

			// Act
			RecommendationsActionPlan result = processor.process(specialProject);

			// Assert
			assertNotNull(result);
		}

		@Test
		@DisplayName("Should process project with very long name")
		void process_VeryLongProjectName_Success() throws Exception {
			// Arrange
			String longName = "A".repeat(500);
			ProjectInputDTO longNameProject = ProjectInputDTO.builder().nodeId("project-1").name(longName)
					.hierarchyLevel(5).hierarchyLevelId("project").sprints(Collections.emptyList()).build();
			when(recommendationCalculationService.calculateRecommendationsForProject(longNameProject))
					.thenReturn(recommendation);

			// Act
			RecommendationsActionPlan result = processor.process(longNameProject);

			// Assert
			assertNotNull(result);
		}
	}

	@Nested
	@DisplayName("Trace Logging Behavior")
	class TraceLoggingBehavior {

		@Test
		@DisplayName("Should not log trace on success")
		void process_SuccessfulProcessing_NoTraceLog() throws Exception {
			// Arrange
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenReturn(recommendation);

			// Act
			processor.process(projectInput);

			// Assert
			verify(processorExecutionTraceLogService, never()).upsertTraceLog(anyString(), anyString(), anyBoolean(),
					anyString());
		}

		@Test
		@DisplayName("Should log trace with correct job name on failure")
		void process_Failure_LogsWithCorrectJobName() throws Exception {
			// Arrange
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenThrow(new RuntimeException("Error"));

			// Act
			processor.process(projectInput);

			// Assert
			verify(processorExecutionTraceLogService).upsertTraceLog(eq("Recommendation"), anyString(), eq(false),
					anyString());
		}

		@Test
		@DisplayName("Should log trace with correct project ID on failure")
		void process_Failure_LogsWithCorrectProjectId() throws Exception {
			// Arrange
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenThrow(new RuntimeException("Error"));

			// Act
			processor.process(projectInput);

			// Assert
			verify(processorExecutionTraceLogService).upsertTraceLog(anyString(), eq("project-1"), eq(false),
					anyString());
		}

		@Test
		@DisplayName("Should log trace with success=false on failure")
		void process_Failure_LogsWithSuccessFalse() throws Exception {
			// Arrange
			when(recommendationCalculationService.calculateRecommendationsForProject(projectInput))
					.thenThrow(new RuntimeException("Error"));

			// Act
			processor.process(projectInput);

			// Assert
			verify(processorExecutionTraceLogService).upsertTraceLog(anyString(), anyString(), eq(false), anyString());
		}
	}
}
