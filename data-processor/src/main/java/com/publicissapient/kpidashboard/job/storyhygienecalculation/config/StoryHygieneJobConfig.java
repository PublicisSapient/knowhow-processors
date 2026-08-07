package com.publicissapient.kpidashboard.job.storyhygienecalculation.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import lombok.Data;

/** Inner calculation parameters for the story-hygiene batch job. */
@Data
public class StoryHygieneJobConfig implements ConfigValidator {

	private Set<String> configValidationErrors = new HashSet<>();

	/** Number of most-recent closed sprints to evaluate per project. */
	private int sprintCount;

	/** Maximum number of issues sampled per sprint when calling the LLM. */
	private int issueCountPerSprint;

	/** Fields always written to the issue node regardless of field mapping (anchor fields). */
	private List<String> anchorFields;

	@Override
	public void validateConfiguration() {
		if (sprintCount < 1) {
			configValidationErrors.add("calculationConfig.sprintCount must be >= 1");
		}
		if (issueCountPerSprint < 1) {
			configValidationErrors.add("calculationConfig.issueCountPerSprint must be >= 1");
		}
	}

	@Override
	public Set<String> getConfigValidationErrors() {
		return Collections.unmodifiableSet(configValidationErrors);
	}

	public List<String> getAnchorFields() {
		return anchorFields == null ? List.of() : anchorFields;
	}
}
