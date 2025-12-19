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

package com.publicissapient.kpidashboard.aiusagestatistics.service;

import com.publicissapient.kpidashboard.client.shareddataservice.SharedDataServiceClient;
import com.publicissapient.kpidashboard.exception.InternalServerErrorException;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.AIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.AIUsageSummary;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.mapper.AIUsageStatisticsMapper;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.enums.AIUsageAggregationType;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.repository.AIUsageStatisticsRepository;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.service.AIUsageStatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIUsageStatisticsServiceTest {

    @Mock
    private AIUsageStatisticsRepository repository;

    @Mock
    private SharedDataServiceClient webClient;

    @Mock
    private AIUsageStatisticsMapper mapper;

    @InjectMocks
    private AIUsageStatisticsService service;

    @Test
    void saveAIUsageStatistics_successful() {
        String levelName = "TestAccount";

        AIUsageSummary summary = new AIUsageSummary(
                100L,
                50L,
                10L,
                5L,
                AIUsageAggregationType.TOTAL
        );

        AIUsagePerOrgLevel response = new AIUsagePerOrgLevel(
                "account",
                levelName,
                Instant.now(),
                summary,
                null,
                1,
                1,
                1L,
                1
        );

        when(webClient.getAIUsageStatsAsync(levelName)).thenReturn(response);
        when(mapper.toEntity(response)).thenReturn(new AIUsageStatistics() {{
            setLevelName(levelName);
            setLevelType("account");
            setUsageSummary(summary);
        }});

        AIUsageStatistics result = service.fetchAIUsageStatistics(levelName);

        assertNotNull(result);
        assertEquals(levelName, result.getLevelName());
        assertEquals("account", result.getLevelType());
        assertEquals(summary, result.getUsageSummary());
    }

    @Test
    void saveAIUsageStatistics_nullResponse() {
        String levelName = "Account";
        when(webClient.getAIUsageStatsAsync(levelName)).thenReturn(null);

        AIUsageStatistics result= service.fetchAIUsageStatistics(levelName);

        assertNull(result);
        verify(repository, never()).save(any());
    }

    @Test
    void saveAIUsageStatistics_clientThrowsException() {
        String levelName = "FailAccount";
        when(webClient.getAIUsageStatsAsync(levelName)).thenThrow(new RuntimeException("Network error"));

        assertThrows(InternalServerErrorException.class, () -> service.fetchAIUsageStatistics(levelName));
        verify(repository, never()).save(any());
    }
}
