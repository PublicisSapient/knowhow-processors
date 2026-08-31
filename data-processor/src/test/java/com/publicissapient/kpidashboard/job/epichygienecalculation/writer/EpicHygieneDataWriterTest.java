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

package com.publicissapient.kpidashboard.job.epichygienecalculation.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneData;
import com.publicissapient.kpidashboard.common.repository.jira.EpicHygieneDataRepository;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;

@ExtendWith(MockitoExtension.class)
@DisplayName("EpicHygieneDataWriter Tests")
class EpicHygieneDataWriterTest {

	@Mock private EpicHygieneDataRepository epicHygieneDataRepository;

	@Mock private ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	private EpicHygieneDataWriter writer;

	@BeforeEach
	void setUp() {
		writer =
				new EpicHygieneDataWriter(epicHygieneDataRepository, processorExecutionTraceLogService);
	}

	private EpicHygieneData data(String projectId) {
		return EpicHygieneData.builder()
				.basicProjectConfigId(projectId)
				.projectNodeId("node-" + projectId)
				.projectName("Project " + projectId)
				.kpiId("kpi312")
				.totalActiveEpics(5)
				.metrics(List.of())
				.calculationDate(Instant.now())
				.build();
	}

	private EpicHygieneData fallbackData(String projectId, String reason) {
		EpicHygieneData fallback = data(projectId);
		fallback.setFallback(true);
		fallback.setFailureReason(reason);
		return fallback;
	}

	@SuppressWarnings("unchecked")
	private List<EpicHygieneData> captureSaved() {
		ArgumentCaptor<List<EpicHygieneData>> captor = ArgumentCaptor.forClass(List.class);
		verify(epicHygieneDataRepository, times(1)).saveAll(captor.capture());
		return captor.getValue();
	}

	@Nested
	@DisplayName("Persisting snapshots")
	class Persisting {

		@Test
		@DisplayName("Should persist every snapshot of the chunk")
		void write_MultipleItems_SavesAll() {
			Chunk<EpicHygieneData> chunk =
					new Chunk<>(Arrays.asList(data("project-1"), data("project-2"), data("project-3")));

			writer.write(chunk);

			assertEquals(3, captureSaved().size());
		}

		@Test
		@DisplayName("Should remove the previous snapshot of a project before saving the new one")
		void write_Items_DeletesPreviousSnapshotFirst() {
			Chunk<EpicHygieneData> chunk =
					new Chunk<>(Arrays.asList(data("project-1"), data("project-2")));

			writer.write(chunk);

			verify(epicHygieneDataRepository).deleteByBasicProjectConfigId("project-1");
			verify(epicHygieneDataRepository).deleteByBasicProjectConfigId("project-2");

			InOrder inOrder = inOrder(epicHygieneDataRepository);
			inOrder.verify(epicHygieneDataRepository, times(2)).deleteByBasicProjectConfigId(anyString());
			inOrder.verify(epicHygieneDataRepository).saveAll(anyList());
		}

		@Test
		@DisplayName("Should delete only once per project when a chunk holds duplicates")
		void write_DuplicateProjects_DeletesOncePerProject() {
			Chunk<EpicHygieneData> chunk =
					new Chunk<>(Arrays.asList(data("project-1"), data("project-1")));

			writer.write(chunk);

			verify(epicHygieneDataRepository, times(1)).deleteByBasicProjectConfigId("project-1");
			assertEquals(2, captureSaved().size());
		}

		@Test
		@DisplayName("Should do nothing for an empty chunk")
		void write_EmptyChunk_NoInteraction() {
			writer.write(new Chunk<>(Collections.emptyList()));

			verify(epicHygieneDataRepository, never()).saveAll(anyList());
			verify(epicHygieneDataRepository, never()).deleteByBasicProjectConfigId(anyString());
			verify(processorExecutionTraceLogService, never())
					.upsertTraceLog(anyString(), anyString(), anyBoolean(), anyString());
		}

		@Test
		@DisplayName("Should handle a large chunk in one round trip")
		void write_LargeChunk_SavesAllAtOnce() {
			List<EpicHygieneData> items = new ArrayList<>();
			for (int index = 0; index < 100; index++) {
				items.add(data("project-" + index));
			}

			writer.write(new Chunk<>(items));

			assertEquals(100, captureSaved().size());
		}
	}

	@Nested
	@DisplayName("Trace logging")
	class TraceLogging {

