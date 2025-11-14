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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloudBitBucketParserTest {

    private CloudBitBucketParser parser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        parser = new CloudBitBucketParser();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testParseDiffToFileChanges_EmptyDiff() {
        List<ScmCommits.FileChange> result = parser.parseDiffToFileChanges("");
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseDiffToFileChanges_NullDiff() {
        List<ScmCommits.FileChange> result = parser.parseDiffToFileChanges(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseDiffToFileChanges_ValidDiff() {
        String diff = "diff --git a/file.txt b/file.txt\n@@ -1,3 +1,4 @@\n+added line\n-removed line\n context line\n";
        List<ScmCommits.FileChange> result = parser.parseDiffToFileChanges(diff);
        assertEquals(1, result.size());
        assertEquals("file.txt", result.get(0).getFilePath());
    }

    @Test
    void testParsePRDiffToFileChanges_EmptyDiff() {
        ScmMergeRequests.PullRequestStats result = parser.parsePRDiffToFileChanges("");
        assertEquals(0, result.getAddedLines());
    }

    @Test
    void testParsePullRequestNode_ValidNode() throws Exception {
        String prJson = "{\"id\":1,\"title\":\"Test PR\",\"state\":\"OPEN\",\"created_on\":\"2024-01-01T00:00:00Z\",\"updated_on\":\"2024-01-02T00:00:00Z\",\"author\":{\"display_name\":\"User\"},\"source\":{\"branch\":{\"name\":\"feature\"}},\"destination\":{\"branch\":{\"name\":\"main\"}},\"links\":{\"self\":{\"href\":\"https://bitbucket.org/owner/repo/pull-requests/1\"}}}";
        JsonNode node = objectMapper.readTree(prJson);
        BitbucketClient.BitbucketPullRequest result = parser.parsePullRequestNode(node);
        assertEquals(1L, result.getId());
    }

    @Test
    void testParseCommitNode_ValidNode() throws Exception {
        String commitJson = "{\"hash\":\"abc123\",\"date\":\"2024-01-01T00:00:00Z\",\"message\":\"Test commit\",\"author\":{\"raw\":\"User <user@example.com>\"}}";
        JsonNode node = objectMapper.readTree(commitJson);
        BitbucketClient.BitbucketCommit result = parser.parseCommitNode(node, true);
        assertEquals("abc123", result.getHash());
    }

    @Test
    void testParseRepositoryData_ValidNode() throws Exception {
        String repoJson = "{\"name\":\"test-repo\",\"updated_on\":\"2024-01-01T00:00:00+00:00\",\"links\":{\"html\":{\"href\":\"https://bitbucket.org/owner/test-repo\"}}}";
        JsonNode node = objectMapper.readTree(repoJson);
        ScmRepos result = parser.parseRepositoryData(node, LocalDateTime.of(2023, 1, 1, 0, 0));
        assertNotNull(result);
    }

    @Test
    void testParseRepositoryBranchData_ValidNode() throws Exception {
        String branchJson = "{\"name\":\"main\",\"target\":{\"date\":\"2024-01-01T00:00:00+00:00\"}}";
        JsonNode node = objectMapper.readTree(branchJson);
        ScmBranch result = parser.parseRepositoryBranchData(null, node, null, null, LocalDateTime.of(2023, 1, 1, 0, 0));
        assertNotNull(result);
    }
}
