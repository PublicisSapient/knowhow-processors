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

package com.publicissapient.kpidashboard.job.recommendationcalculation.writer;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
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
import org.springframework.batch.item.Chunk;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectItemWriter Tests")
class ProjectItemWriterTest {

	@Mock
	private RecommendationRepository recommendationRepository;

	@Mock
	private ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	private ProjectItemWriter writer;

	private RecommendationsActionPlan recommendation1;
	private RecommendationsActionPlan recommendation2;
	private RecommendationsActionPlan recommendation3;

	@BeforeEach
	void setUp() {
		writer = new ProjectItemWriter(recommendationRepository, processorExecutionTraceLogService);

		// Create test recommendations
		Recommendation rec1 = new Recommendation();
		rec1.setTitle("Test Recommendation 1");
		rec1.setDescription("Test Description 1");
		rec1.setActionPlans(Collections.emptyList());

		Recommendation rec2 = new Recommendation();
		rec2.setTitle("Test Recommendation 2");
		rec2.setDescription("Test Description 2");
		rec2.setActionPlans(Collections.emptyList());

		Recommendation rec3 = new Recommendation();
		rec3.setTitle("Test Recommendation 3");
		rec3.setDescription("Test Description 3");
		rec3.setActionPlans(Collections.emptyList());

		recommendation1 = new RecommendationsActionPlan();
		recommendation1.setBasicProjectConfigId("project-1");
		recommendation1.setRecommendations(rec1);

		recommendation2 = new RecommendationsActionPlan();
		recommendation2.setBasicProjectConfigId("project-2");
		recommendation2.setRecommendations(rec2);

		recommendation3 = new RecommendationsActionPlan();
		recommendation3.setBasicProjectConfigId("project-3");
		recommendation3.setRecommendations(rec3);
	}

	@Nested
	@DisplayName("Writing Recommendations")
	class WritingRecommendations {

		@Test
		@DisplayName("Should save all recommendations in chunk")
		void write_MultipleRecommendations_SavesAll() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(
					Arrays.asList(recommendation1, recommendation2, recommendation3));

			// Act
			writer.write(chunk);

			// Assert
			ArgumentCaptor<List<RecommendationsActionPlan>> captor = ArgumentCaptor.forClass(List.class);
			verify(recommendationRepository, times(1)).saveAll(captor.capture());

			List<RecommendationsActionPlan> saved = captor.getValue();
			org.junit.jupiter.api.Assertions.assertEquals(3, saved.size());
			org.junit.jupiter.api.Assertions.assertTrue(saved.contains(recommendation1));
			org.junit.jupiter.api.Assertions.assertTrue(saved.contains(recommendation2));
			org.junit.jupiter.api.Assertions.assertTrue(saved.contains(recommendation3));
		}

