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

package com.publicissapient.kpidashboard.job.epichygienecalculation.processor;

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

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.epichygienecalculation.exception.EpicHygieneKpiUnavailableException;
import com.publicissapient.kpidashboard.job.epichygienecalculation.service.EpicHygieneCalculationService;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("EpicHygieneProjectItemProcessor Tests")
class EpicHygieneProjectItemProcessorTest {

	@Mock private EpicHygieneCalculationService calculationService;

	@Mock private ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	private EpicHygieneProjectItemProcessor processor;
	private ProjectInputDTO projectInput;

	@BeforeEach
	void setUp() {
		processor =
				new EpicHygieneProjectItemProcessor(calculationService, processorExecutionTraceLogService);

		projectInput =
				ProjectInputDTO.builder()
						.nodeId("node-1")
						.name("Test Project")
						.hierarchyLevel(5)
						.hierarchyLevelId("project")
						.basicProjectConfigId("project-1")
						.sprints(Collections.emptyList())
						.build();
	}

	private EpicHygieneData epicHygieneData() {
		return EpicHygieneData.builder()
				.basicProjectConfigId("project-1")
				.projectNodeId("node-1")
				.projectName("Test Project")
				.kpiId("kpi312")
				.kpiName("Epic Hygiene")
				.totalActiveEpics(10)
				.constructionReadyEpics(6)
				.atRiskEpics(2)
				.avgReadinessScore(64.5d)
				.metrics(List.of())
				.calculationDate(Instant.now())
				.build();
	}

	@Nested
	@DisplayName("Successful processing")
	class SuccessfulProcessing {

		@Test
		@DisplayName("Should return the computed epic hygiene snapshot")
		void process_ValidProject_ReturnsSnapshot() {
			when(calculationService.computeForProject(projectInput)).thenReturn(epicHygieneData());

			EpicHygieneData result = processor.process(projectInput);

			assertNotNull(result);
			assertEquals("project-1", result.getBasicProjectConfigId());
			assertEquals(10, result.getTotalActiveEpics());
			verify(calculationService, times(1)).computeForProject(projectInput);
			verify(processorExecutionTraceLogService, never())
					.upsertTraceLog(anyString(), anyString(), anyBoolean(), anyString());
		}

		@Test
		@DisplayName("Should pass through a fallback record without tracing a processing failure")
		void process_FallbackRecord_ReturnsItAsIs() {
			EpicHygieneData fallback = epicHygieneData();
			fallback.setFallback(true);
			fallback.setFailureReason("KPI unavailable");
			when(calculationService.computeForProject(projectInput)).thenReturn(fallback);

			EpicHygieneData result = processor.process(projectInput);

			assertNotNull(result);
			assertTrue(result.isFallback());
			verify(processorExecutionTraceLogService, never())
					.upsertTraceLog(anyString(), anyString(), anyBoolean(), anyString());
		}

		@Test
		@DisplayName("Should process multiple projects independently")
		void process_MultipleProjects_AllProcessed() {
			ProjectInputDTO secondProject =
					ProjectInputDTO.builder()
							.nodeId("node-2")
							.name("Second Project")
							.hierarchyLevel(5)
							.hierarchyLevelId("project")
							.basicProjectConfigId("project-2")
							.sprints(Collections.emptyList())
							.build();

			EpicHygieneData secondData = epicHygieneData();
			secondData.setBasicProjectConfigId("project-2");

			when(calculationService.computeForProject(projectInput)).thenReturn(epicHygieneData());
			when(calculationService.computeForProject(secondProject)).thenReturn(secondData);

			EpicHygieneData firstResult = processor.process(projectInput);
			EpicHygieneData secondResult = processor.process(secondProject);

			assertNotNull(firstResult);
			assertNotNull(secondResult);
			assertEquals("project-1", firstResult.getBasicProjectConfigId());
			assertEquals("project-2", secondResult.getBasicProjectConfigId());
		}
	}

