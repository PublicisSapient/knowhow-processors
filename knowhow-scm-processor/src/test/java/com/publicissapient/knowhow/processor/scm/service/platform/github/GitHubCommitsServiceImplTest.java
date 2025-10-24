package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHUser;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubCommitsServiceImplTest {

    @Mock
    private GitHubClient gitHubClient;

    @Mock
    private GitHubCommonHelper commonHelper;

    @InjectMocks
    private GitHubCommitsServiceImpl commitsService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org",
                "https://github.com/owner/repo.git");
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void testFetchCommits_Success() throws IOException, PlatformApiException {
        List<GHCommit> ghCommits = createMockCommits();
        when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(ghCommits);

        List<ScmCommits> result = commitsService.fetchCommits(toolConfigId, gitUrlInfo, "main", "token", since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(gitHubClient).fetchCommits("org", "testRepo", "main", "token", since, until);
    }

    @Test
    void testFetchCommits_IOException() throws IOException {
        when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new IOException("Network error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> commitsService.fetchCommits(toolConfigId, gitUrlInfo, "main", "token", since, until));
        
        assertEquals("GitHub", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch commits from GitHub"));
    }

    @Test
    void testFetchCommits_EmptyList() throws IOException, PlatformApiException {
        when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = commitsService.fetchCommits(toolConfigId, gitUrlInfo, "main", "token", since, until);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPlatformName() {
        assertEquals("GitHub", commitsService.getPlatformName());
    }

    private List<GHCommit> createMockCommits() throws IOException {
        List<GHCommit> commits = new ArrayList<>();
        commits.add(createMockCommit("sha1", "Test commit 1"));
        commits.add(createMockCommit("sha2", "Test commit 2"));
        return commits;
    }

    private GHCommit createMockCommit(String sha, String message) throws IOException {
        GHCommit commit = mock(GHCommit.class);
        when(commit.getSHA1()).thenReturn(sha);

        GHCommit.ShortInfo shortInfo = mock(GHCommit.ShortInfo.class);
        when(shortInfo.getMessage()).thenReturn(message);
        when(commit.getCommitShortInfo()).thenReturn(shortInfo);

        when(commit.getCommitDate()).thenReturn(new Date());

        GHUser author = mock(GHUser.class);
        when(author.getLogin()).thenReturn("authorLogin");
        when(commit.getAuthor()).thenReturn(author);

        when(commit.getParentSHA1s()).thenReturn(List.of("parent1"));
        when(commit.getFiles()).thenReturn(new ArrayList<>());

        return commit;
    }
}
