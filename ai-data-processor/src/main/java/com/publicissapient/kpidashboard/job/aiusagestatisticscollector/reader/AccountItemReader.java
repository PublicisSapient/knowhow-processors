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

package com.publicissapient.kpidashboard.job.aiusagestatisticscollector.reader;

import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.AIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import org.springframework.batch.item.ItemReader;

import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.service.AccountBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AccountItemReader implements ItemReader<AIUsagePerOrgLevel> {

    private final AccountBatchService accountBatchService;

    @Override
    public AIUsagePerOrgLevel read() {
        AIUsagePerOrgLevel aiUsageStatistics = accountBatchService.getNextAccount();
        if (aiUsageStatistics == null) {
            log.info("No more accounts.");
            return null;
        }
        log.info("{} Reader fetched level name: {}", JobConstants.LOG_PREFIX_AI_USAGE_STATISTICS, aiUsageStatistics.levelName());
        return aiUsageStatistics;
    }
}