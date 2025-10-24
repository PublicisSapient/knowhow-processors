package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitLabCommitsServiceImplTest {

    @Mock
    private GitLabClient gitLabClient;

    @Mock
    private GitLabCommonHelper commonHelper;

    @InjectMocks
    private GitLabCommitsServiceImpl commitsService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner", "testRepo", "org",
                "https://gitlab.com/owner/repo.git");
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void testFetchCommits_Success() throws GitLabApiException, PlatformApiException {
        List<Commit> gitlabCommits = createMockCommits();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabCommits);
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = commitsService.fetchCommits(toolConfigId, gitUrlInfo, "main", "token", since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(gitLabClient).fetchCommits(eq("org"), eq("testRepo"), eq("main"), eq("token"), any(), any(), anyString());
    }

    @Test
    void testFetchCommits_GitLabApiException() throws GitLabApiException {
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new GitLabApiException("API error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> commitsService.fetchCommits(toolConfigId, gitUrlInfo, "main", "token", since, until));

        assertEquals("GitLab", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch commits from GitLab"));
    }

    @Test
    void testFetchCommits_EmptyList() throws GitLabApiException, PlatformApiException {
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = commitsService.fetchCommits(toolConfigId, gitUrlInfo, "main", "token", since, until);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPlatformName() {
        assertEquals("GitLab", commitsService.getPlatformName());
    }

    @Test
    void testSetAndClearRepositoryUrlContext() {
        commitsService.setRepositoryUrlContext("https://gitlab.example.com/repo");
        commitsService.clearRepositoryUrlContext();
        // No exception means success
        assertTrue(true);
    }

    private List<Commit> createMockCommits() {
        List<Commit> commits = new ArrayList<>();
        commits.add(createMockCommit("sha1", "Test commit 1"));
        commits.add(createMockCommit("sha2", "Test commit 2"));
        return commits;
    }

    private Commit createMockCommit(String id, String message) {
        Commit commit = mock(Commit.class);
        when(commit.getId()).thenReturn(id);
        when(commit.getMessage()).thenReturn(message);
        when(commit.getCreatedAt()).thenReturn(new Date());
        when(commit.getAuthorName()).thenReturn("author");
        when(commit.getParentIds()).thenReturn(List.of("parent1"));
        return commit;
    }
}
