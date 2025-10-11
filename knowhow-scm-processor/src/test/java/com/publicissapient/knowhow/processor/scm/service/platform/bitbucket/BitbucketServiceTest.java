package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.wrapper.impl.CloudBitBucketParser;
import com.publicissapient.knowhow.processor.scm.util.wrapper.impl.ServerBitbucketParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class BitbucketServiceTest {

	@Mock
	private BitbucketClient bitbucketClient;

	@Mock
	private RateLimitService rateLimitService;

	@Mock
	private GitUrlParser gitUrlParser;

	@Mock
	private BitbucketParser bitbucketParser;

	@InjectMocks
	private BitbucketService bitbucketService;

	private String toolConfigId;
	private GitUrlParser.GitUrlInfo gitUrlInfo;
	private String token;
	private LocalDateTime since;
	private LocalDateTime until;

    String pullRequestDiff = """
			diff --git a/test b/test
			index 813b7e9d..9d388c2b 100644
			--- a/test
			+++ b/test
			@@ -3536,14 +3536,19 @@ def test:
			 def get_author_of_repository(repository_id):
			     test
			-    test
			+    test

			+    test
			-        test

			@@ -3551,13 +3556,21 @@ test

			-        test
			+        test

			""";

	String commitDiff = """
			diff --git a/test b/test
			index 54588f91..f584b81e 100644
			--- a/test
			+++ b/dtest

			 test

			+# test git	
			+# (Optional) Set this if you want to suppress future GitPython warnings
			+test

			diff --git a/docker/knowhow/Dockerfile b/docker/knowhow/Dockerfile
			index 83d2d806..105f41fb 100644
			--- a/docker/knowhow/Dockerfile
			+++ b/docker/knowhow/Dockerfile
			@@ -78,6 +78,15 @@ test
			 test
			 test

			+# Install git
			+test \\
			+    test \\
			+    test \\

			 COPY test test
			diff --git a/test b/test
			index b2b54455..53e83abd 100644
			--- a/test
			+++ b/test
			@@ -75,6 +75,15 @@ test

			+# git git
			+afvsfr \\
			+abc
			+
			 abc
			""";

	String serverBitbucketCommitDiff = """
			{
			    "fromHash": null,
			    "toHash": "b95e776e69c08c31b08ac66b56ed697fa4db918b",
			    "contextLines": 10,
			    "whitespace": "SHOW",
			    "diffs": [
			        {
			            "source": {
			                "components": [
			                    "src"
			                ],
			                "parent": "src",
			                "name": "test.java",
			                "extension": "java",
			                "toString": "src/test.java"
			            },
			            "destination": {
			                "components": [
			                    "src",
			                    "test.java"
			                ],
			                "parent": "src/",
			                "name": "EntityEventAcknowledgementDTO.java",
			                "extension": "java",
			                "toString": "src/test.java"
			            },
			            "hunks": [
			                {
			                    "context": "test{",
			                    "sourceLine": 22,
			                    "sourceSpan": 13,
			                    "destinationLine": 22,
			                    "destinationSpan": 13,
			                    "segments": [
			                        {
			                            "type": "CONTEXT",
			                            "lines": [
			                                {
			                                    "source": 22,
			                                    "destination": 22,
			                                    "line": "test",
			                                    "truncated": false
			                                },
			                            ],
			                            "truncated": false
			                        },
			                        {
			                            "type": "REMOVED",
			                            "lines": [
			                                {
			                                    "source": 32,
			                                    "destination": 32,
			                                    "line": "test",
			                                    "truncated": false
			                                }
			                            ],
			                            "truncated": false
			                        },
			                        {
			                            "type": "ADDED",
			                            "lines": [
			                                {
			                                    "source": 33,
			                                    "destination": 32,
			                                    "line": "test;",
			                                    "truncated": false
			                                }
			                            ],
			                            "truncated": false
			                        },
			                        {
			                            "type": "CONTEXT",
			                            "lines": [
			                                {
			                                    "source": 33,
			                                    "destination": 33,
			                                    "line": "\\tprivate ConsumerServiceType updatedFrom;",
			                                    "truncated": false
			                                },
			                                {
			                                    "source": 34,
			                                    "destination": 34,
			                                    "line": "}",
			                                    "truncated": false
			                                }
			                            ],
			                            "truncated": false
			                        }
			                    ],
			                    "truncated": false
			                }
			            ],
			            "truncated": false
			        }
			    ],
			    "truncated": false
			}""";

	String serverBitbucketPRDiff = """
			{
			    "fromHash": "2e7662c384943869c59fc2caccf226b345c46261",
			    "toHash": "540d08dfe00df25099bd02ea1963d863fb3487b6",
			    "contextLines": 10,
			    "whitespace": "SHOW",
			    "diffs": [
			        {
			            "source": {
			                "components": [
			                    "build.gradle"
			                ],
			                "parent": "",
			                "name": "build.gradle",
			                "extension": "gradle",
			                "toString": "build.gradle"
			            },
			            "destination": {
			                "components": [
			                    "build.gradle"
			                ],
			                "parent": "",
			                "name": "build.gradle",
			                "extension": "gradle",
			                "toString": "build.gradle"
			            },
			            "hunks": [
			                {
			                    "context": "jar {",
			                    "sourceLine": 38,
			                    "sourceSpan": 22,
			                    "destinationLine": 38,
			                    "destinationSpan": 21,
			                    "segments": [
			                        {
			                            "type": "CONTEXT",
			                            "lines": [
			                                {
			                                    "source": 38,
			                                    "destination": 38,
			                                    "line": "    enabled = false",
			                                    "truncated": false
			                                }
			                            ],
			                            "truncated": false
			                        },
			                        {
			                            "type": "REMOVED",
			                            "lines": [
			                                {
			                                    "source": 48,
			                                    "destination": 48,
			                                    "line": "        url 'http://18.194.46.189:8081/repository/TENG-maven/'",
			                                    "truncated": false
			                                }
			                            ],
			                            "truncated": false
			                        },
			                        {
			                            "type": "ADDED",
			                            "lines": [
			                                {
			                                    "source": 50,
			                                    "destination": 48,
			                                    "line": " test'",
			                                    "truncated": false
			                                }
			                            ],
			                            "truncated": false
			                        },
			                        {
			                            "type": "CONTEXT",
			                            "lines": [
			                                {
			                                    "source": 50,
			                                    "destination": 49,
			                                    "line": "        test {",
			                                    "truncated": false
			                                }
			                            ],
			                            "truncated": false
			                        }
			                    ],
			                    "truncated": false
			                }
			            ],
			            "truncated": false
			        }
			    ],
			    "truncated": false
			}""";

	@BeforeEach
	void setUp() {
		toolConfigId = new ObjectId().toString();
		gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "testowner", "testrepo", "testorg",
				"https://bitbucket.org/testowner/testrepo.git");
		token = "testuser:testpassword";
		since = LocalDateTime.now().minusDays(7);
		until = LocalDateTime.now();
	}

	@Test
	void testSetRepositoryUrlContext() {
		// Act
		bitbucketService.setRepositoryUrlContext("https://test.com/repo");

		// Assert - verify no exception is thrown
		assertDoesNotThrow(() -> bitbucketService.setRepositoryUrlContext("https://test.com/repo"));
	}

	@Test
	void testClearRepositoryUrlContext() {
		// Arrange
		bitbucketService.setRepositoryUrlContext("https://test.com/repo");

		// Act
		bitbucketService.clearRepositoryUrlContext();

		// Assert - verify no exception is thrown
		assertDoesNotThrow(() -> bitbucketService.clearRepositoryUrlContext());
	}

	@Test
	void testFetchCommits_Success() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketCommit> mockCommits = createMockBitbucketCommits();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);

		// Act
		List<ScmCommits> result = bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("commit1", result.get(0).getSha());
		assertEquals("Test commit 1", result.get(0).getCommitMessage());
		assertEquals("testrepo", result.get(0).getRepoSlug());
		verify(bitbucketClient).fetchCommits("testowner", "testrepo", "main", "testuser", "testpassword", since,
				"https://bitbucket.org/testowner/testrepo.git");
	}

	@Test
	void testFetchCommits_WithRepositoryUrlContext() throws PlatformApiException {
		// Arrange
		String contextUrl = "https://tools.publicis.sapient.com/bitbucket/projects/testorg/repos/testRepo.git";
		gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "testorg", "testrepo", "testorg",
				contextUrl);
		bitbucketService.setRepositoryUrlContext(contextUrl);

		List<BitbucketClient.BitbucketCommit> mockCommits = createMockBitbucketCommits();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString())).thenReturn(serverBitbucketCommitDiff);
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(new ServerBitbucketParser());

		// Act
		List<ScmCommits> result = bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		verify(bitbucketClient).fetchCommits("testorg", "testrepo", "main", "testuser", "testpassword", since,
				contextUrl);

		// Cleanup
		bitbucketService.clearRepositoryUrlContext();
	}

	@Test
	void testPullRequests_WithRepositoryUrlContext() throws PlatformApiException {
		// Arrange
		String contextUrl = "https://tools..com/bitbucket/projects/testorg/repos/testRepo.git";
		gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "testorg", "testrepo", "testorg",
				contextUrl);
		bitbucketService.setRepositoryUrlContext(contextUrl);

		List<BitbucketClient.BitbucketPullRequest> mockPrs = createMockBitbucketPullRequests();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPrs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), anyLong(), anyString(), anyString(),
				anyString())).thenReturn(serverBitbucketPRDiff);
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(new ServerBitbucketParser());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		verify(bitbucketClient).fetchPullRequests("testorg", "testrepo", "main", "testuser", "testpassword", since,
				contextUrl);

		// Cleanup
		bitbucketService.clearRepositoryUrlContext();
	}

	@Test
	void testFetchCommits_PlatformApiException() throws PlatformApiException {
		// Arrange
		PlatformApiException exception = new PlatformApiException("Bitbucket", "API Error");
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenThrow(exception);

		// Act & Assert
		PlatformApiException thrown = assertThrows(PlatformApiException.class,
				() -> bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until));
		assertEquals("API Error", thrown.getMessage());
		assertEquals("Bitbucket", thrown.getPlatform());
	}

	@Test
	void testFetchCommits_GenericException() throws PlatformApiException {
		// Arrange
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenThrow(new RuntimeException("Network error"));

		// Act & Assert
		PlatformApiException thrown = assertThrows(PlatformApiException.class,
				() -> bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until));
		assertTrue(thrown.getMessage().contains("Failed to fetch commits from Bitbucket"));
	}

	@Test
	void testFetchCommits_WithStatsAndNoFileChanges() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketCommit> mockCommits = createMockBitbucketCommitsWithStats();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString())).thenThrow(new RuntimeException("Diff error"));
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);

		// Act
		List<ScmCommits> result = bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(10, result.get(0).getAddedLines());
		assertEquals(5, result.get(0).getRemovedLines());
		assertEquals(15, result.get(0).getChangedLines());
	}

	@Test
	void testFetchCommits_WithoutStatsButWithFileChanges() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketCommit> mockCommits = createMockBitbucketCommitsWithoutStats();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString())).thenReturn(commitDiff);
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(new CloudBitBucketParser());

		List<ScmCommits.FileChange> fileChanges = createMockFileChangesWithLines();
		// when(bitbucketParser.parseDiffToFileChanges(anyString())).thenReturn(fileChanges);

		// Act
		List<ScmCommits> result = bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(11, result.get(0).getAddedLines()); // Sum from file changes
		assertEquals(0, result.get(0).getRemovedLines()); // Sum from file changes
	}

	@Test
	void testFetchMergeRequests_Success() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockBitbucketPullRequests();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn(pullRequestDiff);
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("1", result.get(0).getExternalId());
		assertEquals("Test PR 1", result.get(0).getTitle());
		assertEquals("testrepo", result.get(0).getRepoSlug());
	}

	@Test
	void testFetchMergeRequests_Exception() throws PlatformApiException {
		// Arrange
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenThrow(new RuntimeException("API Error"));

		// Act & Assert
		PlatformApiException thrown = assertThrows(PlatformApiException.class,
				() -> bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until));
		assertTrue(thrown.getMessage().contains("Failed to fetch pull requests from Bitbucket"));
	}

	@Test
	void testGetPlatformName() {
		// Act
		String platformName = bitbucketService.getPlatformName();

		// Assert
		assertEquals("Bitbucket", platformName);
	}

	@Test
	void testConvertToCommit_WithMergeCommit() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketCommit> mockCommits = createMockMergeCommit();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);

		// Act
		List<ScmCommits> result = bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertTrue(result.get(0).getIsMergeCommit());
	}

	@Test
	void testConvertToCommit_WithInvalidDate() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketCommit> mockCommits = createMockCommitWithInvalidDate();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString())).thenReturn(commitDiff);
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(new CloudBitBucketParser());

		// Act
		List<ScmCommits> result = bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		// Should handle invalid date gracefully
	}

	@Test
	void testConvertToMergeRequest_OpenState() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockOpenPullRequest();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("OPEN", result.get(0).getState());
		assertTrue(result.get(0).isOpen());
		assertFalse(result.get(0).isClosed());
	}

	@Test
	void testConvertToMergeRequest_MergedState() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockMergedPullRequest();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("MERGED", result.get(0).getState());
		assertTrue(result.get(0).isClosed());
		assertNotNull(result.get(0).getMergedAt());
	}

	@Test
	void testConvertToMergeRequest_ClosedState() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockClosedPullRequest();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("CLOSED", result.get(0).getState());
		assertTrue(result.get(0).isClosed());
	}

	@Test
	void testConvertToMergeRequest_WithReviewers() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockPullRequestWithReviewers();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertNotNull(result.get(0).getReviewerUserIds());
		assertEquals(2, result.get(0).getReviewerUserIds().size());
	}

	@Test
	void testConvertToMergeRequest_DiffFetchException() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockBitbucketPullRequests();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenThrow(new RuntimeException("Diff error"));

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(0, result.get(0).getAddedLines());
		assertEquals(0, result.get(0).getRemovedLines());
		assertEquals(0, result.get(0).getFilesChanged());
	}

	@Test
	void testConvertPullRequestState_AllStates() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockPullRequestsWithDifferentStates();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(4, result.size());
		// Verify different states are handled correctly
		assertTrue(result.stream().anyMatch(mr -> "OPEN".equals(mr.getState())));
		assertTrue(result.stream().anyMatch(mr -> "MERGED".equals(mr.getState())));
		assertTrue(result.stream().anyMatch(mr -> "CLOSED".equals(mr.getState())));
	}

	@Test
	void testExtractCredentials_InvalidToken() {
		// Arrange
		String invalidToken = "invalidtoken";

		// Act & Assert
		assertThrows(PlatformApiException.class,
				() -> bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", invalidToken, since, until));
	}

	@Test
	void testExtractCredentials_NullToken() {
		// Act & Assert
		assertThrows(PlatformApiException.class,
				() -> bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", null, since, until));
	}

	@Test
	void testConvertToCommit_ConversionException() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketCommit> mockCommits = createMockCommitThatThrowsException();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockCommits);

		// Act & Assert
		assertThrows(RuntimeException.class,
				() -> bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until));
	}

	@Test
	void testConvertToMergeRequest_ConversionException() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockPRThatThrowsException();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);

		// Act & Assert
		assertThrows(RuntimeException.class,
				() -> bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until));
	}

	@Test
	void testFetchCommits_WithNullAuthor() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketCommit> mockCommits = createMockCommitWithNullAuthor();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		// when(bitbucketParser.parseDiffToFileChanges(anyString())).thenReturn(createMockFileChanges());

		// Act
		List<ScmCommits> result = bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertNull(result.get(0).getCommitAuthor());
	}

	@Test
	void testFetchMergeRequests_WithNullAuthor() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockPullRequestWithNullAuthor();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertNull(result.get(0).getAuthorId());
	}

	@Test
	void testConvertToMergeRequest_WithPickedUpDate() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockPullRequestWithPickedUpDate();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertNotNull(result.get(0).getPickedForReviewOn());
	}

	@Test
	void testConvertToMergeRequest_WithInvalidPickedUpDate() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockPullRequestWithInvalidPickedUpDate();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		// Should handle invalid date gracefully
	}

	@Test
	void testConvertToMergeRequest_WithInvalidDates() throws PlatformApiException {
		// Arrange
		List<BitbucketClient.BitbucketPullRequest> mockPRs = createMockPullRequestWithInvalidDates();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(mockPRs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), any(), anyString(), anyString(),
				anyString())).thenReturn("mock diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createMockPRStats());

		// Act
		List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token,
				since, until);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		// Should handle invalid dates gracefully
	}

	// Helper methods to create mock objects

	private List<BitbucketClient.BitbucketCommit> createMockBitbucketCommits() {
		List<BitbucketClient.BitbucketCommit> commits = new ArrayList<>();

		BitbucketClient.BitbucketCommit commit1 = new BitbucketClient.BitbucketCommit();
		commit1.setHash("commit1");
		commit1.setMessage("Test commit 1");
		commit1.setDate("2023-01-01T10:00:00+00:00");

		BitbucketClient.BitbucketUser author1 = new BitbucketClient.BitbucketUser();
		author1.setName("Test Author");
		author1.setEmailAddress("test@example.com");
		commit1.setAuthor(author1);

		commit1.setParents(Arrays.asList("parent1"));

		BitbucketClient.BitbucketCommit commit2 = new BitbucketClient.BitbucketCommit();
		commit2.setHash("commit2");
		commit2.setMessage("Test commit 2");
		commit2.setDate("2023-01-02T10:00:00+00:00");
		commit2.setAuthor(author1);
		commit2.setParents(Arrays.asList("parent2"));

		commits.add(commit1);
		commits.add(commit2);

		return commits;
	}

	private List<BitbucketClient.BitbucketCommit> createMockBitbucketCommitsWithStats() {
		List<BitbucketClient.BitbucketCommit> commits = new ArrayList<>();

		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();
		commit.setHash("commit1");
		commit.setMessage("Test commit with stats");
		commit.setDate("2023-01-01T10:00:00+00:00");

		BitbucketClient.BitbucketCommitStats stats = new BitbucketClient.BitbucketCommitStats();
		stats.setAdditions(10);
		stats.setDeletions(5);
		stats.setTotal(15);
		commit.setStats(stats);

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Test Author");
		commit.setAuthor(author);

		commits.add(commit);
		return commits;
	}

	private List<BitbucketClient.BitbucketCommit> createMockBitbucketCommitsWithoutStats() {
		List<BitbucketClient.BitbucketCommit> commits = new ArrayList<>();

		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();
		commit.setHash("commit1");
		commit.setMessage("Test commit without stats");
		commit.setDate("2023-01-01T10:00:00+00:00");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Test Author");
		commit.setAuthor(author);

		commits.add(commit);
		return commits;
	}

	private List<BitbucketClient.BitbucketCommit> createMockMergeCommit() {
		List<BitbucketClient.BitbucketCommit> commits = new ArrayList<>();

		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();
		commit.setHash("merge-commit");
		commit.setMessage("Merge commit");
		commit.setDate("2023-01-01T10:00:00+00:00");
		commit.setParents(Arrays.asList("parent1", "parent2")); // Multiple parents = merge commit

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Test Author");
		commit.setAuthor(author);

		commits.add(commit);
		return commits;
	}

	private List<BitbucketClient.BitbucketCommit> createMockCommitWithInvalidDate() {
		List<BitbucketClient.BitbucketCommit> commits = new ArrayList<>();

		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();
		commit.setHash("commit1");
		commit.setMessage("Test commit");
		commit.setDate("invalid-date-format");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Test Author");
		commit.setAuthor(author);

		commits.add(commit);
		return commits;
	}

	private List<BitbucketClient.BitbucketCommit> createMockCommitWithNullAuthor() {
		List<BitbucketClient.BitbucketCommit> commits = new ArrayList<>();

		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();
		commit.setHash("commit1");
		commit.setMessage("Test commit");
		commit.setDate("2023-01-01T10:00:00+00:00");
		commit.setAuthor(null);

		commits.add(commit);
		return commits;
	}

	private List<BitbucketClient.BitbucketCommit> createMockCommitThatThrowsException() {
		// Create a commit that will cause an exception during conversion
		BitbucketClient.BitbucketCommit commit = mock(BitbucketClient.BitbucketCommit.class);
		when(commit.getHash()).thenThrow(new RuntimeException("Conversion error"));

		return Arrays.asList(commit);
	}

	private List<ScmCommits.FileChange> createMockFileChanges() {
		List<ScmCommits.FileChange> fileChanges = new ArrayList<>();

		ScmCommits.FileChange change1 = ScmCommits.FileChange.builder().filePath("src/main/java/Test.java")
				.changeType("MODIFIED").addedLines(5).removedLines(2).isBinary(false).build();

		fileChanges.add(change1);
		return fileChanges;
	}

	private List<ScmCommits.FileChange> createMockFileChangesWithLines() {
		List<ScmCommits.FileChange> fileChanges = new ArrayList<>();

		ScmCommits.FileChange change1 = ScmCommits.FileChange.builder().filePath("file1.java").addedLines(5)
				.removedLines(2).build();

		ScmCommits.FileChange change2 = ScmCommits.FileChange.builder().filePath("file2.java").addedLines(3)
				.removedLines(1).build();

		fileChanges.add(change1);
		fileChanges.add(change2);
		return fileChanges;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockBitbucketPullRequests() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr1 = new BitbucketClient.BitbucketPullRequest();
		pr1.setId(1L);
		pr1.setTitle("Test PR 1");
		pr1.setDescription("Test description 1");
		pr1.setState("OPEN");
		pr1.setCreatedOn("2023-01-01T10:00:00+00:00");
		pr1.setUpdatedOn("2023-01-01T11:00:00+00:00");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("PR Author");
		pr1.setAuthor(author);

		BitbucketClient.BitbucketBranch source = new BitbucketClient.BitbucketBranch();
		BitbucketClient.BbBranch sourceBranch = new BitbucketClient.BbBranch();
		sourceBranch.setName("feature");
		source.setBranch(sourceBranch);
		pr1.setSource(source);

		BitbucketClient.BitbucketBranch destination = new BitbucketClient.BitbucketBranch();
		BitbucketClient.BbBranch destBranch = new BitbucketClient.BbBranch();
		destBranch.setName("main");
		destination.setBranch(destBranch);
		pr1.setDestination(destination);

		BitbucketClient.BitbucketPullRequest pr2 = new BitbucketClient.BitbucketPullRequest();
		pr2.setId(2L);
		pr2.setTitle("Test PR 2");
		pr2.setDescription("Test description 2");
		pr2.setState("MERGED");
		pr2.setCreatedOn("2023-01-02T10:00:00+00:00");
		pr2.setUpdatedOn("2023-01-02T11:00:00+00:00");
		pr2.setClosedOn("2023-01-02T12:00:00+00:00");
		pr2.setAuthor(author);
		pr2.setSource(source);
		pr2.setDestination(destination);

		prs.add(pr1);
		prs.add(pr2);

		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockOpenPullRequest() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("Open PR");
		pr.setState("OPEN");
		pr.setCreatedOn("2023-01-01T10:00:00+00:00");
		pr.setUpdatedOn("2023-01-01T11:00:00+00:00");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Author");
		pr.setAuthor(author);

		prs.add(pr);
		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockMergedPullRequest() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("Merged PR");
		pr.setState("MERGED");
		pr.setCreatedOn("2023-01-01T10:00:00+00:00");
		pr.setUpdatedOn("2023-01-01T11:00:00+00:00");
		pr.setClosedOn("2023-01-01T12:00:00+00:00");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Author");
		pr.setAuthor(author);

		prs.add(pr);
		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockClosedPullRequest() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("Closed PR");
		pr.setState("DECLINED");
		pr.setCreatedOn("2023-01-01T10:00:00+00:00");
		pr.setUpdatedOn("2023-01-01T11:00:00+00:00");
		pr.setClosedOn("2023-01-01T12:00:00+00:00");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Author");
		pr.setAuthor(author);

		prs.add(pr);
		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockPullRequestWithReviewers() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("PR with reviewers");
		pr.setState("OPEN");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Author");
		pr.setAuthor(author);

		List<BitbucketClient.BitbucketUser> reviewers = new ArrayList<>();
		BitbucketClient.BitbucketUser reviewer1 = new BitbucketClient.BitbucketUser();
		reviewer1.setName("Reviewer1");
		BitbucketClient.BitbucketUser reviewer2 = new BitbucketClient.BitbucketUser();
		reviewer2.setName("Reviewer2");
		reviewers.add(reviewer1);
		reviewers.add(reviewer2);
		pr.setReviewers(reviewers);

		prs.add(pr);
		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockPullRequestWithNullAuthor() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("PR with null author");
		pr.setState("OPEN");
		pr.setAuthor(null);

		prs.add(pr);
		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockPullRequestWithPickedUpDate() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("PR with picked up date");
		pr.setState("OPEN");
		pr.setPickedUpOn("2023-01-01T10:00:00+00:00");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Author");
		pr.setAuthor(author);

		prs.add(pr);
		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockPullRequestWithInvalidPickedUpDate() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("PR with invalid picked up date");
		pr.setState("OPEN");
		pr.setPickedUpOn("invalid-date");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Author");
		pr.setAuthor(author);

		prs.add(pr);
		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockPullRequestWithInvalidDates() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("PR with invalid dates");
		pr.setState("OPEN");
		pr.setCreatedOn("invalid-date");
		pr.setUpdatedOn("invalid-date");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Author");
		pr.setAuthor(author);

		prs.add(pr);
		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockPullRequestsWithDifferentStates() {
		List<BitbucketClient.BitbucketPullRequest> prs = new ArrayList<>();

		// Open PR
		BitbucketClient.BitbucketPullRequest openPR = new BitbucketClient.BitbucketPullRequest();
		openPR.setId(1L);
		openPR.setTitle("Open PR");
		openPR.setState("OPEN");
		openPR.setCreatedOn("2023-01-01T10:00:00+00:00");

		// Merged PR
		BitbucketClient.BitbucketPullRequest mergedPR = new BitbucketClient.BitbucketPullRequest();
		mergedPR.setId(2L);
		mergedPR.setTitle("Merged PR");
		mergedPR.setState("MERGED");
		mergedPR.setCreatedOn("2023-01-01T10:00:00+00:00");
		mergedPR.setClosedOn("2023-01-01T12:00:00+00:00");

		// Declined PR
		BitbucketClient.BitbucketPullRequest declinedPR = new BitbucketClient.BitbucketPullRequest();
		declinedPR.setId(3L);
		declinedPR.setTitle("Declined PR");
		declinedPR.setState("DECLINED");
		declinedPR.setCreatedOn("2023-01-01T10:00:00+00:00");
		declinedPR.setClosedOn("2023-01-01T12:00:00+00:00");

		// Superseded PR
		BitbucketClient.BitbucketPullRequest supersededPR = new BitbucketClient.BitbucketPullRequest();
		supersededPR.setId(4L);
		supersededPR.setTitle("Superseded PR");
		supersededPR.setState("SUPERSEDED");
		supersededPR.setCreatedOn("2023-01-01T10:00:00+00:00");
		supersededPR.setClosedOn("2023-01-01T12:00:00+00:00");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setName("Author");

		openPR.setAuthor(author);
		mergedPR.setAuthor(author);
		declinedPR.setAuthor(author);
		supersededPR.setAuthor(author);

		prs.add(openPR);
		prs.add(mergedPR);
		prs.add(declinedPR);
		prs.add(supersededPR);

		return prs;
	}

	private List<BitbucketClient.BitbucketPullRequest> createMockPRThatThrowsException() {
		BitbucketClient.BitbucketPullRequest pr = mock(BitbucketClient.BitbucketPullRequest.class);
		when(pr.getId()).thenThrow(new RuntimeException("Conversion error"));

		return Arrays.asList(pr);
	}

	private ScmMergeRequests.PullRequestStats createMockPRStats() {
		return new ScmMergeRequests.PullRequestStats(10, 5, 3);

	}
}