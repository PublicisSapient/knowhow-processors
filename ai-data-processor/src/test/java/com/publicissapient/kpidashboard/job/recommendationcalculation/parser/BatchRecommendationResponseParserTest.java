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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Severity;

@DisplayName("BatchRecommendationResponseParser Tests")
class BatchRecommendationResponseParserTest {

	private BatchRecommendationResponseParser parser;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		parser = new BatchRecommendationResponseParser(objectMapper);
	}

	@Nested
	@DisplayName("Valid Response Parsing")
	class ValidResponseParsing {

		@Test
		@DisplayName("Should parse valid JSON response with all fields")
		void parseRecommendation_ValidCompleteJson_Success() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Improve Code Quality",
						"description": "Code quality metrics show declining trend",
						"severity": "HIGH",
						"timeToValue": "2-3 sprints",
						"actionPlans": [
							{
								"title": "Implement Code Reviews",
								"description": "Enforce peer code reviews"
							},
							{
								"title": "Add Unit Tests",
								"description": "Increase test coverage to 80%"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			Recommendation recommendation = result.get();
			assertEquals("Improve Code Quality", recommendation.getTitle());
			assertEquals("Code quality metrics show declining trend", recommendation.getDescription());
			assertEquals(Severity.HIGH, recommendation.getSeverity());
			assertEquals("2-3 sprints", recommendation.getTimeToValue());
			assertNotNull(recommendation.getActionPlans());
			assertEquals(2, recommendation.getActionPlans().size());
			assertEquals("Implement Code Reviews", recommendation.getActionPlans().get(0).getTitle());
		}

		@Test
		@DisplayName("Should parse JSON with markdown code fence")
		void parseRecommendation_WithMarkdownFence_Success() {
			// Arrange
			String jsonResponse = """
					```json
					{
						"title": "Test Title",
						"description": "Test Description"
					}
					```
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertEquals("Test Title", result.get().getTitle());
			assertEquals("Test Description", result.get().getDescription());
		}

		@Test
		@DisplayName("Should parse JSON without code fence")
		void parseRecommendation_WithoutMarkdownFence_Success() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertEquals("Test Title", result.get().getTitle());
		}

		@Test
		@DisplayName("Should parse JSON from recommendations array")
		void parseRecommendation_FromRecommendationsArray_Success() {
			// Arrange
			String jsonResponse = """
					{
						"recommendations": [
							{
								"title": "First Recommendation",
								"description": "First Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertEquals("First Recommendation", result.get().getTitle());
			assertEquals("First Description", result.get().getDescription());
		}

		@Test
		@DisplayName("Should parse minimal JSON with only required fields")
		void parseRecommendation_MinimalJson_Success() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Minimal Title",
						"description": "Minimal Description"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertEquals("Minimal Title", result.get().getTitle());
			assertEquals("Minimal Description", result.get().getDescription());
			assertNull(result.get().getSeverity());
			assertNull(result.get().getTimeToValue());
			assertNull(result.get().getActionPlans());
		}

		@Test
		@DisplayName("Should handle invalid severity gracefully")
		void parseRecommendation_InvalidSeverity_SavesAsNull() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "INVALID_SEVERITY"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertNull(result.get().getSeverity());
		}

		@Test
		@DisplayName("Should parse JSON with text before opening brace")
		void parseRecommendation_WithPrefixText_Success() {
			// Arrange
			String jsonResponse = """
					Here is the recommendation:
					{
						"title": "Test Title",
						"description": "Test Description"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertEquals("Test Title", result.get().getTitle());
		}
	}

	@Nested
	@DisplayName("Invalid Response Handling")
	class InvalidResponseHandling {

		@Test
		@DisplayName("Should return empty for null response content")
		void parseRecommendation_NullContent_ReturnsEmpty() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(null);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("Should return empty for empty string response")
		void parseRecommendation_EmptyString_ReturnsEmpty() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO("");

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("Should return empty for whitespace-only response")
		void parseRecommendation_WhitespaceOnly_ReturnsEmpty() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO("   \n\t  ");

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("Should return empty for empty JSON object")
		void parseRecommendation_EmptyJsonObject_ReturnsEmpty() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO("{}");

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("Should return empty for JSON missing required fields")
		void parseRecommendation_MissingRequiredFields_ReturnsEmpty() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Only Title"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("Should return empty for malformed JSON")
		void parseRecommendation_MalformedJson_ReturnsEmpty() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO("{ invalid json");

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("Should return empty for empty recommendations array")
		void parseRecommendation_EmptyRecommendationsArray_ReturnsEmpty() {
			// Arrange
			String jsonResponse = """
					{
						"recommendations": []
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("Should return empty when title is empty string")
		void parseRecommendation_EmptyTitle_ReturnsEmpty() {
			// Arrange
			String jsonResponse = """
					{
						"title": "",
						"description": "Test Description"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("Should return empty when description is empty string")
		void parseRecommendation_EmptyDescription_ReturnsEmpty() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": ""
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertFalse(result.isPresent());
		}
	}

	@Nested
	@DisplayName("Action Plans Parsing")
	class ActionPlansParsing {

		@Test
		@DisplayName("Should parse multiple action plans")
		void parseRecommendation_MultipleActionPlans_Success() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"actionPlans": [
							{"title": "Action 1", "description": "Description 1"},
							{"title": "Action 2", "description": "Description 2"},
							{"title": "Action 3", "description": "Description 3"}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertEquals(3, result.get().getActionPlans().size());
			assertEquals("Action 1", result.get().getActionPlans().get(0).getTitle());
			assertEquals("Description 1", result.get().getActionPlans().get(0).getDescription());
			assertEquals("Action 3", result.get().getActionPlans().get(2).getTitle());
		}

		@Test
		@DisplayName("Should handle missing actionPlans field")
		void parseRecommendation_NoActionPlans_ReturnsNull() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertNull(result.get().getActionPlans());
		}

		@Test
		@DisplayName("Should handle empty actionPlans array")
		void parseRecommendation_EmptyActionPlansArray_ReturnsEmptyList() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"actionPlans": []
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertNotNull(result.get().getActionPlans());
			assertTrue(result.get().getActionPlans().isEmpty());
		}
	}

	@Nested
	@DisplayName("Severity Parsing")
	class SeverityParsing {

		@Test
		@DisplayName("Should parse all valid severity levels")
		void parseRecommendation_AllSeverityLevels_Success() {
			// Test all severity levels
			String[] severities = { "HIGH", "MEDIUM", "LOW" };

			for (String severity : severities) {
				String jsonResponse = String.format("""
						{
							"title": "Test",
							"description": "Test",
							"severity": "%s"
						}
						""", severity);

				ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);
				Optional<Recommendation> result = parser.parseRecommendation(response);

				assertTrue(result.isPresent());
				assertEquals(Severity.valueOf(severity), result.get().getSeverity());
			}
		}

		@Test
		@DisplayName("Should handle lowercase severity")
		void parseRecommendation_LowercaseSeverity_ParsesCorrectly() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test",
						"description": "Test",
						"severity": "high"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Optional<Recommendation> result = parser.parseRecommendation(response);

			// Assert
			assertTrue(result.isPresent());
			assertEquals(Severity.HIGH, result.get().getSeverity());
		}
	}
}
