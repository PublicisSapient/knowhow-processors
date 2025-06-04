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

package com.publicissapient.kpidashboard.rally.constant;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
/**
 * @author girpatha
 */
@Service
public final class RallyConstants {

    public static final Set<String> ISSUE_FIELD_SET = new HashSet<>(); // NOSONAR
    public static final String STATUS = "status";
    public static final String CUSTOM_FIELD = "CustomField";
    public static final String VALUE = "value";
    public static final String JIRA_ISSUE_CHANGE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS";
    public static final String FALSE = "False";
    public static final String RALLY = "Rally";
    public static final String ORDERBY = "order by";

    public static final String QUERYDATEFORMAT = "yyyy-MM-dd HH:mm";
    public static final String TO_DO = "To Do";
    public static final String DONE = "Done";
    public static final String ERROR_MSG_401 = "Error 401 connecting to RALLY server, your credentials are probably wrong. Note: Ensure you are using RALLY user name not your email address.";
    public static final String ERROR_MSG_NO_RESULT_WAS_AVAILABLE = "No result was available from Jira unexpectedly - defaulting to blank response. The reason for this fault is the following : {}";
    public static final String TOTAL_ISSUES = "total issues";
    public static final String PROCESSED_ISSUES = "processed issues";
    public static final String PAGE_START = "pageStart";
    public static final String BOARD_ID = "boardId";
    public static final String NAME = "name";
    public static final String ERROR_NOTIFICATION_SUBJECT_KEY = "errorInJiraProcessor";
    public static final String ERROR_MAIL_TEMPLATE_KEY = "Error_In_Jira_Processor";
    public static final String HIERARCHY_REVISION_HISTORY = "HierarchyRevisionHistory";

    static {
        ISSUE_FIELD_SET.add("*all,-attachment,-worklog,-comment,-votes,-watches");
    }

    private RallyConstants() {
    }
}
