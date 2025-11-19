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

import com.publicissapient.kpidashboard.common.model.application.AccountHierarchy;
import com.publicissapient.kpidashboard.common.repository.application.AccountHierarchyRepository;
import com.publicissapient.kpidashboard.job.shareddataservice.dto.PagedAIUsagePerOrgLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountBatchService {

    private static final String HIERARCHY_LABEL = "acc";

    private final AccountHierarchyRepository accountHierarchyRepository;

    private List<AccountHierarchy> allAccounts;
    private int currentIndex;
    private boolean initialized = false;

    @PostConstruct
    public void reset() {
        initialized = false;
        currentIndex = 0;
        allAccounts = new ArrayList<>();
    }

    public void initializeBatchProcessingParametersForTheNextProcess() {
        allAccounts = accountHierarchyRepository.findDistinctByLabel(HIERARCHY_LABEL)
                .stream()
                .sorted((a1, a2) -> a1.getNodeName().compareToIgnoreCase(a2.getNodeName()))
                .toList();

        currentIndex = 0;
        initialized = true;

        log.info("Loaded {} accounts for AI usage processing", allAccounts.size());
    }

    /**
     * Return next account to process as a PagedAIUsagePerOrgLevel.
     * Each page contains exactly 1 account because the endpoint supports only 1 account per request.
     */
    public PagedAIUsagePerOrgLevel getNextAccountPage() {
        if (!initialized) {
            initializeBatchProcessingParametersForTheNextProcess();
        }

        if (currentIndex >= allAccounts.size()) {
            return null;
        }

        AccountHierarchy account = allAccounts.get(currentIndex);

        PagedAIUsagePerOrgLevel page = new PagedAIUsagePerOrgLevel(
                "account",
                account.getNodeName(),
                Instant.now(),
                null,
                null,
                currentIndex + 1,
                allAccounts.size(),
                allAccounts.size(),
                1
        );

        currentIndex++;
        return page;
    }
}
