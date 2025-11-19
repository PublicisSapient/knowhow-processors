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

package com.publicissapient.kpidashboard.job.shareddataservice.service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import com.publicissapient.kpidashboard.client.shareddataservice.SharedDataServiceClient;
import com.publicissapient.kpidashboard.job.shareddataservice.dto.PagedAIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.shareddataservice.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.job.shareddataservice.repository.AIUsageStatisticsRepository;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class AIUsageStatisticsService {
    private final AIUsageStatisticsRepository aiUsageStatisticsRepository;
    private final SharedDataServiceClient webClient;

    public CompletableFuture<AIUsageStatistics> saveAIUsageStatistics(String levelName) {
        return webClient.getAIUsageStatsAsync(levelName)
                .thenApply(responseBody -> persistData(levelName, responseBody))
                .exceptionally(ex -> {
                    log.error("Failed to fetch AI usage stats for {}: {}", levelName, ex.getMessage());
                    return null;
                });
    }

    private AIUsageStatistics persistData(String levelName, PagedAIUsagePerOrgLevel responseBody) {
        if (responseBody != null) {
            AIUsageStatistics essentialStatistics = new AIUsageStatistics(
                    responseBody.levelType(),
                    responseBody.levelName(),
                    responseBody.statsDate(),
                    Instant.now(),
                    responseBody.usageSummary(),
                    responseBody.users()
            );
            aiUsageStatisticsRepository.save(essentialStatistics);
            log.info("Successfully fetched and saved AI usage stats for {}", levelName);
            return essentialStatistics;
        } else {
            log.warn("Received null response for level {}", levelName);
            return null;
        }
    }
}
