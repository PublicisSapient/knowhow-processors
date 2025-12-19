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

package com.publicissapient.kpidashboard.job.aiusagestatisticscollector.service;

import java.util.List;

import com.publicissapient.kpidashboard.client.shareddataservice.SharedDataServiceClient;
import com.publicissapient.kpidashboard.exception.InternalServerErrorException;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.AIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.repository.AIUsageStatisticsRepository;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.mapper.AIUsageStatisticsMapper;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@AllArgsConstructor
public class AIUsageStatisticsService {
    private final AIUsageStatisticsRepository aiUsageStatisticsRepository;
    private final SharedDataServiceClient sharedDataServiceClient;
    private final AIUsageStatisticsMapper aiUsageStatisticsMapper;

    public AIUsageStatistics fetchAIUsageStatistics(String levelName) {
        try {
            AIUsagePerOrgLevel aiUsageStatistics = sharedDataServiceClient.getAIUsageStatsAsync(levelName);
            return aiUsageStatisticsMapper.toEntity(aiUsageStatistics);
        } catch (Exception ex) {
            log.error("Failed to fetch AI usage stats for {}: {}", levelName, ex.getMessage());
            throw new InternalServerErrorException("Exception caught while fetching the ai usage for {}" + levelName);
        }
    }

    @Transactional
    public void saveAll(List<AIUsageStatistics> aiUsageStatisticsList) {
        aiUsageStatisticsRepository.saveAll(aiUsageStatisticsList);
        log.info("Successfully fetched and saved {} AI usage statistics for account: {}", aiUsageStatisticsList.size(), aiUsageStatisticsList.get(0).getLevelName());
    }
}
