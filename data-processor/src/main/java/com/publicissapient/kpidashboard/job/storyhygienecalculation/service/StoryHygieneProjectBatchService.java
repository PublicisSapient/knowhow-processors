package com.publicissapient.kpidashboard.job.storyhygienecalculation.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.job.constant.JobConstants;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Supplies eligible {@link FieldMapping} records one at a time for the story-hygiene batch job.
 * Eligible means the project has the Story Hygiene rule-set field configured.
 */
@Slf4j
@Component
@JobScope
@RequiredArgsConstructor
public class StoryHygieneProjectBatchService {

	private final FieldMappingRepository fieldMappingRepository;

	private List<FieldMapping> eligibleMappings = Collections.emptyList();
	private int currentIndex = 0;

	@PostConstruct
	private void loadEligibleMappings() {
		initializeBatchProcessingParametersForTheNextProcess();
	}

	/** Resets batch state — called after the job completes so the next run starts fresh. */
	public void initializeBatchProcessingParametersForTheNextProcess() {
		eligibleMappings =
				new ArrayList<>(fieldMappingRepository.findAllWithHygieneRuleSetConfigured());
		currentIndex = 0;
		log.info(
				"{} Loaded {} eligible projects for story hygiene pre-compute",
				JobConstants.LOG_PREFIX_STORY_HYGIENE,
				eligibleMappings.size());
	}

	/** Returns the next {@link FieldMapping} or {@code null} when all items have been read. */
	public FieldMapping getNextFieldMapping() {
		if (currentIndex >= eligibleMappings.size()) {
			return null;
		}
		return eligibleMappings.get(currentIndex++);
	}
}
