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

import com.publicissapient.kpidashboard.common.model.application.AccountHierarchy;
import com.publicissapient.kpidashboard.common.repository.application.AccountHierarchyRepository;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.AIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.service.AccountBatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountBatchServiceTest {

    @Mock
    private AccountHierarchyRepository repository;
    @InjectMocks
    private AccountBatchService service;

    private AccountHierarchy createAccount(String nodeId, String nodeName) {
        AccountHierarchy account = new AccountHierarchy();
        account.setNodeId(nodeId);
        account.setNodeName(nodeName);
        return account;
    }

    @Test
    void initializeBatchProcessingParametersForTheNextProcess_loadsAccountsAndSorts() {
        List<AccountHierarchy> accounts = List.of(
                createAccount("2", "AccountB"),
                createAccount("1", "AccountA")
        );
        when(repository.findDistinctByLabel("acc")).thenReturn(accounts);

        service.initializeBatchProcessingParametersForTheNextProcess();

        AIUsagePerOrgLevel page = service.getNextAccount();
        assertNotNull(page);
        assertEquals("AccountA", page.levelName());
        assertEquals(1, page.currentPage());
        assertEquals(2, page.totalPages());
        assertEquals(1, page.pageSize());
    }

    @Test
    void getNextAccount_returnsAccountsOneByOne() {
        List<AccountHierarchy> accounts = List.of(
                createAccount("1", "Account1"),
                createAccount("2", "Account2")
        );
        when(repository.findDistinctByLabel("acc")).thenReturn(accounts);

        AIUsagePerOrgLevel page1 = service.getNextAccount();
        assertNotNull(page1);
        assertEquals("Account1", page1.levelName());

        AIUsagePerOrgLevel page2 = service.getNextAccount();
        assertNotNull(page2);
        assertEquals("Account2", page2.levelName());

        AIUsagePerOrgLevel page3 = service.getNextAccount();
        assertNull(page3);
    }

    @Test
    void getNextAccount_emptyRepository_returnsNull() {
        when(repository.findDistinctByLabel("acc")).thenReturn(List.of());

        AIUsagePerOrgLevel page = service.getNextAccount();
        assertNull(page);
    }

    @Test
    void getNextAccount_multipleCalls_iteratesCorrectly() {
        List<AccountHierarchy> accounts = List.of(
                createAccount("1", "Account1"),
                createAccount("2", "Account2"),
                createAccount("3", "Account3")
        );
        when(repository.findDistinctByLabel("acc")).thenReturn(accounts);

        AIUsagePerOrgLevel page1 = service.getNextAccount();
        AIUsagePerOrgLevel page2 = service.getNextAccount();
        AIUsagePerOrgLevel page3 = service.getNextAccount();
        AIUsagePerOrgLevel page4 = service.getNextAccount();

        assertEquals("Account1", page1.levelName());
        assertEquals("Account2", page2.levelName());
        assertEquals("Account3", page3.levelName());
        assertNull(page4);
    }
}
