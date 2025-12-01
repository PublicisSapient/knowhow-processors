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

package com.publicissapient.kpidashboard.job.aiusagestatisticscollector.writer;

import java.util.List;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.service.AIUsageStatisticsService;
import com.publicissapient.kpidashboard.job.constant.AiDataProcessorConstants;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class AccountItemWriter implements ItemWriter<AIUsageStatistics> {
    private final AIUsageStatisticsService aiUsageStatisticsService;

    @Override
    public void write(@NonNull Chunk<? extends AIUsageStatistics> chunk) {
        log.info("{} Received chunk items for inserting into database with size: {}", AiDataProcessorConstants.LOG_PREFIX_AI_USAGE_STATISTICS, chunk.size());
        aiUsageStatisticsService.saveAll((List.copyOf(chunk.getItems())));
    }
}
