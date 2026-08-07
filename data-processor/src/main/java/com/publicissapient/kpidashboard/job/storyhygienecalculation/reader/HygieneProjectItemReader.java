package com.publicissapient.kpidashboard.job.storyhygienecalculation.reader;

import org.springframework.batch.item.ItemReader;

import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.service.StoryHygieneProjectBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Batch ItemReader that returns one {@link FieldMapping} at a time for KPI311 evaluation.
 */
@Slf4j
@RequiredArgsConstructor
public class HygieneProjectItemReader implements ItemReader<FieldMapping> {

	private final StoryHygieneProjectBatchService batchService;

	@Override
	public FieldMapping read() {
		FieldMapping fm = batchService.getNextFieldMapping();
		if (fm != null) {
			log.info(
					"{} Reading project {}",
					JobConstants.LOG_PREFIX_STORY_HYGIENE,
					fm.getBasicProjectConfigId());
		}
		return fm;
	}
}
