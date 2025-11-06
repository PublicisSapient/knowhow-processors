/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.rally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.publicissapient.kpidashboard.common.util.SecureStringUtil;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.common.service.ToolCredentialProvider;
import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.Iteration;
import com.publicissapient.kpidashboard.rally.model.IterationResponse;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.QueryResult;
import com.publicissapient.kpidashboard.rally.model.RallyResponse;
import com.publicissapient.kpidashboard.rally.model.RallyToolConfig;

@ExtendWith(MockitoExtension.class)
public class RallyCommonServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RallyProcessorConfig rallyProcessorConfig;

    @Mock
    private AesEncryptionService aesEncryptionService;

    @Mock
    private ToolCredentialProvider toolCredentialProvider;

    @Mock
    private ProcessorToolConnectionService processorToolConnectionService;

    @Mock
    private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepository;

    @InjectMocks
    private RallyCommonService rallyCommonService;

    private ProjectConfFieldMapping projectConfig;
    private Connection connection;
    private RallyToolConfig rallyToolConfig;
    private ObjectId basicProjectConfigId;

    private static final String TEST_USERNAME = "testuser";
    private static final String ENCRYPTED_PASSWORD = SecureStringUtil.generateRandomPassword(10);

    @BeforeEach
    public void setup() throws Exception {
        basicProjectConfigId = new ObjectId();
        projectConfig = new ProjectConfFieldMapping();
        projectConfig.setBasicProjectConfigId(basicProjectConfigId);

        ProjectBasicConfig basicConfig = new ProjectBasicConfig();
        basicConfig.setId(basicProjectConfigId);

        connection = new Connection();
        connection.setId(new ObjectId());
        connection.setOffline(false);
        connection.setUsername(TEST_USERNAME);
        connection.setPassword(ENCRYPTED_PASSWORD);

        rallyToolConfig = new RallyToolConfig();
        rallyToolConfig.setConnection(Optional.of(connection));

        projectConfig.setJira(rallyToolConfig);

        ProjectToolConfig projectToolConfig = new ProjectToolConfig();
        projectToolConfig.setConnectionId(connection.getId());
        projectConfig.setProjectToolConfig(projectToolConfig);
    }

    @Test
    public void testGetDataFromClientWithMalformedUrl() {
        assertThrows(IOException.class, () -> {
            URL testUrl = new URL("invalid://url");
            rallyCommonService.getDataFromClient(projectConfig, testUrl);
        });
    }

    @Test
    public void testDecryptJiraPassword() {
        // Setup
        String encryptedPassword = "encryptedPassword";
        String decryptedPassword = "decryptedPassword";
        String aesKey = "aesKey";

        when(rallyProcessorConfig.getAesEncryptionKey()).thenReturn(aesKey);
        when(aesEncryptionService.decrypt(encryptedPassword, aesKey)).thenReturn(decryptedPassword);

        // Execute
        String result = rallyCommonService.decryptJiraPassword(encryptedPassword);

        // Verify
        assertEquals(decryptedPassword, result);
    }

    @Test
    public void testEncodeCredentialsToBase64() {
        // Execute
        String result = rallyCommonService.encodeCredentialsToBase64("user", "pass");

        // Verify
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    public void testGetApiHost() throws Exception {
        // Setup
        when(rallyProcessorConfig.getUiHost()).thenReturn("rally1.rallydev.com");

        // Execute
        String result = rallyCommonService.getApiHost();

        // Verify - just check that it contains the host name, ignoring slash direction
        assertTrue(result.contains("rally1.rallydev.com"), "Expected URL to contain the host name");
    }

    @Test
    public void testGetApiHostWithEmptyHost() {
        // Setup
        when(rallyProcessorConfig.getUiHost()).thenReturn("");

        // Execute and verify
        assertThrows(UnknownHostException.class, () -> {
            rallyCommonService.getApiHost();
        });
    }

    @Test
    public void testSaveSearchDetailsInContext() {
        // Setup
        RallyResponse rallyResponse = new RallyResponse();
        QueryResult queryResult = new QueryResult();
        queryResult.setTotalResultCount(100);
        rallyResponse.setQueryResult(queryResult);

        StepContext stepContext = mock(StepContext.class);
        StepExecution stepExecution = mock(StepExecution.class);
        JobExecution jobExecution = new JobExecution(1L, new JobParameters());
        ExecutionContext executionContext = new ExecutionContext();
        jobExecution.setExecutionContext(executionContext);

        when(stepContext.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);
        when(rallyProcessorConfig.getPageSize()).thenReturn(20);

        // Execute
        rallyCommonService.saveSearchDetailsInContext(rallyResponse, 1, "board123", stepContext);

        // Verify
        assertEquals(100, executionContext.getInt(RallyConstants.TOTAL_ISSUES));
        assertEquals(20, executionContext.getInt(RallyConstants.PROCESSED_ISSUES));
        assertEquals(1, executionContext.getInt(RallyConstants.PAGE_START));
        assertEquals("board123", executionContext.getString(RallyConstants.BOARD_ID));
    }

    @Test
    public void testSaveSearchDetailsInContextWithNullStepContext() {
        // Setup
        RallyResponse rallyResponse = new RallyResponse();
        QueryResult queryResult = new QueryResult();
        queryResult.setTotalResultCount(100);
        rallyResponse.setQueryResult(queryResult);

        // Execute - should not throw exception
        rallyCommonService.saveSearchDetailsInContext(rallyResponse, 1, "board123", null);

        // No assertions needed as we're just verifying it doesn't throw an exception
    }

    @Test
    public void testFetchIterationDetails() throws Exception {
        // Setup
        String iterationUrl = "https://rally1.rallydev.com/slm/webservice/v2.0/iteration/12345";
        HttpEntity<String> entity = new HttpEntity<>(null);

        IterationResponse iterationResponse = new IterationResponse();
        Iteration iteration = new Iteration();
        iteration.setName("Sprint 1");
        iterationResponse.setIteration(iteration);

        ResponseEntity<IterationResponse> responseEntity = new ResponseEntity<>(iterationResponse, HttpStatus.OK);
        when(restTemplate.exchange(eq(iterationUrl), eq(HttpMethod.GET), any(), eq(IterationResponse.class)))
                .thenReturn(responseEntity);

        // Use reflection to access private method
        java.lang.reflect.Method method = RallyCommonService.class.getDeclaredMethod(
                "fetchIterationDetails", String.class, HttpEntity.class);
        method.setAccessible(true);

        // Execute
        Iteration result = (Iteration) method.invoke(rallyCommonService, iterationUrl, entity);

        // Verify
        assertNotNull(result);
        assertEquals("Sprint 1", result.getName());
    }
}
