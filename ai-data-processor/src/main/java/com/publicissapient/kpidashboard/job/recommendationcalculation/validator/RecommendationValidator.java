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

package com.publicissapient.kpidashboard.job.recommendationcalculation.validator;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.ActionPlan;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Severity;
import com.publicissapient.kpidashboard.job.constant.AiDataProcessorConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.stream.IntStream;

/**
 * Extracts complex validation logic.
 */
@Slf4j
@Component
public class RecommendationValidator {
	
	private static final int MAX_TITLE_LENGTH = 500;
	private static final int MAX_DESCRIPTION_LENGTH = 5000;
	private static final int MAX_ACTION_PLAN_TITLE_LENGTH = 200;
	private static final int MAX_ACTION_PLAN_DESCRIPTION_LENGTH = 2000;
	
	/**
	 * Validates and sanitizes AI-generated recommendation.
	 * Ensures data quality and prevents silent failures from malformed AI responses.
	 * 
	 * @param recommendation the recommendation to validate (must not be null)
	 * @param projectId the project identifier for logging context
	 * @throws IllegalArgumentException if recommendation is null or invalid
	 */
	public void validateAndSanitize(Recommendation recommendation, String projectId) {
		Assert.notNull(recommendation, "Recommendation cannot be null");
		Assert.hasText(projectId, "Project ID cannot be empty");
		
		validateTitle(recommendation, projectId);
		validateDescription(recommendation, projectId);
		validateSeverity(recommendation, projectId);
		validateActionPlans(recommendation, projectId);
		
		log.debug("{} AI response validation successful for project: {}",
				AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectId);
	}
	
	/**
	 * Validates and truncates recommendation title.
	 */
	private void validateTitle(Recommendation recommendation, String projectId) {
		validateAndTruncateTextField(recommendation::getTitle, recommendation::setTitle, 
				"Recommendation title", MAX_TITLE_LENGTH, projectId);
	}
	
	/**
	 * Validates and truncates recommendation description.
	 */
	private void validateDescription(Recommendation recommendation, String projectId) {
		validateAndTruncateTextField(recommendation::getDescription, recommendation::setDescription, 
				"Recommendation description", MAX_DESCRIPTION_LENGTH, projectId);
	}
	
	/**
	 * Generic method to validate and truncate text fields.
	 * Ensures text is not empty and truncates if exceeds max length.
	 * 
	 * @param getter function to get the text value
	 * @param setter function to set the text value
	 * @param fieldName display name for logging
	 * @param maxLength maximum allowed length
	 * @param projectId project identifier for logging context
	 */
	private void validateAndTruncateTextField(java.util.function.Supplier<String> getter, 
			java.util.function.Consumer<String> setter, String fieldName, int maxLength, String projectId) {
		String value = getter.get();
		Assert.hasText(value, fieldName + " cannot be empty");
		
		if (value.length() > maxLength) {
			log.warn("{} {} exceeds max length ({}) for project: {}. Truncating.",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, fieldName, maxLength, projectId);
			setter.accept(StringUtils.left(value, maxLength));
		}
	}
	
	/**
	 * Validates and sets default severity if missing.
	 */
	private void validateSeverity(Recommendation recommendation, String projectId) {
		if (recommendation.getSeverity() == null) {
			log.warn("{} Recommendation missing severity for project: {}. Setting default to MEDIUM.",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectId);
			recommendation.setSeverity(Severity.MEDIUM);
		}
	}
	
	/**
	 * Validates and sanitizes action plans.
	 */
	private void validateActionPlans(Recommendation recommendation, String projectId) {
		if (CollectionUtils.isEmpty(recommendation.getActionPlans())) {
			log.debug("{} No action plans provided in recommendation for project: {}",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, projectId);
			return;
		}
		
		IntStream.range(0, recommendation.getActionPlans().size())
				.forEach(i -> validateActionPlan(
						recommendation.getActionPlans().get(i), 
						i + 1, 
						projectId));
	}
	
	/**
	 * Validates and sanitizes individual action plan.
	 */
	private void validateActionPlan(ActionPlan actionPlan, int index, String projectId) {
		// Validate title
		if (StringUtils.isBlank(actionPlan.getTitle())) {
			log.warn("{} Action plan #{} missing title for project: {}",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, index, projectId);
		} else {
			validateAndTruncateTextField(actionPlan::getTitle, actionPlan::setTitle, 
					"Action plan #" + index + " title", MAX_ACTION_PLAN_TITLE_LENGTH, projectId);
		}
		
		// Validate description
		if (StringUtils.isBlank(actionPlan.getDescription())) {
			log.warn("{} Action plan #{} missing description for project: {}",
					AiDataProcessorConstants.LOG_PREFIX_RECOMMENDATION, index, projectId);
		} else {
			validateAndTruncateTextField(actionPlan::getDescription, actionPlan::setDescription, 
					"Action plan #" + index + " description", MAX_ACTION_PLAN_DESCRIPTION_LENGTH, projectId);
		}
	}
}
