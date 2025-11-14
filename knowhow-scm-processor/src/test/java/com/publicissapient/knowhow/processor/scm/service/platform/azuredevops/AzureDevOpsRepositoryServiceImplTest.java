package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;

@ExtendWith(MockitoExtension.class)
class AzureDevOpsRepositoryServiceImplTest {

    @Mock
    private AzureDevOpsClient azureDevOpsClient;

    @Mock
    private GitUrlParser gitUrlParser;

    @InjectMocks
    private AzureDevOpsRepositoryServiceImpl service;

    private ScanRequest scanRequest;
    private ObjectId connectionId;

    @BeforeEach
    void setUp() {
        connectionId = new ObjectId();
        scanRequest = ScanRequest.builder().build();
        scanRequest.setBaseUrl("https://dev.azure.com/testOrg/testProject");
        scanRequest.setToken("testToken");
        scanRequest.setSince(LocalDateTime.now().minusDays(7));
        scanRequest.setConnectionId(connectionId);
    }

    @Test
    void fetchRepositories_Success() throws Exception {
        ScmRepos repo = ScmRepos.builder().build();
        repo.setRepositoryName("testRepo");
        when(azureDevOpsClient.fetchRepositories(anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList(repo));

        List<ScmRepos> result = service.fetchRepositories(scanRequest);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("testRepo", result.get(0).getRepositoryName());
        verify(azureDevOpsClient).fetchRepositories(eq("testToken"), eq("testOrg"), any(), eq(connectionId));
    }

    @Test
    void fetchRepositories_WithHttpUrl() throws Exception {
        scanRequest.setBaseUrl("http://dev.azure.com/myOrg/myProject");
        when(azureDevOpsClient.fetchRepositories(anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList());

        List<ScmRepos> result = service.fetchRepositories(scanRequest);

        assertNotNull(result);
        verify(azureDevOpsClient).fetchRepositories(eq("testToken"), eq("myOrg"), any(), eq(connectionId));
    }

    @Test
    void fetchRepositories_WithUserInUrl() throws Exception {
        scanRequest.setBaseUrl("https://user@dev.azure.com/testOrg/testProject");
        when(azureDevOpsClient.fetchRepositories(anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList());

        List<ScmRepos> result = service.fetchRepositories(scanRequest);

        assertNotNull(result);
        verify(azureDevOpsClient).fetchRepositories(eq("testToken"), eq("testOrg"), any(), eq(connectionId));
    }

    @Test
    void fetchRepositories_InvalidUrl_ThrowsException() {
        scanRequest.setBaseUrl("https://github.com/testOrg/testRepo");

        PlatformApiException exception = assertThrows(PlatformApiException.class, () ->
                service.fetchRepositories(scanRequest));

        assertTrue(exception.getMessage().contains("The provided URL is not a valid Azure DevOps URL"));
    }

    @Test
    void fetchRepositories_MalformedUrl_ThrowsException() {
        scanRequest.setBaseUrl("not-a-valid-url");

        assertThrows(PlatformApiException.class, () ->
                service.fetchRepositories(scanRequest));
    }

    @Test
    void fetchRepositories_ClientThrowsException() throws Exception {
        when(azureDevOpsClient.fetchRepositories(anyString(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("API error"));

        assertThrows(PlatformApiException.class, () ->
                service.fetchRepositories(scanRequest));
    }

    @Test
    void fetchRepositories_EmptyList() throws Exception {
        when(azureDevOpsClient.fetchRepositories(anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList());

        List<ScmRepos> result = service.fetchRepositories(scanRequest);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchRepositories_MultipleRepos() throws Exception {
        ScmRepos repo1 = ScmRepos.builder().build();
        repo1.setRepositoryName("repo1");
        ScmRepos repo2 = ScmRepos.builder().build();
        repo2.setRepositoryName("repo2");

        when(azureDevOpsClient.fetchRepositories(anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList(repo1, repo2));

        List<ScmRepos> result = service.fetchRepositories(scanRequest);

        assertNotNull(result);
        assertEquals(2, result.size());
    }
}
