/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.job.shareddataservice.enums;

public enum AIUsageAggregationType {
    LAST_DAY("Last Uploaded Day"),
    LAST_30_DAYS("Last Uploaded 30 Days"),
    YTD("Year to Last Uploaded Date"),
    TOTAL("Total");

    private final String displayName;

    AIUsageAggregationType(String displayName) {
        this.displayName = displayName;
    }
}
