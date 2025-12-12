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

package com.publicissapient.kpidashboard.job.aiusagestatisticscollector.processor;

import org.springframework.batch.item.ItemProcessor;

import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.AIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.service.AIUsageStatisticsService;
import com.publicissapient.kpidashboard.job.constant.JobConstants;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class AccountItemProcessor implements ItemProcessor<AIUsagePerOrgLevel, AIUsageStatistics> {
    private final AIUsageStatisticsService aiUsageStatisticsService;

    @Override
    public AIUsageStatistics process(@Nonnull AIUsagePerOrgLevel item) {
        log.debug("{} Fetching AI usage statistics for level name: {}", JobConstants.LOG_PREFIX_AI_USAGE_STATISTICS, item.levelName());
        try {
            return aiUsageStatisticsService.fetchAIUsageStatistics(item.levelName());
        } catch (Exception ex) {
            log.error("Failed fetching AI stats for {} – skipping", item.levelName());
            throw ex;
        }
    }
}