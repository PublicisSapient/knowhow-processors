package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
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
class BitbucketCommitsServiceImplTest {

    @Mock
    private BitbucketClient bitbucketClient;

    @Mock
    private BitbucketCommonHelper commonHelper;

    @InjectMocks
    private BitbucketCommitsServiceImpl commitsService;

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
    void testFetchCommits_Success() throws IOException, PlatformApiException {
        List<Map<String, Object>> bbCommits = createMockCommits();
        when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(bbCommits);

        List<ScmCommits> result = commitsService.fetchCommits(toolConfigId, gitUrlInfo, "main", "user:pass", since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(bitbucketClient).fetchCommits(eq("owner"), eq("testRepo"), eq("main"), eq("user"), eq("pass"), any(), any());
    }

    @Test
    void testFetchCommits_IOException() throws IOException {
        when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new IOException("API error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> commitsService.fetchCommits(toolConfigId, gitUrlInfo, "main", "user:pass", since, until));

        assertEquals("Bitbucket", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch commits from Bitbucket"));
    }

    @Test
    void testGetPlatformName() {
        assertEquals("Bitbucket", commitsService.getPlatformName());
    }

    private List<Map<String, Object>> createMockCommits() {
        List<Map<String, Object>> commits = new ArrayList<>();
        commits.add(createMockCommit("hash1", "Test commit 1"));
        commits.add(createMockCommit("hash2", "Test commit 2"));
        return commits;
    }

    private Map<String, Object> createMockCommit(String hash, String message) {
        Map<String, Object> commit = new HashMap<>();
        commit.put("hash", hash);
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("raw", message);
        commit.put("message", messageMap);
        return commit;
    }
}
