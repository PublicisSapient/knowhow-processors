/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2020 Sapient Limited.
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

package com.publicissapient.kpidashboard.jiratest.adapter.atlassianbespoke.parser;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.atlassian.jira.rest.client.api.ExpandableProperty;
import com.atlassian.jira.rest.client.api.domain.BasicUser;
import com.atlassian.jira.rest.client.api.domain.User;
import com.atlassian.jira.rest.client.internal.json.JsonObjectParser;
import com.atlassian.jira.rest.client.internal.json.UserJsonParser;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.publicissapient.kpidashboard.jiratest.adapter.atlassianbespoke.util.JsonParseUtil;

public class CustomUserJsonParser extends UserJsonParser {

	@Override
	public User parse(JSONObject json) throws JSONException {
		final BasicUser basicUser = Preconditions.checkNotNull(JsonParseUtil.parseBasicUser(json));
		final String timezone = JsonParseUtil.getOptionalString(json, "timeZone");
		final String avatarUrl = JsonParseUtil.getOptionalString(json, "avatarUrl");
		Map<String, URI> avatarUris = Maps.newHashMap();
		if (avatarUrl != null) {
			// JIRA prior 5.0
			final URI avatarUri = JsonParseUtil.parseURI(avatarUrl);
			avatarUris.put(User.S48_48, avatarUri);
		} else {
			// JIRA 5.0+
			final JSONObject avatarUrlsJson = json.getJSONObject("avatarUrls");
			@SuppressWarnings("unchecked")
			final Iterator<String> iterator = avatarUrlsJson.keys();
			while (iterator.hasNext()) {
				final String key = iterator.next();
				avatarUris.put(key, JsonParseUtil.parseURI(avatarUrlsJson.getString(key)));
			}
		}
		// e-mail may be not set in response if e-mail visibility in jira configuration
		// is set to hidden (in jira 4.3+)
		final String emailAddress = JsonParseUtil.getOptionalString(json, "emailAddress");
		final ExpandableProperty<String> groups = JsonParseUtil
				.parseOptionalExpandableProperty(json.optJSONObject("groups"), new JsonObjectParser<String>() {
					@Override
					public String parse(JSONObject json) throws JSONException {
						if (json.has("name")) {
							return json.getString("name");
						}
						return json.has("displayName") ? json.getString("displayName") : "";
					}
				});
		return new User(basicUser.getSelf(), basicUser.getName(), basicUser.getDisplayName(), emailAddress, true, groups,
				avatarUris, timezone);
	}
}
