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

package com.publicissapient.knowhow.processor.scm.client.wrapper.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

import com.publicissapient.knowhow.processor.scm.client.wrapper.BitbucketParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudBitBucketParser implements BitbucketParser {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	// CHANGE: Extract constants to improve maintainability
	private static final String DIFF_GIT_PREFIX = "diff --git";
	private static final String HUNK_HEADER_PREFIX = "@@";
	private static final String ADDED_LINE_PREFIX = "+";
	private static final String REMOVED_LINE_PREFIX = "-";
	private static final String ADDED_FILE_PREFIX = "+++";
	private static final String REMOVED_FILE_PREFIX = "---";
	private static final int MIN_DIFF_PARTS = 4;
	private static final int FILE_PATH_INDEX = 3;
	private static final int FILE_PATH_PREFIX_LENGTH = 2;

	// CHANGE: Extract change type constants
	private static final String CHANGE_TYPE_ADDED = "ADDED";
	private static final String CHANGE_TYPE_DELETED = "DELETED";
	private static final String CHANGE_TYPE_MODIFIED = "MODIFIED";
	private static final String CHANGE_TYPE_UNCHANGED = "UNCHANGED";

	/**
	 * Parses diff content to extract file changes.
	 */
	public List<ScmCommits.FileChange> parseDiffToFileChanges(String diffContent) {
		List<ScmCommits.FileChange> fileChanges = new ArrayList<>();

		if (diffContent == null || diffContent.trim().isEmpty()) {
			return fileChanges;
		}

		String[] lines = diffContent.split("\n");
		DiffParsingContext context = new DiffParsingContext();

		for (String line : lines) {
			if (line.startsWith(DIFF_GIT_PREFIX)) {
				// CHANGE: Extract method to handle file transition
				handleFileTransition(fileChanges, context, line);
			} else if (line.startsWith(ADDED_LINE_PREFIX) && !line.startsWith(ADDED_FILE_PREFIX)) {
				context.addedLines++;
			} else if (line.startsWith(REMOVED_LINE_PREFIX) && !line.startsWith(REMOVED_FILE_PREFIX)) {
				context.removedLines++;
			} else if (line.startsWith(HUNK_HEADER_PREFIX)) {
				// CHANGE: Extract method to parse hunk header
				parseHunkHeader(line, context.lineNumbers);
			}
		}

		// Save last file
		if (context.currentFile != null) {
			addFileChange(fileChanges, context);
		}

		return fileChanges;
	}

	// CHANGE: Extract helper class to reduce method parameters
	private static class DiffParsingContext {
		String currentFile = null;
		int addedLines = 0;
		int removedLines = 0;
		Set<Integer> lineNumbers = new HashSet<>();

		void reset() {
			addedLines = 0;
			removedLines = 0;
			lineNumbers.clear();
		}
	}

	// CHANGE: Extract method to handle file transitions
	private void handleFileTransition(List<ScmCommits.FileChange> fileChanges, DiffParsingContext context,
			String line) {
		// Save previous file if exists
		if (context.currentFile != null) {
			addFileChange(fileChanges, context);
		}

		// Extract file path
		String[] parts = line.split(" ");
		if (parts.length >= MIN_DIFF_PARTS) {
			context.currentFile = parts[FILE_PATH_INDEX].substring(FILE_PATH_PREFIX_LENGTH); // Remove "b/" prefix
		}
		context.reset();
	}

	// CHANGE: Extract method to add file change
	private void addFileChange(List<ScmCommits.FileChange> fileChanges, DiffParsingContext context) {
		fileChanges.add(ScmCommits.FileChange.builder().filePath(context.currentFile).addedLines(context.addedLines)
				.removedLines(context.removedLines)
				.changeType(determineChangeType(context.addedLines, context.removedLines))
				.changedLineNumbers(new ArrayList<>(context.lineNumbers)).build());
	}

	// CHANGE: Extract method to parse hunk header
	private void parseHunkHeader(String line, Set<Integer> lineNumbers) {
		String hunkInfo = line.substring(2, line.lastIndexOf(HUNK_HEADER_PREFIX)).trim();
		for (String part : hunkInfo.split(" ")) {
			if (part.startsWith(ADDED_LINE_PREFIX) || part.startsWith(REMOVED_LINE_PREFIX)) {
				parseLineRange(part, lineNumbers, line);
			}
		}
	}

	// CHANGE: Extract method to parse line range
	private void parseLineRange(String part, Set<Integer> lineNumbers, String originalLine) {
		String[] rangeParts = part.substring(1).split(",");
		try {
			int startLine = Integer.parseInt(rangeParts[0]);
			int lineCount = rangeParts.length > 1 ? Integer.parseInt(rangeParts[1]) : 1;
			for (int i = 0; i < lineCount; i++) {
				lineNumbers.add(startLine + i);
			}
		} catch (NumberFormatException e) {
			// CHANGE: Include exception in log for better debugging
			log.warn("Failed to parse line number from hunk header: {}", originalLine, e);
		}
	}

	/**
	 * Determines the change type based on added and removed lines.
	 */
	private String determineChangeType(int addedLines, int removedLines) {
		if (addedLines > 0 && removedLines == 0) {
			return CHANGE_TYPE_ADDED;
		} else if (addedLines == 0 && removedLines > 0) {
			return CHANGE_TYPE_DELETED;
		} else if (addedLines > 0 && removedLines > 0) {
			return CHANGE_TYPE_MODIFIED;
		} else {
			return CHANGE_TYPE_UNCHANGED;
		}
	}

	@Override
	public ScmMergeRequests.PullRequestStats parsePRDiffToFileChanges(String diffContent) {
		if (diffContent == null || diffContent.trim().isEmpty()) {
			return new ScmMergeRequests.PullRequestStats(0, 0, 0);
		}

		String[] lines = diffContent.split("\n");
		int addedLines = 0;
		int removedLines = 0;
		int changedFiles = 0;

		for (String line : lines) {
			if (line.startsWith(DIFF_GIT_PREFIX)) {
				changedFiles++;
			} else if (line.startsWith(ADDED_LINE_PREFIX) && !line.startsWith(ADDED_FILE_PREFIX)) {
				addedLines++;
			} else if (line.startsWith(REMOVED_LINE_PREFIX) && !line.startsWith(REMOVED_FILE_PREFIX)) {
				removedLines++;
			}
		}

		return new ScmMergeRequests.PullRequestStats(addedLines, removedLines, changedFiles);
	}

	public BitbucketClient.BitbucketPullRequest parsePullRequestNode(JsonNode prNode) {
		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();

		// CHANGE: Extract methods to parse different sections
		parseBasicPRInfo(prNode, pr);
		parsePRDates(prNode, pr);
		parsePRMergeInfo(prNode, pr);
		parsePRAuthorAndReviewers(prNode, pr);
		parsePRBranches(prNode, pr);
		parsePRLinks(prNode, pr);

		return pr;
	}

	// CHANGE: Extract method to parse basic PR info
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

	// CHANGE: Extract method to parse PR dates
	private void parsePRDates(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode createdOnNode = prNode.get("created_on");
		if (createdOnNode != null) {
			pr.setCreatedOn(createdOnNode.asText());
		}

		JsonNode updatedOnNode = prNode.get("updated_on");
		if (updatedOnNode != null) {
			pr.setUpdatedOn(updatedOnNode.asText());
			pr.setClosedOn(updatedOnNode.asText());
		}
	}

	// CHANGE: Extract method to parse merge info
	private void parsePRMergeInfo(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode mergeCommitNode = prNode.get("merge_commit");
		if (mergeCommitNode != null) {
			BitbucketClient.BitbucketCommit mergeCommit = objectMapper.convertValue(mergeCommitNode,
					BitbucketClient.BitbucketCommit.class);
			pr.setMergeCommit(mergeCommit);
		}

		JsonNode closeSourceBranchNode = prNode.get("close_source_branch");
		if (closeSourceBranchNode != null) {
			pr.setCloseSourceBranch(closeSourceBranchNode.asBoolean());
		}
	}

	// CHANGE: Extract method to parse author and reviewers
	private void parsePRAuthorAndReviewers(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode authorNode = prNode.get("author");
		if (authorNode != null) {
			BitbucketClient.BitbucketUser author = objectMapper.convertValue(authorNode,
					BitbucketClient.BitbucketUser.class);
			pr.setAuthor(author);
		}

		JsonNode reviewersNode = prNode.get("reviewers");
		if (reviewersNode != null && reviewersNode.isArray()) {
			List<BitbucketClient.BitbucketUser> reviewers = new ArrayList<>();
			for (JsonNode reviewerNode : reviewersNode) {
				BitbucketClient.BitbucketUser reviewer = objectMapper.convertValue(reviewerNode,
						BitbucketClient.BitbucketUser.class);
				reviewers.add(reviewer);
			}
			pr.setReviewers(reviewers);
		}
	}

	// CHANGE: Extract method to parse branches
	private void parsePRBranches(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode sourceNode = prNode.get("source");
		if (sourceNode != null) {
			BitbucketClient.BitbucketBranch source = objectMapper.convertValue(sourceNode,
					BitbucketClient.BitbucketBranch.class);
			pr.setSource(source);
		}

		JsonNode destinationNode = prNode.get("destination");
		if (destinationNode != null) {
			BitbucketClient.BitbucketBranch destination = objectMapper.convertValue(destinationNode,
					BitbucketClient.BitbucketBranch.class);
			pr.setDestination(destination);
		}
	}

	// CHANGE: Extract method to parse links
	private void parsePRLinks(JsonNode prNode, BitbucketClient.BitbucketPullRequest pr) {
		JsonNode prLinksNode = prNode.get("links");
		if (prLinksNode != null) {
			JsonNode selfNode = prLinksNode.get("self");
			if (selfNode != null && selfNode.get("href") != null) {
				pr.setSelfLink(selfNode.get("href").asText());
			}
		}
	}

	@Override
	public BitbucketClient.BitbucketCommit parseCommitNode(JsonNode commitNode, boolean isBitbucketCloud) {
		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();

		// CHANGE: Extract methods to parse different sections
		parseBasicCommitInfo(commitNode, commit);
		parseCommitAuthor(commitNode, commit);

		return commit;
	}

	// CHANGE: Extract method to parse basic commit info
	private void parseBasicCommitInfo(JsonNode commitNode, BitbucketClient.BitbucketCommit commit) {
		JsonNode hashNode = commitNode.get("hash");
		if (hashNode != null) {
			commit.setHash(hashNode.asText());
		}

		JsonNode dateNode = commitNode.get("date");
		if (dateNode != null) {
			commit.setDate(dateNode.asText());
		}

		JsonNode messageNode = commitNode.get("message");
		if (messageNode != null) {
			commit.setMessage(messageNode.asText());
		}
	}

	// CHANGE: Extract method to parse commit author
	private void parseCommitAuthor(JsonNode commitNode, BitbucketClient.BitbucketCommit commit) {
		JsonNode authorNode = commitNode.get("author");
		if (authorNode != null) {
			BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();

			parseAuthorType(authorNode, author);
			parseAuthorUser(authorNode, author);
			parseAuthorRawInfo(authorNode, author);

			commit.setAuthor(author);
		}
	}

	// CHANGE: Extract method to parse author type
	private void parseAuthorType(JsonNode authorNode, BitbucketClient.BitbucketUser author) {
		JsonNode typeNode = authorNode.get("type");
		if (typeNode != null) {
			author.setType(typeNode.asText());
		}
	}

	// CHANGE: Extract method to parse author user details
	private void parseAuthorUser(JsonNode authorNode, BitbucketClient.BitbucketUser author) {
		JsonNode userNode = authorNode.get("user");
		if (userNode != null) {
			BitbucketClient.BbUser user = new BitbucketClient.BbUser();

			JsonNode displayNameNode = userNode.get("display_name");
			if (displayNameNode != null) {
				user.setDisplayName(displayNameNode.asText());
			}

			JsonNode usernameNode = userNode.get("nickname");
			if (usernameNode != null) {
				user.setUsername(usernameNode.asText());
			}

			JsonNode uuidNode = userNode.get("uuid");
			if (uuidNode != null) {
				user.setUuid(uuidNode.asText());
			}

			JsonNode accountIdNode = userNode.get("account_id");
			if (accountIdNode != null) {
				user.setAccountId(accountIdNode.asText());
			}

			author.setUser(user);
		}
	}

	// CHANGE: Extract method to parse raw author info
	private void parseAuthorRawInfo(JsonNode authorNode, BitbucketClient.BitbucketUser author) {
		JsonNode rawNode = authorNode.get("raw");
		if (rawNode != null) {
			String rawAuthor = rawNode.asText();
			// Extract email from "name <email>" format
			if (rawAuthor.contains("<") && rawAuthor.contains(">")) {
				int emailStartIndex = rawAuthor.indexOf("<") + 1;
				int emailEndIndex = rawAuthor.indexOf(">");
				String email = rawAuthor.substring(emailStartIndex, emailEndIndex);
				author.setEmailAddress(email);
				String name = rawAuthor.substring(0, emailStartIndex - 1).trim();
				author.setName(name);
			}
		}
	}
}
