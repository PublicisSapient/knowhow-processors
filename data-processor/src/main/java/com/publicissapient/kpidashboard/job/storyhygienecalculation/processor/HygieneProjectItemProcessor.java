package com.publicissapient.kpidashboard.job.storyhygienecalculation.processor;

import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.batch.item.ItemProcessor;

import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.StoryHygieneSprintResult;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.service.StoryHygieneCalculationService;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Batch {@link ItemProcessor} for story hygiene pre-compute. Converts one {@link
 * FieldMapping} into the list of {@link StoryHygieneSprintResult} objects that must be upserted
 * (fresh cache hits are excluded by the service).
 */
@Slf4j
@RequiredArgsConstructor
public class HygieneProjectItemProcessor
		implements ItemProcessor<FieldMapping, List<StoryHygieneSprintResult>> {

	private final StoryHygieneCalculationService calculationService;
	private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Override
	public List<StoryHygieneSprintResult> process(@Nonnull FieldMapping fieldMapping) {
		String projectId = String.valueOf(fieldMapping.getBasicProjectConfigId());
		try {
			log.info("{} Processing project {}", JobConstants.LOG_PREFIX_STORY_HYGIENE, projectId);
			return calculationService.computeForProject(fieldMapping);
		} catch (Exception ex) {
			log.error(
					"{} Failed to process project {}: {}",
					JobConstants.LOG_PREFIX_STORY_HYGIENE,
					projectId,
					ex.getMessage(),
					ex);
			processorExecutionTraceLogService.upsertTraceLog(
					JobConstants.JOB_STORY_HYGIENE_CALCULATION,
					projectId,
					false,
					String.format(
							"Processing failed for project %s: %s — %s. Root cause: %s",
							projectId,
							ex.getClass().getSimpleName(),
							ex.getMessage(),
							ExceptionUtils.getRootCauseMessage(ex)));
			return null;
		}
	}
}
