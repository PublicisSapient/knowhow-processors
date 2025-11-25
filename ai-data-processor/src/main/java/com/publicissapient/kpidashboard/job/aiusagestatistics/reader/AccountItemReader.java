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

package com.publicissapient.kpidashboard.job.aiusagestatistics.reader;

import com.publicissapient.kpidashboard.job.aiusagestatistics.dto.PagedAIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.aiusagestatistics.service.AccountBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

@Slf4j
@RequiredArgsConstructor
public class AccountItemReader implements ItemReader<PagedAIUsagePerOrgLevel> {

    private final AccountBatchService accountBatchService;

    @Override
    public PagedAIUsagePerOrgLevel read() {
        PagedAIUsagePerOrgLevel aiUsageStatistics = accountBatchService.getNextAccountPage();
        log.info("Reader fetched level name: {}", aiUsageStatistics.levelName());
        return aiUsageStatistics;
    }
}