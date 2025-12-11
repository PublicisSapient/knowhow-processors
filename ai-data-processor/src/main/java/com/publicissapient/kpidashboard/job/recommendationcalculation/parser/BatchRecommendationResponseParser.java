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
import org.springframework.lang.NonNull;
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
	 *            ChatGenerationResponseDTO from AI Gateway (must not be null)
	 * @return Optional containing parsed Recommendation, or empty if parsing fails
	 */
	public Optional<Recommendation> parseRecommendation(@NonNull ChatGenerationResponseDTO response) {
		// Validate response content is not null or empty
		String aiResponse = response.content();
		if (aiResponse == null || aiResponse.trim().isEmpty()) {
			log.error("{} AI Gateway returned null or empty response content",
					JobConstants.LOG_PREFIX_RECOMMENDATION);
			return Optional.empty();
		}

		return parseRecommendationContent(aiResponse);
	}

	/**
	 * Parses AI response content into a Recommendation object.
	 * 
	 * @param aiResponse
	 *            JSON string from AI Gateway
	 * @return Optional containing parsed Recommendation, or empty if parsing fails
	 */
	private Optional<Recommendation> parseRecommendationContent(String aiResponse) {
		if (StringUtils.isBlank(aiResponse)) {
			log.error("{} AI response is empty, cannot parse recommendation",
					JobConstants.LOG_PREFIX_RECOMMENDATION);
			return Optional.empty();
		}

		try {
			String jsonContent = extractJsonContent(aiResponse);

			if (StringUtils.isBlank(jsonContent) || EMPTY_JSON_OBJECT.equals(jsonContent)) {
				log.error("{} Extracted JSON content is empty or invalid from AI response",
						JobConstants.LOG_PREFIX_RECOMMENDATION);
				return Optional.empty();
			}
			JsonNode rootNode = objectMapper.readTree(jsonContent);

			// Check for direct recommendation object with required non-empty fields
			if (hasValidTextField(rootNode, TITLE) && hasValidTextField(rootNode, DESCRIPTION)) {
				return Optional.of(parseRecommendationNode(rootNode));
			}

			// Check for recommendations array
			return Optional.ofNullable(rootNode.get(RECOMMENDATIONS)).filter(JsonNode::isArray)
					.filter(node -> !node.isEmpty()).map(node -> parseRecommendationNode(node.get(0)));

		} catch (Exception e) {
			String preview = StringUtils.abbreviate(aiResponse, 100);
			log.error("{} Error parsing AI response JSON: {} - Response preview: {}",
					JobConstants.LOG_PREFIX_RECOMMENDATION, e.getMessage(), preview, e);
			return Optional.empty();
		}
	}

	/**
	 * Extracts JSON content from AI response by removing markdown code blocks.
	 * Handles responses wrapped in ```json``` markdown blocks.
	 * 
	 * @param aiResponse
	 *            the raw AI response string
	 * @return extracted JSON content, or empty JSON object if extraction fails
	 */
	private String extractJsonContent(String aiResponse) {
		String content = StringUtils.defaultIfBlank(aiResponse, EMPTY_JSON_OBJECT).trim();

		// Remove markdown code blocks if present
		if (content.startsWith(MARKDOWN_CODE_FENCE)) {
			content = StringUtils.substringBetween(content, "\n", MARKDOWN_CODE_FENCE);
			if (content == null) {
				return EMPTY_JSON_OBJECT;
			}
		}

		// Find and extract JSON object starting from first {
		int jsonStart = content.indexOf(JSON_START_CHAR);
		return jsonStart >= 0 ? content.substring(jsonStart) : content;
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
			log.warn("{} Invalid severity value from AI response: {}. Saving as null.",
					JobConstants.LOG_PREFIX_RECOMMENDATION, severityStr);
			return Optional.empty();
		}
	}

	/**
	 * Parses action plans from JSON array node.
	 */
	private List<ActionPlan> parseActionPlans(JsonNode actionPlansNode) {
		List<ActionPlan> actionPlans = new ArrayList<>();
		actionPlansNode.forEach(actionNode -> {
			ActionPlan action = ActionPlan.builder().title(getTextValue(actionNode, TITLE))
					.description(getTextValue(actionNode, DESCRIPTION)).build();
			actionPlans.add(action);
		});
		return actionPlans;
	}

	/**
	 * Checks if JSON node has a valid non-empty text field.
	 * 
	 * @param node
	 *            the JSON node to check
	 * @param fieldName
	 *            the field name to check
	 * @return true if field exists and has non-blank text
	 */
	private boolean hasValidTextField(JsonNode node, String fieldName) {
		return Optional.ofNullable(node.get(fieldName)).map(JsonNode::asText).filter(StringUtils::isNotBlank)
				.isPresent();
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
