package com.publicissapient.knowhow.processor.scm.util.wrapper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ServerBitbucketParserTest {

    private ServerBitbucketParser parser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        parser = new ServerBitbucketParser();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testParseDiffToFileChanges_ValidDiff() throws Exception {
        String diffJson = "{\"diffs\":[{\"source\":{\"toString\":\"file.txt\"},\"hunks\":[{\"segments\":[{\"type\":\"ADDED\",\"lines\":[{\"destination\":1}]},{\"type\":\"REMOVED\",\"lines\":[{\"source\":1}]}]}]}]}";
        List<ScmCommits.FileChange> result = parser.parseDiffToFileChanges(diffJson);
        assertEquals(1, result.size());
    }

    @Test
    void testParsePRDiffToFileChanges_ValidDiff() throws Exception {
        String diffJson = "{\"diffs\":[{\"hunks\":[{\"segments\":[{\"type\":\"ADDED\",\"lines\":[{},{},{}]},{\"type\":\"REMOVED\",\"lines\":[{},{}]}]}]}]}";
        ScmMergeRequests.PullRequestStats result = parser.parsePRDiffToFileChanges(diffJson);
        assertEquals(3, result.getAddedLines());
        assertEquals(2, result.getRemovedLines());
    }

    @Test
    void testParsePullRequestNode_ValidNode() throws Exception {
        String prJson = "{\"id\":1,\"title\":\"Test PR\",\"state\":\"OPEN\",\"createdDate\":1609459200000,\"updatedDate\":1609545600000,\"author\":{\"user\":{\"name\":\"testuser\"}},\"fromRef\":{\"displayId\":\"feature\"},\"toRef\":{\"displayId\":\"main\"},\"links\":{\"self\":[{\"href\":\"http://server/projects/TEST/repos/repo/pull-requests/1\"}]}}";
        JsonNode node = objectMapper.readTree(prJson);
        BitbucketClient.BitbucketPullRequest result = parser.parsePullRequestNode(node);
        assertEquals(1L, result.getId());
    }

    @Test
    void testParseCommitNode_ValidNode() throws Exception {
        String commitJson = "{\"id\":\"abc123\",\"authorTimestamp\":1609459200000,\"message\":\"Test commit\",\"author\":{\"name\":\"Test User\",\"emailAddress\":\"test@example.com\"}}";
        JsonNode node = objectMapper.readTree(commitJson);
        BitbucketClient.BitbucketCommit result = parser.parseCommitNode(node, false);
        assertEquals("abc123", result.getHash());
    }

    @Test
    void testParseRepositoryData_ValidNode() throws Exception {
        String repoJson = "{\"name\":\"test-repo\",\"links\":{\"self\":[{\"href\":\"http://server/rest/api/1.0/projects/TEST/repos/test-repo\"}]}}";
        JsonNode node = objectMapper.readTree(repoJson);
        ScmRepos result = parser.parseRepositoryData(node, null);
        assertNotNull(result);
    }

    @Test
    void testParseRepositoryBranchData_ValidNode() throws Exception {
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String branchJson = "{\"displayId\":\"main\",\"latestCommit\":\"abc123\"}";
        long recentTimestamp = System.currentTimeMillis();
        String commitJson = "{\"authorTimestamp\":" + recentTimestamp + "}";
        
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(commitJson));
        
        JsonNode node = objectMapper.readTree(branchJson);
        ScmBranch result = parser.parseRepositoryBranchData(mockClient, node, "TEST", "repo", LocalDateTime.of(2023, 1, 1, 0, 0));
        assertNotNull(result);
    }
}
