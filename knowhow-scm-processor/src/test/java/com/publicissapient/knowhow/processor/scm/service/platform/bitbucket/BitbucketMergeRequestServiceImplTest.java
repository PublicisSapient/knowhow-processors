package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BitbucketMergeRequestServiceImplTest {

    @Mock
    private BitbucketClient bitbucketClient;

    @Mock
    private BitbucketCommonHelper commonHelper;

    @InjectMocks
    private BitbucketMergeRequestServiceImpl mergeRequestService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "testRepo", null,
                "https://bitbucket.org/owner/repo.git");
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void testFetchMergeRequests_Success() throws IOException, PlatformApiException {
        List<Map<String, Object>> bbPRs = createMockPullRequests();
        when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(bbPRs);

        List<ScmMergeRequests> result = mergeRequestService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, "user:pass", since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(bitbucketClient).fetchPullRequests(eq("owner"), eq("testRepo"), isNull(), eq("user"), eq("pass"), any(), any());
    }

    @Test
    void testFetchMergeRequests_IOException() throws IOException {
        when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new IOException("API error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> mergeRequestService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", "user:pass", since, until));

        assertEquals("Bitbucket", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch merge requests from Bitbucket"));
    }

    @Test
    void testGetPlatformName() {
        assertEquals("Bitbucket", mergeRequestService.getPlatformName());
    }

    private List<Map<String, Object>> createMockPullRequests() {
        List<Map<String, Object>> prs = new ArrayList<>();
        prs.add(createMockPullRequest(1, "Test PR 1"));
        prs.add(createMockPullRequest(2, "Test PR 2"));
        return prs;
    }

    private Map<String, Object> createMockPullRequest(int id, String title) {
        Map<String, Object> pr = new HashMap<>();
        pr.put("id", id);
        pr.put("title", title);
        pr.put("state", "OPEN");
        return pr;
    }
}
