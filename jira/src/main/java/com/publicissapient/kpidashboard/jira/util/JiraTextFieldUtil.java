/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.jira.util;

import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Flattens the value of a Jira rich text / option custom field into readable plain text.
 *
 * <p>The processor talks to {@code /rest/api/latest}, so the very same custom field comes back in
 * three different shapes depending on the Jira flavour:
 *
 * <ul>
 *   <li><b>Jira Cloud (API v3)</b> - Atlassian Document Format, i.e. a JSON document tree such as
 *       {@code {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Given
 *       ..."}]}]}}
 *   <li><b>Jira Server / Data Center (API v2)</b> - a plain string holding wiki markup
 *   <li><b>select / multi select fields</b> - {@code {"value":"..."}} or an array of those
 * </ul>
 *
 * <p>Storing the raw JSON would make the value useless for the UI, for Excel exports and for the
 * LLM driven hygiene KPIs, so everything is reduced to text here.
 */
@Slf4j
public final class JiraTextFieldUtil {

	/** ADF node types that start a new line once their content is written. */
	private static final Set<String> BLOCK_TYPES =
			Set.of(
					"paragraph",
					"heading",
					"blockquote",
					"listItem",
					"bulletList",
					"orderedList",
					"codeBlock",
					"panel",
					"rule",
					"taskItem",
					"taskList",
					"decisionItem",
					"decisionList",
					"mediaSingle",
					"mediaGroup",
					"tableRow",
					"expand",
					"nestedExpand");

	private static final String TYPE = "type";
	private static final String TEXT = "text";
	private static final String CONTENT = "content";
	private static final String ATTRS = "attrs";
	private static final String VALUE = "value";
	private static final String HARD_BREAK = "hardBreak";
	private static final String NULL_LITERAL = "null";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** Possessive quantifier - no backtracking while stripping end of line blanks. */
	private static final Pattern TRAILING_BLANKS = Pattern.compile("[ \\t]++\\n");

	private JiraTextFieldUtil() {}

	/**
	 * Converts the raw value of an {@code IssueField} into plain text.
	 *
	 * @param fieldValue raw value as returned by {@code IssueField#getValue()} - an ADF {@code
	 *     JSONObject}, a {@code JSONArray} of options, a wiki markup {@code String} or any other
	 *     scalar
	 * @return the readable text, or {@code null} when the field carries no value
	 */
	public static String toPlainText(Object fieldValue) {
		if (fieldValue == null) {
			return null;
		}

		String raw = fieldValue.toString().trim();
		if (raw.isEmpty()
				|| NULL_LITERAL.equalsIgnoreCase(raw)
				|| "[]".equals(raw)
				|| "{}".equals(raw)) {
			return null;
		}

		// Both org.codehaus.jettison and org.json.simple render valid JSON from
		// toString(), so a single Jackson parse covers every structured shape.
		if (raw.charAt(0) == '{' || raw.charAt(0) == '[') {
			try {
				String flattened = normalise(flatten(OBJECT_MAPPER.readTree(raw)));
				return StringUtils.isBlank(flattened) ? null : flattened;
			} catch (JsonProcessingException e) {
				log.debug(
						"JIRA Processor | Value is not parsable JSON, keeping it as text. Reason : {}",
						e.getMessage());
			}
		}

		return raw;
	}

	private static String flatten(JsonNode node) {
		StringBuilder builder = new StringBuilder();
		// Siblings of the outermost array are independent values - typically the
		// options of a multi select field - so they are comma separated.
		append(node, builder, true);
		return builder.toString();
	}

	/**
	 * @param joinSiblings {@code true} to comma separate the entries of an array, {@code false} for
	 *     the inline {@code content} of an ADF node, whose parts form one continuous sentence
	 */
	private static void append(JsonNode node, StringBuilder builder, boolean joinSiblings) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isValueNode()) {
			builder.append(node.asText());
			return;
		}
		if (node.isArray()) {
			appendArray(node, builder, joinSiblings);
			return;
		}
		appendObject(node, builder);
	}

	private static void appendArray(JsonNode array, StringBuilder builder, boolean joinSiblings) {
		for (JsonNode child : array) {
			int lengthBefore = builder.length();
			append(child, builder, joinSiblings);
			// Never glue two ADF blocks together - those already brought their own line
			// break - and never separate the inline parts of a single sentence.
			if (joinSiblings && builder.length() > lengthBefore && !endsWithBreak(builder)) {
				builder.append(", ");
			}
		}
		if (joinSiblings) {
			stripTrailingSeparator(builder);
		}
	}

	private static void appendObject(JsonNode object, StringBuilder builder) {
		String type = object.path(TYPE).asText(StringUtils.EMPTY);

		if (HARD_BREAK.equals(type)) {
			builder.append('\n');
			return;
		}

		// Leaf text node of an ADF document.
		if (object.hasNonNull(TEXT)) {
			builder.append(object.get(TEXT).asText());
			return;
		}

		if (object.has(CONTENT)) {
			append(object.get(CONTENT), builder, false);
			if (BLOCK_TYPES.contains(type) && !endsWithBreak(builder)) {
				builder.append('\n');
			}
			return;
		}

		// Option like values ({"value":"Yes"}), users and inline cards / mentions.
		String scalar = firstNonBlank(object, VALUE, "name", "displayName", "key");
		if (scalar != null) {
			builder.append(scalar);
			return;
		}

		if (object.has(ATTRS)) {
			String attribute = firstNonBlank(object.get(ATTRS), TEXT, "url", "shortName", "id");
			if (attribute != null) {
				builder.append(attribute);
			}
		}
	}

	private static String firstNonBlank(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode candidate = node.path(fieldName);
			if (candidate.isValueNode() && StringUtils.isNotBlank(candidate.asText())) {
				return candidate.asText();
			}
		}
		return null;
	}

	private static boolean endsWithBreak(StringBuilder builder) {
		return builder.isEmpty() || builder.charAt(builder.length() - 1) == '\n';
	}

	private static void stripTrailingSeparator(StringBuilder builder) {
		if (builder.length() >= 2
				&& ", ".contentEquals(builder.subSequence(builder.length() - 2, builder.length()))) {
			builder.setLength(builder.length() - 2);
		}
	}

	/** Trims trailing blanks on every line and collapses runs of blank lines into a single one. */
	private static String normalise(String text) {
		return TRAILING_BLANKS.matcher(text).replaceAll("\n").replaceAll("\\n{3,}", "\n\n").trim();
	}
}
