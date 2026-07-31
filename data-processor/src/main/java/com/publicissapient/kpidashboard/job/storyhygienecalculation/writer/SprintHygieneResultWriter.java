package com.publicissapient.kpidashboard.job.storyhygienecalculation.writer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import com.publicissapient.kpidashboard.common.model.jira.StoryHygieneSprintResult;
import com.publicissapient.kpidashboard.common.repository.jira.StoryHygieneSprintResultRepository;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SprintHygieneResultWriter implements ItemWriter<List<StoryHygieneSprintResult>> {

	private final StoryHygieneSprintResultRepository hygieneResultRepository;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Override
	public void write(@NonNull Chunk<? extends List<StoryHygieneSprintResult>> chunk) {
		List<StoryHygieneSprintResult> toSave =
				chunk.getItems().stream()
						.filter(Objects::nonNull)
						.flatMap(List::stream)
						.filter(r -> r != null && r.getBasicProjectConfigId() != null)
						.collect(Collectors.toList());

		if (toSave.isEmpty()) {
			log.debug(
					"{} No hygiene results to persist in this chunk", JobConstants.LOG_PREFIX_STORY_HYGIENE);
			return;
		}

		log.info(
				"{} Persisting {} sprint hygiene result(s)",
				JobConstants.LOG_PREFIX_STORY_HYGIENE,
				toSave.size());

		hygieneResultRepository.saveAll(toSave);

		toSave.stream()
				.map(StoryHygieneSprintResult::getBasicProjectConfigId)
				.distinct()
				.forEach(
						projectId ->
								processorExecutionTraceLogService.upsertTraceLog(
										JobConstants.JOB_STORY_HYGIENE_CALCULATION, projectId, true, null));
	}
}
