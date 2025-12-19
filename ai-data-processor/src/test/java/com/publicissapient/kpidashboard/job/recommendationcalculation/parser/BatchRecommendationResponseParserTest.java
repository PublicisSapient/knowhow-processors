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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
		void parseRecommendation_ValidCompleteJson_Success() throws Exception {
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
			Recommendation result = parser.parseRecommendation(response);

			// Assert
			assertNotNull(result);
			assertEquals("Improve Code Quality", result.getTitle());
			assertEquals("Code quality metrics show declining trend", result.getDescription());
			assertEquals(Severity.HIGH, result.getSeverity());
			assertEquals("2-3 sprints", result.getTimeToValue());
			assertNotNull(result.getActionPlans());
			assertEquals(2, result.getActionPlans().size());
			assertEquals("Implement Code Reviews", result.getActionPlans().get(0).getTitle());
		}

		@Test
		@DisplayName("Should parse JSON with markdown code fence")
		void parseRecommendation_WithMarkdownFence_Success() throws Exception {
			// Arrange
			String jsonResponse = """
					```json
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "MEDIUM",
						"timeToValue": "1-2 weeks",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Action Description"
							}
						]
					}
					```
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Recommendation result = parser.parseRecommendation(response);

			// Assert
			assertNotNull(result);
			assertEquals("Test Title", result.getTitle());
			assertEquals("Test Description", result.getDescription());
			assertEquals(Severity.MEDIUM, result.getSeverity());
			assertEquals("1-2 weeks", result.getTimeToValue());
			assertNotNull(result.getActionPlans());
			assertEquals(1, result.getActionPlans().size());
		}

		@Test
		@DisplayName("Should parse JSON without code fence")
		void parseRecommendation_WithoutMarkdownFence_Success() throws Exception {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "LOW",
						"timeToValue": "1 week",
						"actionPlans": [
							{
								"title": "Simple Action",
								"description": "Simple Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Recommendation result = parser.parseRecommendation(response);

			// Assert
			assertNotNull(result);
			assertEquals("Test Title", result.getTitle());
			assertEquals("Test Description", result.getDescription());
			assertEquals(Severity.LOW, result.getSeverity());
			assertEquals("1 week", result.getTimeToValue());
			assertNotNull(result.getActionPlans());
			assertEquals(1, result.getActionPlans().size());
		}

		@Test
		@DisplayName("Should parse JSON from recommendations array")
		void parseRecommendation_FromRecommendationsArray_Success() throws Exception {
			// Arrange
			String jsonResponse = """
					{
						"recommendations": [
							{
								"title": "First Recommendation",
								"description": "First Description",
								"severity": "HIGH",
								"timeToValue": "2-3 months",
								"actionPlans": [
									{
										"title": "Action 1",
										"description": "Description 1"
									}
								]
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Recommendation result = parser.parseRecommendation(response);

			// Assert
			assertNotNull(result);
			assertEquals("First Recommendation", result.getTitle());
			assertEquals("First Description", result.getDescription());
			assertEquals(Severity.HIGH, result.getSeverity());
			assertEquals("2-3 months", result.getTimeToValue());
			assertNotNull(result.getActionPlans());
			assertEquals(1, result.getActionPlans().size());
		}

		@Test
		@DisplayName("Should parse JSON with text before opening brace")
		void parseRecommendation_WithPrefixText_Success() throws Exception {
			// Arrange
			String jsonResponse = """
					Here is the recommendation:
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "MEDIUM",
						"timeToValue": "2 weeks",
						"actionPlans": [
							{
								"title": "Prefix Action",
								"description": "Prefix Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Recommendation result = parser.parseRecommendation(response);

			// Assert
			assertNotNull(result);
			assertEquals("Test Title", result.getTitle());
		}
	}

	@Nested
	@DisplayName("Invalid Response Handling")
	class InvalidResponseHandling {

		@Test
		@DisplayName("Should throw exception for null response")
		void parseRecommendation_NullResponse_ThrowsException() {
			// Act & Assert
			assertThrows(IllegalArgumentException.class, () -> parser.parseRecommendation(null));
		}

		@Test
		@DisplayName("Should throw exception for null response content")
		void parseRecommendation_NullContent_ThrowsException() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(null);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception for empty string response")
		void parseRecommendation_EmptyString_ThrowsException() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO("");

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception for whitespace-only response")
		void parseRecommendation_WhitespaceOnly_ThrowsException() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO("   \n\t  ");

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception for empty JSON object")
		void parseRecommendation_EmptyJsonObject_ThrowsException() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO("{}");

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception for JSON missing required fields")
		void parseRecommendation_MissingRequiredFields_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Only Title"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception for malformed JSON")
		void parseRecommendation_MalformedJson_ThrowsException() {
			// Arrange
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO("{ invalid json");

			// Act & Assert
			assertThrows(Exception.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception for empty recommendations array")
		void parseRecommendation_EmptyRecommendationsArray_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"recommendations": []
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception when title is empty string")
		void parseRecommendation_EmptyTitle_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "",
						"description": "Test Description",
						"severity": "HIGH",
						"timeToValue": "1 month",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception when description is empty string")
		void parseRecommendation_EmptyDescription_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "",
						"severity": "HIGH",
						"timeToValue": "1 month",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}
	}

	@Nested
	@DisplayName("Action Plans Parsing")
	class ActionPlansParsing {

		@Test
		@DisplayName("Should parse multiple action plans")
		void parseRecommendation_MultipleActionPlans_Success() throws Exception {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "HIGH",
						"timeToValue": "3 months",
						"actionPlans": [
							{"title": "Action 1", "description": "Description 1"},
							{"title": "Action 2", "description": "Description 2"},
							{"title": "Action 3", "description": "Description 3"}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Recommendation result = parser.parseRecommendation(response);

			// Assert
			assertNotNull(result);
			assertEquals(3, result.getActionPlans().size());
			assertEquals("Action 1", result.getActionPlans().get(0).getTitle());
			assertEquals("Description 1", result.getActionPlans().get(0).getDescription());
			assertEquals("Action 3", result.getActionPlans().get(2).getTitle());
		}

		@Test
		@DisplayName("Should throw exception when actionPlans field is missing")
		void parseRecommendation_NoActionPlans_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "HIGH",
						"timeToValue": "1 month"
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception when actionPlans array is empty")
		void parseRecommendation_EmptyActionPlansArray_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "HIGH",
						"timeToValue": "1 month",
						"actionPlans": []
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}
	}

	@Nested
	@DisplayName("Severity Parsing")
	class SeverityParsing {

		@Test
		@DisplayName("Should parse all valid severity levels")
		void parseRecommendation_AllSeverityLevels_Success() throws Exception {
			// Test all severity levels
			String[] severities = { "HIGH", "MEDIUM", "LOW" };

			for (String severity : severities) {
				String jsonResponse = String.format("""
						{
							"title": "Test",
							"description": "Test",
							"severity": "%s",
							"timeToValue": "1 month",
							"actionPlans": [
								{
									"title": "Test Action",
									"description": "Test Description"
								}
							]
						}
						""", severity);

				ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);
				Recommendation result = parser.parseRecommendation(response);

				assertNotNull(result);
				assertEquals(Severity.valueOf(severity), result.getSeverity());
			}
		}

		@Test
		@DisplayName("Should handle lowercase severity")
		void parseRecommendation_LowercaseSeverity_ParsesCorrectly() throws Exception {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test",
						"description": "Test",
						"severity": "high",
						"timeToValue": "1 month",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act
			Recommendation result = parser.parseRecommendation(response);

			// Assert
			assertNotNull(result);
			assertEquals(Severity.HIGH, result.getSeverity());
		}

		@Test
		@DisplayName("Should throw exception when severity is missing")
		void parseRecommendation_MissingSeverity_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"timeToValue": "1 month",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception for invalid severity")
		void parseRecommendation_InvalidSeverity_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "INVALID_SEVERITY",
						"timeToValue": "1 month",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}
	}

	@Nested
	@DisplayName("TimeToValue Validation")
	class TimeToValueValidation {

		@Test
		@DisplayName("Should throw exception when timeToValue is missing")
		void parseRecommendation_MissingTimeToValue_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "HIGH",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception when timeToValue is empty")
		void parseRecommendation_EmptyTimeToValue_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "HIGH",
						"timeToValue": "",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}

		@Test
		@DisplayName("Should throw exception when timeToValue is blank")
		void parseRecommendation_BlankTimeToValue_ThrowsException() {
			// Arrange
			String jsonResponse = """
					{
						"title": "Test Title",
						"description": "Test Description",
						"severity": "HIGH",
						"timeToValue": "   ",
						"actionPlans": [
							{
								"title": "Test Action",
								"description": "Test Description"
							}
						]
					}
					""";
			ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(jsonResponse);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> parser.parseRecommendation(response));
		}
	}
}
