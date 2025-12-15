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

package com.publicissapient.kpidashboard.job.recommendationcalculation.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.ActionPlan;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Severity;
import com.publicissapient.kpidashboard.job.constant.JobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for batch processor AI Gateway responses. Converts AI-generated JSON
 * responses into structured Recommendation objects.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchRecommendationResponseParser {

	public static final String TITLE = "title";
	public static final String DESCRIPTION = "description";
	public static final String RECOMMENDATIONS = "recommendations";
	public static final String SEVERITY = "severity";
	public static final String ACTION_PLANS = "actionPlans";
	public static final String TIME_TO_VALUE = "timeToValue";
	private static final String MARKDOWN_CODE_FENCE = "```";
	private static final char JSON_START_CHAR = '{';
	private static final String EMPTY_JSON_OBJECT = "{}";
	private final ObjectMapper objectMapper;

	/**
	 * Parses AI response into a Recommendation object. Validates response content
	 * and structure.
	 * 
	 * @param response
	 *            ChatGenerationResponseDTO from AI Gateway
	 * @return parsed and validated Recommendation object
	 * @throws IllegalArgumentException
	 *             if response is null
	 * @throws IllegalStateException
	 *             if response content is empty, JSON extraction fails, or required
	 *             fields missing
	 * @throws Exception
	 *             if JSON parsing fails or other processing errors occur
	 */
	public Recommendation parseRecommendation(ChatGenerationResponseDTO response) throws Exception {
		if (response == null) {
			throw new IllegalArgumentException("AI Gateway response cannot be null");
		}

		// Validate response content is not null or empty
		String aiResponse = response.content();
		if (aiResponse == null || aiResponse.trim().isEmpty()) {
			throw new IllegalStateException("AI Gateway returned empty response content");
		}

		return parseRecommendationContent(aiResponse);
	}

	/**
	 * Parses AI response content into a Recommendation object.
	 * 
	 * @param aiResponse
	 *            JSON string from AI Gateway
	 * @return parsed and validated Recommendation
	 * @throws IllegalStateException
	 *             if content is empty, JSON extraction fails, or required fields
	 *             missing
	 * @throws Exception
	 *             if JSON parsing fails
	 */
	private Recommendation parseRecommendationContent(String aiResponse) throws Exception {
		String jsonContent = extractJsonContent(aiResponse);

		if (StringUtils.isBlank(jsonContent) || EMPTY_JSON_OBJECT.equals(jsonContent)) {
			throw new IllegalStateException(
					"Failed to extract JSON from AI response - malformed markdown or missing JSON content");
		}

		JsonNode rootNode = objectMapper.readTree(jsonContent);

		// Check for direct recommendation object with required non-empty fields
		if (getTextValue(rootNode, TITLE) != null && getTextValue(rootNode, DESCRIPTION) != null) {
			return parseAndValidateRecommendation(rootNode);
		}

		// Check for recommendations array
		JsonNode recommendationsArray = rootNode.get(RECOMMENDATIONS);
		if (recommendationsArray != null && recommendationsArray.isArray() && !recommendationsArray.isEmpty()) {
			return parseAndValidateRecommendation(recommendationsArray.get(0));
		}

		// Missing required fields
		throw new IllegalStateException("AI response missing required fields: title and description must be non-empty");
	}

	/**
	 * Parses and validates a recommendation node to ensure data quality.
	 * 
	 * @param node
	 *            the JSON node to parse
	 * @return validated Recommendation object
	 * @throws IllegalStateException
	 *             if required fields are missing or invalid
	 */
	private Recommendation parseAndValidateRecommendation(JsonNode node) {
		Recommendation recommendation = parseRecommendationNode(node);

		// Validate required text fields
		if (StringUtils.isBlank(recommendation.getTitle())) {
			throw new IllegalStateException(
					"AI response missing required field 'title' - recommendation must have non-empty title");
		}

		if (StringUtils.isBlank(recommendation.getDescription())) {
			throw new IllegalStateException(
					"AI response missing required field 'description' - recommendation must have non-empty description");
		}

		// Validate critical fields
		if (recommendation.getSeverity() == null) {
			throw new IllegalStateException(
					"AI response missing required field 'severity' - cannot determine recommendation priority");
		}

		if (StringUtils.isBlank(recommendation.getTimeToValue())) {
			throw new IllegalStateException(
					"AI response missing required field 'timeToValue' - prompt requires timeline estimate based on severity and complexity");
		}

		if (recommendation.getActionPlans() == null || recommendation.getActionPlans().isEmpty()) {
			throw new IllegalStateException(
					"AI response missing required field 'actionPlans' - recommendation must include actionable steps");
		}

		return recommendation;
	}

	/**
	 * Extracts JSON content from AI response by removing markdown code blocks.
	 * Handles responses wrapped in ```json``` markdown blocks with fallback logic.
	 * 
	 * @param aiResponse
	 *            the raw AI response string
	 * @return extracted JSON content, or empty JSON object if extraction fails
	 */
	private String extractJsonContent(String aiResponse) {
		String content = StringUtils.defaultIfBlank(aiResponse, EMPTY_JSON_OBJECT).trim();

		// Remove markdown code blocks if present
		if (content.startsWith(MARKDOWN_CODE_FENCE)) {
			String extracted = StringUtils.substringBetween(content, "\n", MARKDOWN_CODE_FENCE);
			if (extracted == null) {
				log.warn("{} Malformed markdown fence - attempting fallback extraction",
						JobConstants.LOG_PREFIX_RECOMMENDATION);
				int firstNewline = content.indexOf('\n');
				if (firstNewline > 0 && firstNewline < content.length() - 1) {
					content = content.substring(firstNewline + 1).trim();
				} else {
					return EMPTY_JSON_OBJECT;
				}
			} else {
				content = extracted.trim();
			}
		}

		// Find and extract JSON object starting from first {
		int jsonStart = content.indexOf(JSON_START_CHAR);
		if (jsonStart < 0) {
			log.warn("{} No JSON object found in response", JobConstants.LOG_PREFIX_RECOMMENDATION);
			return EMPTY_JSON_OBJECT;
		}

		return content.substring(jsonStart);
	}

	/**
	 * Parses a JSON node into a Recommendation object. Extracts all fields directly
	 * from AI response.
	 * 
	 * @param node
	 *            the JSON node containing recommendation data
	 * @return parsed Recommendation object with values exactly as provided by AI
	 */
	private Recommendation parseRecommendationNode(JsonNode node) {
		// Parse severity directly from AI response
		Severity severity = Optional.ofNullable(getTextValue(node, SEVERITY)).map(String::toUpperCase)
				.flatMap(this::parseSeverity).orElse(null);

		// Parse action plans
		List<ActionPlan> actionPlans = Optional.ofNullable(node.get(ACTION_PLANS)).filter(JsonNode::isArray)
				.map(this::parseActionPlans).orElse(null);

		// Build recommendation using builder
		return Recommendation.builder().title(getTextValue(node, TITLE)).description(getTextValue(node, DESCRIPTION))
				.severity(severity).timeToValue(getTextValue(node, TIME_TO_VALUE)).actionPlans(actionPlans).build();
	}

	/**
	 * Safely parses severity enum value.
	 */
	private Optional<Severity> parseSeverity(String severityStr) {
		try {
			return Optional.of(Severity.valueOf(severityStr));
		} catch (IllegalArgumentException e) {
			log.debug("{} Invalid severity value '{}' in AI response - will be validated",
					JobConstants.LOG_PREFIX_RECOMMENDATION, severityStr);
			return Optional.empty();
		}
	}

	/**
	 * Parses action plans from JSON array node. Filters out action plans with empty
	 * or blank title/description to ensure data quality.
	 * 
	 * @param actionPlansNode
	 *            the JSON array node containing action plans
	 * @return list of valid action plans, or null if none are valid
	 */
	private List<ActionPlan> parseActionPlans(JsonNode actionPlansNode) {
		List<ActionPlan> actionPlans = new ArrayList<>();
		int skippedCount = 0;

		for (JsonNode actionNode : actionPlansNode) {
			String title = getTextValue(actionNode, TITLE);
			String description = getTextValue(actionNode, DESCRIPTION);

			// Only include action plans with both title and description
			if (StringUtils.isNotBlank(title) && StringUtils.isNotBlank(description)) {
				ActionPlan action = ActionPlan.builder().title(title).description(description).build();
				actionPlans.add(action);
			} else {
				skippedCount++;
			}
		}

		if (skippedCount > 0) {
			log.debug("{} Filtered out {} action plan(s) with empty content from {} total",
					JobConstants.LOG_PREFIX_RECOMMENDATION, skippedCount, actionPlansNode.size());
		}

		return actionPlans.isEmpty() ? null : actionPlans;
	}

	/**
	 * Safely extracts text value from JSON node. Returns null if field doesn't
	 * exist or is null.
	 * 
	 * @param node
	 *            the JSON node to extract from
	 * @param fieldName
	 *            the field name to extract
	 * @return extracted text value, or null if not present
	 */
	private String getTextValue(JsonNode node, String fieldName) {
		return Optional.ofNullable(node.get(fieldName)).map(JsonNode::asText).filter(StringUtils::isNotBlank)
				.orElse(null);
	}
}
