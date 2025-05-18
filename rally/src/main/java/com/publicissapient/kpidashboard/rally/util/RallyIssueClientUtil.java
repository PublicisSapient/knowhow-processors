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

package com.publicissapient.kpidashboard.rally.util;

import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author girpatha
 */
@Slf4j
public final class RallyIssueClientUtil {

	public static final Comparator<SprintDetails> SPRINT_COMPARATOR = (SprintDetails o1, SprintDetails o2) -> {
		int cmp1 = ObjectUtils.compare(o1.getStartDate(), o2.getStartDate());
		if (cmp1 != 0) {
			return cmp1;
		}
		return ObjectUtils.compare(o1.getEndDate(), o2.getEndDate());
	};

	private RallyIssueClientUtil() {
		super();
	}

	/**
	 * Builds Filed Map
	 *
	 * @param fields
	 *          IssueField Iterable
	 * @return Map of FieldIssue ID and FieldIssue Object
	 */
	public static Map<String, IssueField> buildFieldMap(Iterable<IssueField> fields) {
		Map<String, IssueField> rt = new HashMap<>();

		if (fields != null) {
			for (IssueField issueField : fields) {
				rt.put(issueField.getId(), issueField);
			}
		}

		return rt;
	}
}
