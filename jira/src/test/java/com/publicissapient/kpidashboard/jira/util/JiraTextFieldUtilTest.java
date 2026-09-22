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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

public class JiraTextFieldUtilTest {

	@Test
	public void nullValueGivesNull() {
		assertNull(JiraTextFieldUtil.toPlainText(null));
	}

	@Test
	public void emptyValuesGiveNull() {
		assertNull(JiraTextFieldUtil.toPlainText(""));
		assertNull(JiraTextFieldUtil.toPlainText("   "));
		assertNull(JiraTextFieldUtil.toPlainText("null"));
		assertNull(JiraTextFieldUtil.toPlainText("[]"));
		assertNull(JiraTextFieldUtil.toPlainText("{}"));
	}

	/** Jira Server / Data Center returns wiki markup, which is already readable. */
	@Test
	public void wikiMarkupIsKeptAsIs() {
		String wiki = "* Given a logged in user\n* When she opens the report\n* Then the KPI is shown";

		assertEquals(wiki, JiraTextFieldUtil.toPlainText(wiki));
	}

	/** Jira Cloud (API v3) returns an Atlassian Document Format tree. */
	@Test
	public void adfDocumentIsFlattenedToText() {
		String adf =
				"{\"type\":\"doc\",\"version\":1,\"content\":["
						+ "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Acceptance Criteria\"}]},"
						+ "{\"type\":\"bulletList\",\"content\":["
						+ "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":"
						+ "[{\"type\":\"text\",\"text\":\"Given a logged in user\"}]}]},"
						+ "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":"
						+ "[{\"type\":\"text\",\"text\":\"Then the KPI is shown\"}]}]}]}]}";

		String text = JiraTextFieldUtil.toPlainText(adf);

		assertEquals("Acceptance Criteria\nGiven a logged in user\nThen the KPI is shown", text);
	}

	@Test
	public void adfHardBreakBecomesNewLine() {
		String adf =
				"{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
						+ "{\"type\":\"text\",\"text\":\"first\"},{\"type\":\"hardBreak\"},"
						+ "{\"type\":\"text\",\"text\":\"second\"}]}]}";

		assertEquals("first\nsecond", JiraTextFieldUtil.toPlainText(adf));
	}

	@Test
	public void adfWithoutAnyTextGivesNull() {
		assertNull(JiraTextFieldUtil.toPlainText("{\"type\":\"doc\",\"version\":1,\"content\":[]}"));
	}

	/** A single select custom field arrives as {@code {"value":"..."}}. */
	@Test
	public void selectOptionUsesItsValue() throws JSONException {
		JSONObject option = new JSONObject().put("value", "Ready").put("id", "10101");

		assertEquals("Ready", JiraTextFieldUtil.toPlainText(option));
	}

	/** A multi select custom field arrives as an array of options. */
	@Test
	public void multiSelectOptionsAreJoined() {
		String options = "[{\"value\":\"Ready\"},{\"value\":\"Reviewed\"}]";

		assertEquals("Ready, Reviewed", JiraTextFieldUtil.toPlainText(options));
	}

	@Test
	public void unparsableJsonIsReturnedUntouched() {
		String notJson = "{ this is not json";

		assertEquals(notJson, JiraTextFieldUtil.toPlainText(notJson));
	}

	@Test
	public void blankLineRunsAreCollapsed() {
		String adf =
				"{\"type\":\"doc\",\"content\":["
						+ "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"one\"}]},"
						+ "{\"type\":\"paragraph\"},{\"type\":\"paragraph\"},{\"type\":\"paragraph\"},"
						+ "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"two\"}]}]}";

		String text = JiraTextFieldUtil.toPlainText(adf);

		assertTrue(text.startsWith("one"));
		assertTrue(text.endsWith("two"));
		assertTrue(!text.contains("\n\n\n"));
	}
}
