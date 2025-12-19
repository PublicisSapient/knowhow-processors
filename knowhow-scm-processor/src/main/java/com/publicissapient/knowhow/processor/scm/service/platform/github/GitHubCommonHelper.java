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

package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHUser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helper class for GitHub service implementations.
 * Contains common utilities for user creation, diff parsing, date handling, and stats extraction.
 */
@Component
@Slf4j
public class GitHubCommonHelper {

    private static final String MODIFIED_STATUS = "MODIFIED";
    private static final Set<String> BINARY_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".ico", ".svg",
            ".pdf", ".zip", ".tar", ".gz", ".rar", ".7z", ".exe", ".dll", ".so", ".dylib", ".class", ".jar");
    private static final Pattern HUNK_PATTERN = Pattern.compile("@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

    /**
     * Creates a User object from GitHub user
     */
    public User createUser(GHUser ghUser) throws IOException {
        return User.builder()
                .username(ghUser.getLogin())
                .displayName(ghUser.getName() != null ? ghUser.getName() : ghUser.getLogin())
                .email(ghUser.getEmail())
                .build();
    }

    /**
     * Maps GitHub file status to change type
     */
    public String mapGitHubStatus(String status) {
        if (status == null) return MODIFIED_STATUS;

        return switch (status.toLowerCase()) {
            case "added" -> "ADDED";
            case "removed" -> "DELETED";
            case "renamed" -> "RENAMED";
            default -> MODIFIED_STATUS;
        };
    }

    /**
     * Determines if a file is binary based on its extension
     */
    public boolean isBinaryFile(String fileName) {
        if (fileName == null) return false;
        String lowerFileName = fileName.toLowerCase();
        return BINARY_EXTENSIONS.stream().anyMatch(lowerFileName::endsWith);
    }

    /**
     * Extracts line numbers from diff content
     */
    public List<Integer> extractLineNumbers(String diff) {
        List<Integer> lineNumbers = new ArrayList<>();
        if (diff == null || diff.isEmpty()) {
            return lineNumbers;
        }

        try {
            String[] lines = diff.split("\n");
            int currentLineNumber = 0;

            for (String line : lines) {
                Matcher matcher = HUNK_PATTERN.matcher(line);
                if (matcher.find()) {
                    currentLineNumber = Integer.parseInt(matcher.group(1));
                } else if (isChangeLine(line)) {
                    lineNumbers.add(currentLineNumber);
                    if (line.startsWith("+")) {
                        currentLineNumber++;
                    }
                } else if (isContentLine(line)) {
                    currentLineNumber++;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract line numbers from diff: {}", e.getMessage());
        }

        return lineNumbers;
    }

    /**
     * Processes individual file change and returns stats
     */
    public FileChangeStats processFileChange(GHCommit.File file) {
        int additions = file.getLinesAdded();
        int deletions = file.getLinesDeleted();
        int changes = additions + deletions;

        ScmCommits.FileChange fileChange = ScmCommits.FileChange.builder()
                .filePath(file.getFileName())
                .addedLines(additions)
                .removedLines(deletions)
                .changedLines(changes)
                .changeType(mapGitHubStatus(file.getStatus()))
                .previousPath(file.getPreviousFilename())
                .isBinary(isBinaryFile(file.getFileName()))
                .changedLineNumbers(extractLineNumbers(file.getPatch()))
                .build();

        return new FileChangeStats(additions, deletions, changes, fileChange);
    }

    /**
     * Sets pull request state
     */
    public void setPullRequestState(ScmMergeRequests.ScmMergeRequestsBuilder builder, GHPullRequest ghPr) {
        if (ghPr.getState().name().equalsIgnoreCase(ScmMergeRequests.MergeRequestState.CLOSED.name())) {
            builder.state((ghPr.getMergedAt() != null) ? ScmMergeRequests.MergeRequestState.MERGED.name()
                    : ScmMergeRequests.MergeRequestState.CLOSED.name());
            builder.isClosed(true);
        } else {
            builder.state(ScmMergeRequests.MergeRequestState.OPEN.name());
            builder.isOpen(true);
        }
    }

    /**
     * Sets merge and close timestamps
     */
    public void setMergeAndCloseTimestamps(ScmMergeRequests.ScmMergeRequestsBuilder builder, GHPullRequest ghPr) {
        if (ghPr.getMergedAt() != null) {
            builder.mergedAt(ghPr.getMergedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (ghPr.getClosedAt() != null) {
            builder.closedDate(ghPr.getClosedAt().toInstant().toEpochMilli());
        }
    }

    /**
     * Sets pull request author
     */
    public void setPullRequestAuthor(ScmMergeRequests.ScmMergeRequestsBuilder builder, GHPullRequest ghPr) throws IOException {
        if (ghPr.getUser() != null) {
            User author = createUser(ghPr.getUser());
            builder.authorId(author);
            builder.authorUserId(ghPr.getUser().getLogin());
        }
    }

    /**
     * Extracts pull request statistics
     */
    public PullRequestStats extractPullRequestStats(GHPullRequest ghPr) {
        try {
            return new PullRequestStats(
                    ghPr.getAdditions() + ghPr.getDeletions(),
                    ghPr.getCommits(),
                    ghPr.getChangedFiles(),
                    ghPr.getAdditions(),
                    ghPr.getDeletions()
            );
        } catch (Exception e) {
            log.warn("Failed to extract stats from pull request #{}: {}", ghPr.getNumber(), e.getMessage());
            return new PullRequestStats(0, 0, 0, 0, 0);
        }
    }

    /**
     * Gets PR pickup time (first review activity)
     */
    public Long getPrPickupTime(GHPullRequest ghPr) throws IOException {
        Set<String> reviewActivities = Set.of(
                GHPullRequestReviewState.APPROVED.name(),
                GHPullRequestReviewState.COMMENTED.name(),
                GHPullRequestReviewState.CHANGES_REQUESTED.name(),
                GHPullRequestReviewState.DISMISSED.name()
        );

        Date pickedForReviewOn = null;
        for (GHPullRequestReview requestReview : ghPr.listReviews()) {
            GHPullRequestReviewState state = requestReview.getState();
            if (state != null && reviewActivities.contains(state.name())) {
                Date reviewTime = requestReview.getSubmittedAt();
                if (reviewTime != null && (pickedForReviewOn == null || reviewTime.before(pickedForReviewOn))) {
                    pickedForReviewOn = reviewTime;
                }
            }
        }
        return pickedForReviewOn == null ? null : pickedForReviewOn.toInstant().toEpochMilli();
    }

    private boolean isChangeLine(String line) {
        return (line.startsWith("+") || line.startsWith("-")) && !line.startsWith("+++") && !line.startsWith("---");
    }

    private boolean isContentLine(String line) {
        return !line.startsWith("\\") && !line.startsWith("@@") && !line.startsWith("diff");
    }

    /**
     * Helper class for file change statistics
     */
    @Getter
    public static class FileChangeStats {
        private final int additions;
        private final int deletions;
        private final int changes;
        private final ScmCommits.FileChange fileChange;

        public FileChangeStats(int additions, int deletions, int changes, ScmCommits.FileChange fileChange) {
            this.additions = additions;
            this.deletions = deletions;
            this.changes = changes;
            this.fileChange = fileChange;
        }
    }

    /**
     * Helper class for pull request statistics
     */
    @Getter
    public static class PullRequestStats {
        private final int linesChanged;
        private final int commitCount;
        private final int filesChanged;
        private final int addedLines;
        private final int removedLines;

        public PullRequestStats(int linesChanged, int commitCount, int filesChanged, int addedLines, int removedLines) {
            this.linesChanged = linesChanged;
            this.commitCount = commitCount;
            this.filesChanged = filesChanged;
            this.addedLines = addedLines;
            this.removedLines = removedLines;
        }
    }
}
