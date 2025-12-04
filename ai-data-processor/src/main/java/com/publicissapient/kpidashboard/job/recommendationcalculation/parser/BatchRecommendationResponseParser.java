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

import com.fasterxml.jackson.databind.JsonNode;
import com.publicissapient.kpidashboard.common.mapper.CustomObjectMapper;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.ActionPlan;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for batch processor AI Gateway responses.
 */
@Slf4j
@Component
public class BatchRecommendationResponseParser {
	
	private final CustomObjectMapper objectMapper = new CustomObjectMapper();
	
	/**
	 * Parses AI response JSON into a single Recommendation object.*
	 * @param aiResponse JSON string from AI Gateway
	 * @return Parsed Recommendation object (empty Recommendation on error)
	 */
	public Recommendation parseRecommendation(String aiResponse) {
		
		try {
			// Extract JSON from response
			String jsonContent = extractJsonContent(aiResponse);
			
			// Parse JSON response
			JsonNode rootNode = objectMapper.readTree(jsonContent);
			
			// Check for direct recommendation object
			if (rootNode.has("title") && rootNode.has("description")) {
				return parseRecommendationNode(rootNode);
			}
			
			// Check for recommendations array (backward compatibility)
			JsonNode recommendationsNode = rootNode.get("recommendations");
			if (recommendationsNode != null && recommendationsNode.isArray() && !recommendationsNode.isEmpty()) {
				return parseRecommendationNode(recommendationsNode.get(0));
			}
			
			log.warn("No recommendation found in AI response");
			return new Recommendation();
			
		} catch (Exception e) {
			log.error("Error parsing AI response JSON: {}", e.getMessage(), e);
			return new Recommendation();
		}
	}
	
	/**
	 * Extracts JSON content from AI response.
	 */
	private String extractJsonContent(String aiResponse) {
		if (aiResponse == null || aiResponse.isEmpty()) {
			return "{}";
		}
		
		// Remove markdown code blocks if present
		String content = aiResponse.trim();
		if (content.startsWith("```")) {
			content = content.substring(content.indexOf('\n') + 1);
			content = content.substring(0, content.lastIndexOf("```"));
		}
		
		// Find first { for JSON start
		int jsonStart = content.indexOf('{');
		if (jsonStart >= 0) {
			return content.substring(jsonStart);
		}
		
		return content;
	}
	
	/**
	 * Parses a single recommendation node.
	 */
	private Recommendation parseRecommendationNode(JsonNode node) {
		Recommendation rec = new Recommendation();
		
		// Required fields
		rec.setTitle(getTextValue(node, "title"));
		rec.setDescription(getTextValue(node, "description"));
		
		// Parse severity
		String severityStr = getTextValue(node, "severity");
		if (severityStr != null && !severityStr.isEmpty()) {
			try {
				rec.setSeverity(Severity.valueOf(severityStr.toUpperCase()));
			} catch (Exception e) {
				log.debug("Invalid severity value: {}, defaulting to MEDIUM", severityStr);
				rec.setSeverity(Severity.MEDIUM);
			}
		} else {
			rec.setSeverity(Severity.MEDIUM);
		}
		
		// Optional fields
		rec.setTimeToValue(getTextValue(node, "timeToValue"));
		
		// Parse action plans
		JsonNode actionPlansNode = node.get("actionPlans");
		if (actionPlansNode != null && actionPlansNode.isArray()) {
			List<ActionPlan> actionPlans = new ArrayList<>();
			for (JsonNode actionNode : actionPlansNode) {
				ActionPlan action = new ActionPlan();
				action.setTitle(getTextValue(actionNode, "title"));
				action.setDescription(getTextValue(actionNode, "description"));
				actionPlans.add(action);
			}
			rec.setActionPlans(actionPlans);
		}
		
		return rec;
	}
	
	/**
	 * Safely extracts text value from JSON node.
	 */
	private String getTextValue(JsonNode node, String fieldName) {
		JsonNode fieldNode = node.get(fieldName);
		return fieldNode != null ? fieldNode.asText() : null;
	}
}
