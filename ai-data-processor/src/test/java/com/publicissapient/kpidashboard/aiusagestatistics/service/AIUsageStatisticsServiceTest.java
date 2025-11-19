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
import com.publicissapient.kpidashboard.job.shareddataservice.dto.AIUsageSummary;
import com.publicissapient.kpidashboard.job.shareddataservice.dto.PagedAIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.shareddataservice.enums.AIUsageAggregationType;
import com.publicissapient.kpidashboard.job.shareddataservice.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.job.shareddataservice.repository.AIUsageStatisticsRepository;
import com.publicissapient.kpidashboard.job.shareddataservice.service.AIUsageStatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIUsageStatisticsServiceTest {

    @Mock
    private AIUsageStatisticsRepository repository;

    @Mock
    private SharedDataServiceClient webClient;

    @InjectMocks
    private AIUsageStatisticsService service;

    @Test
    void saveAIUsageStatistics_successful() throws Exception {
        String levelName = "TestAccount";

        AIUsageSummary summary = new AIUsageSummary(
                100L,
                50L,
                10L,
                5L,
                AIUsageAggregationType.TOTAL
        );

        PagedAIUsagePerOrgLevel response = new PagedAIUsagePerOrgLevel(
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

        when(webClient.getAIUsageStatsAsync(levelName))
                .thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<AIUsageStatistics> future = service.saveAIUsageStatistics(levelName);
        AIUsageStatistics result = future.get();

        assertNotNull(result);
        assertEquals(levelName, result.getLevelName());
        assertEquals("account", result.getLevelType());
        assertEquals(summary, result.getUsageSummary());

        ArgumentCaptor<AIUsageStatistics> captor = ArgumentCaptor.forClass(AIUsageStatistics.class);
        verify(repository, times(1)).save(captor.capture());
        AIUsageStatistics saved = captor.getValue();
        assertEquals(levelName, saved.getLevelName());
    }

    @Test
    void saveAIUsageStatistics_nullResponse() throws Exception {
        String levelName = "NullAccount";
        when(webClient.getAIUsageStatsAsync(levelName))
                .thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<AIUsageStatistics> future = service.saveAIUsageStatistics(levelName);
        AIUsageStatistics result = future.get();

        assertNull(result);
        verify(repository, never()).save(any());
    }

    @Test
    void saveAIUsageStatistics_clientThrowsException() throws Exception {
        String levelName = "FailAccount";
        when(webClient.getAIUsageStatsAsync(levelName))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));

        CompletableFuture<AIUsageStatistics> future = service.saveAIUsageStatistics(levelName);
        AIUsageStatistics result = future.get();

        assertNull(result);
        verify(repository, never()).save(any());
    }
}