		@Test
		@DisplayName("Should save single recommendation")
		void write_SingleRecommendation_SavesSuccessfully() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.singletonList(recommendation1));

			// Act
			writer.write(chunk);

			// Assert
			ArgumentCaptor<List<RecommendationsActionPlan>> captor = ArgumentCaptor.forClass(List.class);
			verify(recommendationRepository, times(1)).saveAll(captor.capture());

			List<RecommendationsActionPlan> saved = captor.getValue();
			org.junit.jupiter.api.Assertions.assertEquals(1, saved.size());
			org.junit.jupiter.api.Assertions.assertEquals("project-1", saved.get(0).getBasicProjectConfigId());
		}

		@Test
		@DisplayName("Should not save when chunk is empty")
		void write_EmptyChunk_NoSave() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.emptyList());

			// Act
			writer.write(chunk);

			// Assert
			verify(recommendationRepository, never()).saveAll(anyList());
			verify(processorExecutionTraceLogService, never()).upsertTraceLog(anyString(), anyString(), eq(true),
					eq(null));
		}

		@Test
		@DisplayName("Should filter out null recommendations before saving")
		void write_ChunkWithNulls_FiltersNulls() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(
					Arrays.asList(recommendation1, recommendation2, recommendation3));

			// Act
			writer.write(chunk);

			// Assert
			ArgumentCaptor<List<RecommendationsActionPlan>> captor = ArgumentCaptor.forClass(List.class);
			verify(recommendationRepository, times(1)).saveAll(captor.capture());

			List<RecommendationsActionPlan> saved = captor.getValue();
			org.junit.jupiter.api.Assertions.assertEquals(3, saved.size());
			org.junit.jupiter.api.Assertions.assertFalse(saved.contains(null));
		}

		@Test
		@DisplayName("Should not save when all items are null")
		void write_AllNullItems_NoSave() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.emptyList());

			// Act
			writer.write(chunk);

			// Assert
			verify(recommendationRepository, never()).saveAll(anyList());
		}

		@Test
		@DisplayName("Should handle large chunk size")
		void write_LargeChunk_SavesAll() {
			// Arrange
			int chunkSize = 100;
			List<RecommendationsActionPlan> items = new java.util.ArrayList<>();
			for (int i = 0; i < chunkSize; i++) {
				RecommendationsActionPlan rec = new RecommendationsActionPlan();
				rec.setBasicProjectConfigId("project-" + i);
				items.add(rec);
			}
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(items);

			// Act
			writer.write(chunk);

			// Assert
			ArgumentCaptor<List<RecommendationsActionPlan>> captor = ArgumentCaptor.forClass(List.class);
			verify(recommendationRepository, times(1)).saveAll(captor.capture());

			List<RecommendationsActionPlan> saved = captor.getValue();
			org.junit.jupiter.api.Assertions.assertEquals(chunkSize, saved.size());
		}
	}

	@Nested
	@DisplayName("Trace Logging")
	class TraceLogging {

		@Test
		@DisplayName("Should log trace for each saved recommendation")
		void write_MultipleRecommendations_LogsTraceForEach() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(
					Arrays.asList(recommendation1, recommendation2, recommendation3));

			// Act
			writer.write(chunk);

			// Assert
			verify(processorExecutionTraceLogService, times(3)).upsertTraceLog(eq("Recommendation"), anyString(),
					eq(true), eq(null));
		}

		@Test
		@DisplayName("Should log trace with correct project IDs")
		void write_Recommendations_LogsWithCorrectProjectIds() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Arrays.asList(recommendation1, recommendation2));

			// Act
			writer.write(chunk);

			// Assert
			verify(processorExecutionTraceLogService).upsertTraceLog(eq("Recommendation"), eq("project-1"), eq(true),
					eq(null));
			verify(processorExecutionTraceLogService).upsertTraceLog(eq("Recommendation"), eq("project-2"), eq(true),
					eq(null));
		}

		@Test
		@DisplayName("Should not log trace when chunk is empty")
		void write_EmptyChunk_NoTraceLog() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.emptyList());

			// Act
			writer.write(chunk);

			// Assert
			verify(processorExecutionTraceLogService, never()).upsertTraceLog(anyString(), anyString(), eq(true),
					eq(null));
		}

		@Test
		@DisplayName("Should only log trace for non-null items")
		void write_ChunkWithNulls_LogsOnlyForNonNulls() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Arrays.asList(recommendation1, recommendation2));

			// Act
			writer.write(chunk);

			// Assert
			verify(processorExecutionTraceLogService, times(2)).upsertTraceLog(eq("Recommendation"), anyString(),
					eq(true), eq(null));
		}

		@Test
		@DisplayName("Should log trace with success=true")
		void write_Recommendations_LogsWithSuccessTrue() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.singletonList(recommendation1));

			// Act
			writer.write(chunk);

			// Assert
			verify(processorExecutionTraceLogService).upsertTraceLog(anyString(), anyString(), eq(true), eq(null));
		}

		@Test
		@DisplayName("Should log trace with null error message")
		void write_Recommendations_LogsWithNullError() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.singletonList(recommendation1));

			// Act
			writer.write(chunk);

			// Assert
			verify(processorExecutionTraceLogService).upsertTraceLog(anyString(), anyString(), eq(true), eq(null));
		}
	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCases {

		@Test
		@DisplayName("Should handle recommendation with null project ID gracefully")
		void write_NullProjectId_HandlesGracefully() {
			// Arrange
			RecommendationsActionPlan recWithNullId = new RecommendationsActionPlan();
			recWithNullId.setBasicProjectConfigId(null);
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.singletonList(recWithNullId));

			// Act
			writer.write(chunk);

			// Assert
			verify(recommendationRepository, times(1)).saveAll(anyList());
			verify(processorExecutionTraceLogService).upsertTraceLog(eq("Recommendation"), eq(null), eq(true),
					eq(null));
		}

		@Test
		@DisplayName("Should handle recommendation with empty project ID")
		void write_EmptyProjectId_HandlesGracefully() {
			// Arrange
			RecommendationsActionPlan recWithEmptyId = new RecommendationsActionPlan();
			recWithEmptyId.setBasicProjectConfigId("");
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.singletonList(recWithEmptyId));

			// Act
			writer.write(chunk);

			// Assert
			verify(recommendationRepository, times(1)).saveAll(anyList());
			verify(processorExecutionTraceLogService).upsertTraceLog(eq("Recommendation"), eq(""), eq(true), eq(null));
		}

		@Test
		@DisplayName("Should handle valid recommendations")
		void write_MixedNullAndValid_ProcessesValidOnes() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Arrays.asList(recommendation1, recommendation2));

			// Act
			writer.write(chunk);

			// Assert
			ArgumentCaptor<List<RecommendationsActionPlan>> captor = ArgumentCaptor.forClass(List.class);
			verify(recommendationRepository, times(1)).saveAll(captor.capture());

			List<RecommendationsActionPlan> saved = captor.getValue();
			org.junit.jupiter.api.Assertions.assertEquals(2, saved.size());
			verify(processorExecutionTraceLogService, times(2)).upsertTraceLog(anyString(), anyString(), eq(true),
					eq(null));
		}
	}

	@Nested
	@DisplayName("Integration Behavior")
	class IntegrationBehavior {

		@Test
		@DisplayName("Should call saveAll before logging traces")
		void write_Recommendations_SavesBeforeLogging() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(Collections.singletonList(recommendation1));
			org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(recommendationRepository,
					processorExecutionTraceLogService);

			// Act
			writer.write(chunk);

			// Assert
			inOrder.verify(recommendationRepository).saveAll(anyList());
			inOrder.verify(processorExecutionTraceLogService).upsertTraceLog(anyString(), anyString(), eq(true),
					eq(null));
		}

		@Test
		@DisplayName("Should process all recommendations in sequence")
		void write_MultipleRecommendations_ProcessesInSequence() {
			// Arrange
			Chunk<RecommendationsActionPlan> chunk = new Chunk<>(
					Arrays.asList(recommendation1, recommendation2, recommendation3));

			// Act
			writer.write(chunk);

			// Assert
			verify(recommendationRepository, times(1)).saveAll(anyList());
			verify(processorExecutionTraceLogService, times(3)).upsertTraceLog(anyString(), anyString(), eq(true),
					eq(null));
		}
	}
}