		@Test
		@DisplayName("Should log a successful trace for each persisted snapshot")
		void write_ValidItems_LogsSuccessPerProject() {
			Chunk<EpicHygieneData> chunk =
					new Chunk<>(Arrays.asList(data("project-1"), data("project-2")));

			writer.write(chunk);

			verify(processorExecutionTraceLogService)
					.upsertTraceLog(
							eq(JobConstants.JOB_EPIC_HYGIENE_CALCULATION), eq("project-1"), eq(true), eq(null));
			verify(processorExecutionTraceLogService)
					.upsertTraceLog(
							eq(JobConstants.JOB_EPIC_HYGIENE_CALCULATION), eq("project-2"), eq(true), eq(null));
		}

		@Test
		@DisplayName("Should log a failed trace carrying the reason for fallback snapshots")
		void write_FallbackItem_LogsFailureWithReason() {
			Chunk<EpicHygieneData> chunk =
					new Chunk<>(Collections.singletonList(fallbackData("project-1", "KPI unavailable")));

			writer.write(chunk);

			verify(processorExecutionTraceLogService)
					.upsertTraceLog(
							eq(JobConstants.JOB_EPIC_HYGIENE_CALCULATION),
							eq("project-1"),
							eq(false),
							eq("KPI unavailable"));
		}

		@Test
		@DisplayName("Should log mixed success and fallback traces in the same chunk")
		void write_MixedItems_LogsBothOutcomes() {
			Chunk<EpicHygieneData> chunk =
					new Chunk<>(
							Arrays.asList(data("project-1"), fallbackData("project-2", "gateway timeout")));

			writer.write(chunk);

			verify(processorExecutionTraceLogService)
					.upsertTraceLog(anyString(), eq("project-1"), eq(true), eq(null));
			verify(processorExecutionTraceLogService)
					.upsertTraceLog(anyString(), eq("project-2"), eq(false), eq("gateway timeout"));
		}

		@Test
		@DisplayName("Should save before tracing so a trace never claims unsaved data")
		void write_Items_SavesBeforeTracing() {
			Chunk<EpicHygieneData> chunk = new Chunk<>(Collections.singletonList(data("project-1")));
			InOrder inOrder = inOrder(epicHygieneDataRepository, processorExecutionTraceLogService);

			writer.write(chunk);

			inOrder.verify(epicHygieneDataRepository).saveAll(anyList());
			inOrder
					.verify(processorExecutionTraceLogService)
					.upsertTraceLog(anyString(), anyString(), eq(true), eq(null));
		}
	}

	@Nested
	@DisplayName("Edge cases")
	class EdgeCases {

		@Test
		@DisplayName("Should skip null items")
		void write_ChunkWithNulls_SkipsThem() {
			Chunk<EpicHygieneData> chunk =
					new Chunk<>(Arrays.asList(data("project-1"), null, data("project-2")));

			writer.write(chunk);

			List<EpicHygieneData> saved = captureSaved();
			assertEquals(2, saved.size());
			assertTrue(saved.stream().noneMatch(java.util.Objects::isNull));
		}

		@Test
		@DisplayName("Should skip items without a project id")
		void write_ItemWithNullProjectId_SkipsIt() {
			EpicHygieneData invalid = data("project-1");
			invalid.setBasicProjectConfigId(null);
			Chunk<EpicHygieneData> chunk = new Chunk<>(Arrays.asList(invalid, data("project-2")));

			writer.write(chunk);

			List<EpicHygieneData> saved = captureSaved();
			assertEquals(1, saved.size());
			assertEquals("project-2", saved.get(0).getBasicProjectConfigId());
		}

		@Test
		@DisplayName("Should skip items whose project id is blank")
		void write_ItemWithBlankProjectId_SkipsIt() {
			EpicHygieneData invalid = data("project-1");
			invalid.setBasicProjectConfigId("   ");
			Chunk<EpicHygieneData> chunk = new Chunk<>(Collections.singletonList(invalid));

			writer.write(chunk);

			verify(epicHygieneDataRepository, never()).saveAll(anyList());
			verify(processorExecutionTraceLogService, never())
					.upsertTraceLog(anyString(), anyString(), anyBoolean(), anyString());
		}

		@Test
		@DisplayName("Should do nothing when every item of the chunk is invalid")
		void write_AllItemsInvalid_NoSave() {
			EpicHygieneData invalid = data("project-1");
			invalid.setBasicProjectConfigId(null);
			Chunk<EpicHygieneData> chunk = new Chunk<>(Arrays.asList(invalid, null));

			writer.write(chunk);

			verify(epicHygieneDataRepository, never()).saveAll(anyList());
			verify(epicHygieneDataRepository, never()).deleteByBasicProjectConfigId(anyString());
		}
	}
}
