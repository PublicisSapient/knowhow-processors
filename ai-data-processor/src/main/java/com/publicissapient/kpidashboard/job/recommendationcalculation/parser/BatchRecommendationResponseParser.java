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

	private static final String MARKDOWN_CODE_FENCE = "```";
	private static final char JSON_START_CHAR = '{';
	private static final String EMPTY_JSON_OBJECT = "{}";
	private static final int MAX_RESPONSE_SIZE = 100_000; // 100KB max response size

	private final ObjectMapper objectMapper;

	/**
	 * Parses AI response into a Recommendation object. Validates response content,
	 * size, and structure.
	 * 
	 * @param response
	 *            ChatGenerationResponseDTO from AI Gateway (must not be null)
	 * @return Optional containing parsed Recommendation, or empty if parsing fails
	 */
	public Optional<Recommendation> parseRecommendation(@NonNull ChatGenerationResponseDTO response) {
		// Validate response content is not null or empty
		String aiResponse = response.content();
		if (aiResponse == null || aiResponse.trim().isEmpty()) {
			log.error("AI Gateway returned null or empty response content");
			return Optional.empty();
		}

		// Validate response size
		if (aiResponse.length() > MAX_RESPONSE_SIZE) {
			log.error("AI response exceeds maximum size limit: {} bytes (max: {})", aiResponse.length(),
					MAX_RESPONSE_SIZE);
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
			log.error("AI response is empty, cannot parse recommendation");
			return Optional.empty();
		}

		try {
			String jsonContent = extractJsonContent(aiResponse);

			if (StringUtils.isBlank(jsonContent) || EMPTY_JSON_OBJECT.equals(jsonContent)) {
				log.error("Extracted JSON content is empty or invalid from AI response");
				return Optional.empty();
			}

			JsonNode rootNode = objectMapper.readTree(jsonContent);

			// Check for direct recommendation object with required non-empty fields
			if (hasValidTextField(rootNode, "title") && hasValidTextField(rootNode, "description")) {
				return Optional.of(parseRecommendationNode(rootNode));
			}

			// Check for recommendations array
			return Optional.ofNullable(rootNode.get("recommendations")).filter(JsonNode::isArray)
					.filter(node -> !node.isEmpty()).map(node -> parseRecommendationNode(node.get(0)));

		} catch (Exception e) {
			String preview = StringUtils.abbreviate(aiResponse, 100);
			log.error("Error parsing AI response JSON: {} - Response preview: {}", e.getMessage(), preview, e);
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
	 * Parses a JSON node into a Recommendation object. Extracts title, description,
	 * severity, timeToValue, and action plans.
	 * 
	 * @param node
	 *            the JSON node containing recommendation data
	 * @return parsed Recommendation object with default values for missing fields
	 */
	private Recommendation parseRecommendationNode(JsonNode node) {
		Recommendation rec = new Recommendation();

		// Required fields
		rec.setTitle(getTextValue(node, "title"));
		rec.setDescription(getTextValue(node, "description"));

		// Parse severity from AI response - no default here
		Optional.ofNullable(getTextValue(node, "severity")).map(String::toUpperCase).flatMap(this::parseSeverity)
				.ifPresent(rec::setSeverity);

		// Optional fields
		rec.setTimeToValue(getTextValue(node, "timeToValue"));

		// Parse action plans using stream
		Optional.ofNullable(node.get("actionPlans")).filter(JsonNode::isArray).map(this::parseActionPlans)
				.ifPresent(rec::setActionPlans);

		return rec;
	}

	/**
	 * Safely parses severity enum value.
	 */
	private Optional<Severity> parseSeverity(String severityStr) {
		try {
			return Optional.of(Severity.valueOf(severityStr));
		} catch (IllegalArgumentException e) {
			log.debug("Invalid severity value from AI response: {}. Will be set by validator.", severityStr);
			return Optional.empty();
		}
	}

	/**
	 * Parses action plans from JSON array node.
	 */
	private List<ActionPlan> parseActionPlans(JsonNode actionPlansNode) {
		List<ActionPlan> actionPlans = new ArrayList<>();
		actionPlansNode.forEach(actionNode -> {
			ActionPlan action = new ActionPlan();
			action.setTitle(getTextValue(actionNode, "title"));
			action.setDescription(getTextValue(actionNode, "description"));
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