	@Nested
	@DisplayName("Exception handling")
	class ExceptionHandling {

		@Test
		@DisplayName("Should filter the item out and trace when the KPI stays unavailable")
		void process_KpiUnavailable_ReturnsNullAndTraces() {
			when(calculationService.computeForProject(projectInput))
					.thenThrow(new EpicHygieneKpiUnavailableException("KPI down after 3 attempts"));

			assertNull(processor.process(projectInput));

			verify(processorExecutionTraceLogService, times(1))
					.upsertTraceLog(
							eq(JobConstants.JOB_EPIC_HYGIENE_CALCULATION),
							eq("project-1"),
							eq(false),
							anyString());
		}

		@Test
		@DisplayName("Should capture a detailed error message in the trace log")
		void process_Exception_CapturesDetailedErrorMessage() {
			when(calculationService.computeForProject(projectInput))
					.thenThrow(new RuntimeException("Parsing failed"));

			processor.process(projectInput);

			ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
			verify(processorExecutionTraceLogService)
					.upsertTraceLog(
							eq(JobConstants.JOB_EPIC_HYGIENE_CALCULATION),
							eq("project-1"),
							eq(false),
							errorCaptor.capture());

			String errorMessage = errorCaptor.getValue();
			assertTrue(errorMessage.contains("Test Project"));
			assertTrue(errorMessage.contains("RuntimeException"));
			assertTrue(errorMessage.contains("Parsing failed"));
		}

		@Test
		@DisplayName("Should include the root cause of nested exceptions")
		void process_NestedException_IncludesRootCause() {
			when(calculationService.computeForProject(projectInput))
					.thenThrow(
							new RuntimeException("Service call failed", new IllegalStateException("timeout")));

			processor.process(projectInput);

			ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
			verify(processorExecutionTraceLogService)
					.upsertTraceLog(anyString(), anyString(), eq(false), errorCaptor.capture());

			assertTrue(errorCaptor.getValue().contains("Root cause"));
		}

		@Test
		@DisplayName("Should handle a NullPointerException gracefully")
		void process_NullPointerException_ReturnsNull() {
			when(calculationService.computeForProject(projectInput))
					.thenThrow(new NullPointerException("Required field is null"));

			assertNull(processor.process(projectInput));
			verify(processorExecutionTraceLogService, times(1))
					.upsertTraceLog(anyString(), anyString(), eq(false), anyString());
		}

		@Test
		@DisplayName("Should not break when the failing exception carries no message")
		void process_ExceptionWithoutMessage_StillTraces() {
			when(calculationService.computeForProject(projectInput))
					.thenThrow(new IllegalStateException());

			assertNull(processor.process(projectInput));
			verify(processorExecutionTraceLogService, times(1))
					.upsertTraceLog(anyString(), anyString(), eq(false), anyString());
		}
	}

	@Nested
	@DisplayName("Edge cases")
	class EdgeCases {

		@Test
		@DisplayName("Should return null when the service returns null")
		void process_ServiceReturnsNull_ReturnsNull() {
			when(calculationService.computeForProject(projectInput)).thenReturn(null);

			assertNull(processor.process(projectInput));
			verify(processorExecutionTraceLogService, never())
					.upsertTraceLog(anyString(), anyString(), anyBoolean(), anyString());
		}

		@Test
		@DisplayName("Should trace with the project id even when the project name is null")
		void process_ProjectWithoutName_TracesWithProjectId() {
			ProjectInputDTO namelessProject =
					ProjectInputDTO.builder()
							.nodeId("node-3")
							.hierarchyLevel(5)
							.hierarchyLevelId("project")
							.basicProjectConfigId("project-3")
							.sprints(Collections.emptyList())
							.build();
			when(calculationService.computeForProject(namelessProject))
					.thenThrow(new RuntimeException("boom"));

			assertNull(processor.process(namelessProject));
			verify(processorExecutionTraceLogService)
					.upsertTraceLog(anyString(), eq("project-3"), eq(false), anyString());
		}
	}
}
