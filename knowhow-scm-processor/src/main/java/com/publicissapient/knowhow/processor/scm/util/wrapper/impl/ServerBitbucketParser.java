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

package com.publicissapient.knowhow.processor.scm.util.wrapper.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServerBitbucketParser implements BitbucketParser {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	// CHANGE: Extract constants to improve maintainability
	private static final String SEGMENT_TYPE_ADDED = "ADDED";
	private static final String SEGMENT_TYPE_REMOVED = "REMOVED";
	private static final String SEGMENT_TYPE_CONTEXT = "context";
	private static final String UNKNOWN_FILE = "Unknown File";
	private static final String SOURCE_FILE = "source";
	private static final long MILLIS_TO_SECONDS = 1000L;
	private static final int FIRST_ELEMENT_INDEX = 0;

	public List<ScmCommits.FileChange> parseDiffToFileChanges(String diffContent) {
		List<ScmCommits.FileChange> fileChanges = new ArrayList<>();
		try {
			JsonNode rootNode = objectMapper.readTree(diffContent);
			JsonNode diffs = rootNode.get("diffs");

			if (diffs != null && diffs.isArray()) {
				for (JsonNode diff : diffs) {
					// CHANGE: Extract method to process single diff
					ScmCommits.FileChange fileChange = processDiff(diff);
					fileChanges.add(fileChange);
				}
			}
		} catch (Exception e) {
			// CHANGE: Use proper logging instead of printStackTrace
			log.error("Error parsing diff content", e);
		}
		return fileChanges;
	}

	// CHANGE: Extract method to process single diff
	private ScmCommits.FileChange processDiff(JsonNode diff) {
		ScmCommits.FileChange fileChange = new ScmCommits.FileChange();
		DiffStats stats = new DiffStats();

		// Set file path
		fileChange.setFilePath(extractFileName(diff));

		// Process hunks
		JsonNode hunks = diff.get("hunks");
		if (hunks != null && hunks.isArray()) {
			for (JsonNode hunk : hunks) {
				processHunk(hunk, stats);
			}
		}

		fileChange.setAddedLines(stats.totalAddedLines);
		fileChange.setRemovedLines(stats.totalRemovedLines);
		fileChange.setChangedLineNumbers(new ArrayList<>(stats.changedLines));

		return fileChange;
	}

	// CHANGE: Extract helper class to reduce method parameters
	private static class DiffStats {
		int totalAddedLines = 0;
		int totalRemovedLines = 0;
		Set<Integer> changedLines = new HashSet<>();
	}

	// CHANGE: Extract method to get file name
	private String extractFileName(JsonNode diff) {
		JsonNode sourceNode = diff.get(SOURCE_FILE);
		return (sourceNode != null && sourceNode.get("toString") != null) ? sourceNode.get("toString").asText()
				: UNKNOWN_FILE;
	}

	// CHANGE: Extract method to process hunk
	private void processHunk(JsonNode hunk, DiffStats stats) {
		JsonNode segments = hunk.get("segments");
		if (segments != null && segments.isArray()) {
			for (JsonNode segment : segments) {
				processSegment(segment, stats);
			}
		}
	}

	// CHANGE: Extract method to process segment
	private void processSegment(JsonNode segment, DiffStats stats) {
		String type = segment.get("type").asText();
		if (SEGMENT_TYPE_CONTEXT.equalsIgnoreCase(type)) {
			return;
		}

		JsonNode lines = segment.get("lines");
		if (lines != null && lines.isArray()) {
			for (JsonNode line : lines) {
				processLine(line, type, stats);
			}
		}
	}

	// CHANGE: Extract method to process line
	private void processLine(JsonNode line, String type, DiffStats stats) {
		if (SEGMENT_TYPE_ADDED.equals(type)) {
			int lineNumber = getLineNumber(line, "destination", SOURCE_FILE);
			stats.totalAddedLines++;
			if (lineNumber != -1) {
				stats.changedLines.add(lineNumber);
			}
		} else if (SEGMENT_TYPE_REMOVED.equals(type)) {
			int lineNumber = getLineNumber(line, SOURCE_FILE, "destination");
			stats.totalRemovedLines++;
			if (lineNumber != -1) {
				stats.changedLines.add(lineNumber);
			}
		}
	}

	// CHANGE: Extract method to get line number with fallback
	private int getLineNumber(JsonNode line, String primaryField, String fallbackField) {
		JsonNode primaryNode = line.get(primaryField);
		if (primaryNode != null) {
			return primaryNode.asInt();
		}
		JsonNode fallbackNode = line.get(fallbackField);
		return fallbackNode != null ? fallbackNode.asInt() : -1;
	}

	@Override
	public ScmMergeRequests.PullRequestStats parsePRDiffToFileChanges(String diffContent) {
		try {
			JsonNode rootNode = objectMapper.readTree(diffContent);
			JsonNode diffs = rootNode.get("diffs");

			PRDiffStats stats = new PRDiffStats();

			if (diffs != null && diffs.isArray()) {
				for (JsonNode diff : diffs) {
					stats.changedFiles++;
					processPRDiff(diff, stats);
				}
			}

			return new ScmMergeRequests.PullRequestStats(stats.totalAddedLines, stats.totalRemovedLines,
					stats.changedFiles);
		} catch (Exception e) {
			log.error("Error parsing PR diff content", e);
			return new ScmMergeRequests.PullRequestStats(0, 0, 0);
		}
	}

	private static class PRDiffStats {
		int totalAddedLines = 0;
		int totalRemovedLines = 0;
		int changedFiles = 0;
	}

	private void processPRDiff(JsonNode diff, PRDiffStats stats) {
		JsonNode hunks = diff.get("hunks");
		if (hunks != null && hunks.isArray()) {
			for (JsonNode hunk : hunks) {
				processPRHunk(hunk, stats);
			}
		}
	}

	private void processPRHunk(JsonNode hunk, PRDiffStats stats) {
		JsonNode segments = hunk.get("segments");
		if (segments != null && segments.isArray()) {
			for (JsonNode segment : segments) {
				processPRSegment(segment, stats);
			}
		}
	}

	private void processPRSegment(JsonNode segment, PRDiffStats stats) {
		String type = segment.get("type").asText();
		if (SEGMENT_TYPE_CONTEXT.equalsIgnoreCase(type)) {
			return;
		}

		JsonNode lines = segment.get("lines");
		if (lines != null && lines.isArray()) {
			if (SEGMENT_TYPE_ADDED.equals(type)) {
				stats.totalAddedLines += lines.size();
			} else if (SEGMENT_TYPE_REMOVED.equals(type)) {
				stats.totalRemovedLines += lines.size();
			}
		}
	}

	public BitbucketClient.BitbucketPullRequest parsePullRequestNode(JsonNode prNode) {
		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();

		parseBasicPRInfo(prNode, pr);
		parsePRDates(prNode, pr);
		parsePRMergeInfo(prNode, pr);
		parsePRAuthorAndReviewers(prNode, pr);
		parsePRBranches(prNode, pr);
		parsePRLinks(prNode, pr);

		return pr;
	}

	private void parseBasicPRInfo(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode idNode = prNode.get("id");
		if (idNode != null) {
			pr.setId(idNode.asLong());
		}

		JsonNode titleNode = prNode.get("title");
		if (titleNode != null) {
			pr.setTitle(titleNode.asText());
		}

		JsonNode descriptionNode = prNode.get("description");
		if (descriptionNode != null) {
			pr.setDescription(descriptionNode.asText());
		}

		JsonNode stateNode = prNode.get("state");
		if (stateNode != null) {
			pr.setState(stateNode.asText());
		}
	}

	private void parsePRDates(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode createdDateNode = prNode.get("createdDate");
		if (createdDateNode != null) {
			pr.setCreatedOn(convertTimestampToISO(createdDateNode.asLong()));
		}

		JsonNode closedDateNode = prNode.get("closedDate");
		if (closedDateNode != null) {
			pr.setClosedOn(convertTimestampToISO(closedDateNode.asLong()));
		}

		JsonNode updatedDateNode = prNode.get("updatedDate");
		if (updatedDateNode != null) {
			pr.setUpdatedOn(convertTimestampToISO(updatedDateNode.asLong()));
		}
	}

	private String convertTimestampToISO(long timestamp) {
		LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / MILLIS_TO_SECONDS, 0, ZoneOffset.UTC);
		return dateTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	private void parsePRMergeInfo(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode mergeCommitNode = prNode.get("mergeCommit");
		if (mergeCommitNode != null) {
			BitbucketClient.BitbucketCommit mergeCommit = objectMapper.convertValue(mergeCommitNode,
					BitbucketClient.BitbucketCommit.class);
			pr.setMergeCommit(mergeCommit);
		}

		JsonNode closeSourceBranchNode = prNode.get("closeSourceBranch");
		if (closeSourceBranchNode != null) {
			pr.setCloseSourceBranch(closeSourceBranchNode.asBoolean());
		}
	}

	private void parsePRAuthorAndReviewers(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode authorNode = prNode.get("author");
		if (authorNode != null) {
			JsonNode userNode = authorNode.get("user");
			if (userNode != null) {
				BitbucketClient.BitbucketUser author = objectMapper.convertValue(userNode,
						BitbucketClient.BitbucketUser.class);
				pr.setAuthor(author);
			}
		}

		JsonNode reviewersNode = prNode.get("reviewers");
		if (reviewersNode != null && reviewersNode.isArray()) {
			List<BitbucketClient.BitbucketUser> reviewers = new ArrayList<>();
			for (JsonNode reviewerNode : reviewersNode) {
				JsonNode userNode = reviewerNode.get("user");
				if (userNode != null) {
					BitbucketClient.BitbucketUser reviewer = objectMapper.convertValue(userNode,
							BitbucketClient.BitbucketUser.class);
					reviewers.add(reviewer);
				}
			}
			pr.setReviewers(reviewers);
		}
	}

	// CHANGE: Extract method to parse branches
	private void parsePRBranches(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode fromRefNode = prNode.get("fromRef");
		if (fromRefNode != null) {
			pr.setSource(createBranch(fromRefNode));
		}

		JsonNode toRefNode = prNode.get("toRef");
		if (toRefNode != null) {
			pr.setDestination(createBranch(toRefNode));
		}
	}

	// CHANGE: Extract method to create branch object
	private BitbucketClient.BitbucketBranch createBranch(JsonNode refNode) {
		BitbucketClient.BitbucketBranch branch = new BitbucketClient.BitbucketBranch();
		BitbucketClient.BbBranch bbBranch = new BitbucketClient.BbBranch();

		JsonNode displayIdNode = refNode.get("displayId");
		if (displayIdNode != null) {
			bbBranch.setName(displayIdNode.asText());
		}

		branch.setBranch(bbBranch);
		return branch;
	}

	// CHANGE: Extract method to parse links
	private void parsePRLinks(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode linksNode = prNode.get("links");
		if (linksNode != null) {
			JsonNode selfNode = linksNode.get("self");
			if (selfNode != null && selfNode.isArray() && !selfNode.isEmpty()) {
				JsonNode firstLink = selfNode.get(FIRST_ELEMENT_INDEX);
				if (firstLink != null) {
					JsonNode hrefNode = firstLink.get("href");
					if (hrefNode != null) {
						pr.setSelfLink(hrefNode.asText());
					}
				}
			}
		}
	}

	@Override
	public BitbucketClient.BitbucketCommit parseCommitNode(JsonNode commitNode, boolean isBitbucketCloud) {
		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();

		// CHANGE: Extract methods to parse different sections
		parseBasicCommitInfo(commitNode, commit);
		parseCommitParents(commitNode, commit);
		parseCommitAuthor(commitNode, commit);

		return commit;
	}

	// CHANGE: Extract method to parse basic commit info
	private void parseBasicCommitInfo(JsonNode commitNode, BitbucketClient.BitbucketCommit commit) {
		JsonNode idNode = commitNode.get("id");
		if (idNode != null) {
			commit.setHash(idNode.asText());
		}

		JsonNode authorTimestampNode = commitNode.get("authorTimestamp");
		if (authorTimestampNode != null) {
			commit.setDate(convertTimestampToISO(authorTimestampNode.asLong()));
		}

		JsonNode messageNode = commitNode.get("message");
		if (messageNode != null) {
			commit.setMessage(messageNode.asText());
		}
	}

	// CHANGE: Extract method to parse commit parents
	private void parseCommitParents(JsonNode commitNode, BitbucketClient.BitbucketCommit commit) {
		JsonNode parents = commitNode.get("parents");
		if (parents != null && parents.isArray()) {
			List<String> parentHashes = new ArrayList<>();
			for (JsonNode parent : parents) {
				JsonNode parentIdNode = parent.get("id");
				if (parentIdNode != null) {
					parentHashes.add(parentIdNode.asText());
				}
			}
			commit.setParents(parentHashes);
		}
	}

	// CHANGE: Extract method to parse commit author
	private void parseCommitAuthor(JsonNode commitNode, BitbucketClient.BitbucketCommit commit) {
		JsonNode authorNode = commitNode.get("author");
		if (authorNode != null) {
			BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();

			parseAuthorBasicInfo(authorNode, author);
			parseAuthorAdditionalInfo(authorNode, author);

			commit.setAuthor(author);
		}
	}

	// CHANGE: Extract method to parse author basic info
	private void parseAuthorBasicInfo(JsonNode authorNode, BitbucketClient.BitbucketUser author) {
		JsonNode nameNode = authorNode.get("name");
		if (nameNode != null) {
			author.setName(nameNode.asText());
		}

		JsonNode emailNode = authorNode.get("emailAddress");
		if (emailNode != null) {
			author.setEmailAddress(emailNode.asText());
		}

		JsonNode displayNameNode = authorNode.get("displayName");
		if (displayNameNode != null) {
			author.setDisplayName(displayNameNode.asText());
		}
	}

	// CHANGE: Extract method to parse author additional info
	private void parseAuthorAdditionalInfo(JsonNode authorNode, BitbucketClient.BitbucketUser author) {
		JsonNode idNode = authorNode.get("id");
		if (idNode != null) {
			author.setUuid(idNode.toString());
		}

		JsonNode slugNode = authorNode.get("slug");
		if (slugNode != null) {
			author.setSlug(slugNode.asText());
		}

		JsonNode activeNode = authorNode.get("active");
		if (activeNode != null) {
			author.setActive(activeNode.asBoolean());
		}
	}
}
