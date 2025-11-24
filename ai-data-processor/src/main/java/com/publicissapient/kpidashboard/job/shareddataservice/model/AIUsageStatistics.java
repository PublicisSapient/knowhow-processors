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

package com.publicissapient.kpidashboard.job.shareddataservice.model;

import java.time.Instant;
import java.util.List;

import com.publicissapient.kpidashboard.job.shareddataservice.dto.AIUsagePerUser;
import com.publicissapient.kpidashboard.job.shareddataservice.dto.AIUsageSummary;
import org.springframework.data.mongodb.core.mapping.Document;

import com.publicissapient.kpidashboard.common.model.generic.BasicModel;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_usage_statistics")
public class AIUsageStatistics extends BasicModel {
    private String levelType;
    private String levelName;
    private Instant statsDate;
    private Instant ingestTimestamp;
    private AIUsageSummary usageSummary;
    private List<AIUsagePerUser> users;

    public String getLevelType() {
        return levelType;
    }

    public void setLevelType(String levelType) {
        this.levelType = levelType;
    }

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }

    public Instant getStatsDate() {
        return statsDate;
    }

    public void setStatsDate(Instant statsDate) {
        this.statsDate = statsDate;
    }

    public Instant getIngestTimestamp() {
        return ingestTimestamp;
    }

    public void setIngestTimestamp(Instant ingestTimestamp) {
        this.ingestTimestamp = ingestTimestamp;
    }

    public AIUsageSummary getUsageSummary() {
        return usageSummary;
    }

    public void setUsageSummary(AIUsageSummary usageSummary) {
        this.usageSummary = usageSummary;
    }

    public List<AIUsagePerUser> getUsers() {
        return users;
    }

    public void setUsers(List<AIUsagePerUser> users) {
        this.users = users;
    }
}
