package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.bson.types.ObjectId;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GitHub implementation of GitPlatformService.
 * 
 * This service focuses on business logic and data transformation,
 * delegating all GitHub API interactions to GitHubClient.
 */
@Service
public class GitHubService implements GitPlatformService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    private final GitHubClient gitHubClient;
    private final RateLimitService rateLimitService;

    @Autowired
    public GitHubService(GitHubClient gitHubClient, RateLimitService rateLimitService) {
        this.gitHubClient = gitHubClient;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                            String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching commits for GitHub repository: {}/{}", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());
            String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();
            List<GHCommit> ghCommits = gitHubClient.fetchCommits(owner, gitUrlInfo.getRepositoryName(), branchName, token, since, until);
            List<ScmCommits> commitDetails = new ArrayList<>();

            for (GHCommit ghCommit : ghCommits) {
                try {
                    ScmCommits commitDetail = convertToCommit(ghCommit, toolConfigId, owner, gitUrlInfo.getRepositoryName());
                    commitDetails.add(commitDetail);
                } catch (Exception e) {
                    log.warn("Failed to convert GitHub commit {}: {}", ghCommit.getSHA1(), e.getMessage());
                }
            }
            
            log.info("Successfully converted {} GitHub commits to domain objects", commitDetails.size());
            return commitDetails;
            
        } catch (IOException e) {
            log.error("Failed to fetch commits from GitHub repository {}/{}: {}", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException("GitHub", "Failed to fetch commits from GitHub", e);
        }
    }

    @Override
    public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                                     String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching merge requests for GitHub repository: {}/{} (branch: {})", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");

            List<GHPullRequest> ghPullRequests = gitHubClient.fetchPullRequests(gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), token, since, until);
            List<ScmMergeRequests> mergeRequests = new ArrayList<>();

            // Filter by target branch if specified
            if (branchName != null && !branchName.trim().isEmpty()) {
                ghPullRequests = ghPullRequests.stream()
                    .filter(pr -> {
                        try {
                            String baseBranch = pr.getBase().getRef();
                            return baseBranch != null && baseBranch.equals(branchName);
                        } catch (Exception e) {
                            log.warn("Failed to get base branch for PR #{}: {}", pr.getNumber(), e.getMessage());
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            }

            for (GHPullRequest ghPr : ghPullRequests) {
                try {
                    ScmMergeRequests mergeRequest = convertToMergeRequest(ghPr, toolConfigId);
                    mergeRequests.add(mergeRequest);
                } catch (Exception e) {
                    log.warn("Failed to convert GitHub pull request #{}: {}", ghPr.getNumber(), e.getMessage());
                }
            }

            log.info("Successfully converted {} GitHub pull requests to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (IOException e) {
            log.error("Failed to fetch merge requests from GitHub repository {}/{}: {}", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException("GitHub", "Failed to fetch merge requests from GitHub", e);
        }
    }

    @Override
    public List<ScmMergeRequests> fetchMergeRequestsByState(String toolConfigId, String owner, String repository, String branchName, String state,
                                                         String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching {} merge requests for GitHub repository: {}/{} (branch: {})", state, owner, repository, branchName != null ? branchName : "all");

            List<GHPullRequest> ghPullRequests = gitHubClient.fetchPullRequestsByState(owner, repository, state, token, since, until);

            // Filter by target branch if specified
            if (branchName != null && !branchName.trim().isEmpty()) {
                ghPullRequests = ghPullRequests.stream()
                    .filter(pr -> {
                        try {
                            String baseBranch = pr.getBase().getRef();
                            return baseBranch != null && baseBranch.equals(branchName);
                        } catch (Exception e) {
                            log.warn("Failed to get base branch for PR #{}: {}", pr.getNumber(), e.getMessage());
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            }

            List<ScmMergeRequests> mergeRequests = new ArrayList<>();

            for (GHPullRequest ghPr : ghPullRequests) {
                try {
                    ScmMergeRequests mergeRequest = convertToMergeRequest(ghPr, toolConfigId);
                    mergeRequests.add(mergeRequest);
                } catch (Exception e) {
                    log.warn("Failed to convert GitHub pull request #{}: {}", ghPr.getNumber(), e.getMessage());
                }
            }

            log.info("Successfully converted {} {} GitHub pull requests to domain objects", mergeRequests.size(), state);
            return mergeRequests;

        } catch (IOException e) {
            log.error("Failed to fetch {} merge requests from GitHub repository {}/{}: {}", state, owner, repository, e.getMessage());
            throw new PlatformApiException("GitHub", "Failed to fetch merge requests from GitHub", e);
        }
    }

    @Override
    public List<ScmMergeRequests> fetchLatestMergeRequests(String toolConfigId, String owner, String repository, String branchName,
                                                        String token, int limit) throws PlatformApiException {
        try {
            log.info("Fetching latest {} merge requests for GitHub repository: {}/{} (branch: {})", limit, owner, repository, branchName != null ? branchName : "all");

            // Fetch more than requested to account for filtering
            int fetchLimit = branchName != null && !branchName.trim().isEmpty() ? limit * 2 : limit;
            List<GHPullRequest> ghPullRequests = gitHubClient.fetchLatestPullRequests(owner, repository, token, fetchLimit);

            // Filter by target branch if specified
            if (branchName != null && !branchName.trim().isEmpty()) {
                ghPullRequests = ghPullRequests.stream()
                    .filter(pr -> {
                        try {
                            String baseBranch = pr.getBase().getRef();
                            return baseBranch != null && baseBranch.equals(branchName);
                        } catch (Exception e) {
                            log.warn("Failed to get base branch for PR #{}: {}", pr.getNumber(), e.getMessage());
                            return false;
                        }
                    })
                    .limit(limit)
                    .collect(Collectors.toList());
            }

            List<ScmMergeRequests> mergeRequests = new ArrayList<>();

            for (GHPullRequest ghPr : ghPullRequests) {
                try {
                    ScmMergeRequests mergeRequest = convertToMergeRequest(ghPr, toolConfigId);
                    mergeRequests.add(mergeRequest);
                } catch (Exception e) {
                    log.warn("Failed to convert GitHub pull request #{}: {}", ghPr.getNumber(), e.getMessage());
                }
            }

            log.info("Successfully converted {} latest GitHub pull requests to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (IOException e) {
            log.error("Failed to fetch latest merge requests from GitHub repository {}/{}: {}", owner, repository, e.getMessage());
            throw new PlatformApiException("GitHub", "Failed to fetch latest merge requests from GitHub", e);
        }
    }

    @Override
    public boolean testConnection(String token) throws PlatformApiException {
        try {
            return gitHubClient.testConnection(token);
        } catch (IOException e) {
            throw new PlatformApiException("GitHub", "GitHub connection test failed", e);
        }
    }

    @Override
    public String getPlatformName() {
        return "GitHub";
    }

    @Override
    public String getApiBaseUrl() {
        return gitHubClient.getApiUrl();
    }


    /**
     * Converts a GitHub commit to domain Commit object
     */
    private ScmCommits convertToCommit(GHCommit ghCommit, String toolConfigId, String owner, String repository) throws IOException {
        ScmCommits.ScmCommitsBuilder builder = ScmCommits.builder()
            .processorItemId(new ObjectId(toolConfigId))
            .repositoryName(owner + "/" + repository)
            .sha(ghCommit.getSHA1())
            .commitMessage(ghCommit.getCommitShortInfo().getMessage())
            .commitTimestamp(ghCommit.getCommitDate().toInstant().toEpochMilli());

        // Set author information
        if (ghCommit.getAuthor() != null) {
            GHUser author = ghCommit.getAuthor();
            User user = User.builder()
                    .username(author.getLogin())
                    .displayName(author.getName() != null ? author.getName() : author.getLogin())
                    .email(author.getEmail())
                    .build();
            builder.commitAuthor(user)
                   .authorEmail(author.getEmail());
        }else if (ghCommit.getCommitter() != null) {
            GHUser committer = ghCommit.getCommitter();
            User user = User.builder()
                    .username(committer.getLogin())
                    .displayName(committer.getName() != null ? committer.getName() : committer.getLogin())
                    .email(committer.getEmail())
                    .build();
            builder.commitAuthor(user)
                   .authorEmail(committer.getEmail());
        }

        // Set branch name if available
        try {
            // Try to get branch from the commit context
            builder.branchName("main"); // Default, could be enhanced to detect actual branch
        } catch (Exception e) {
            log.debug("Could not determine branch for commit {}", ghCommit.getSHA1());
        }

        // Extract diff statistics and file changes
        GitHubDiffStats diffStats = extractDiffStats(ghCommit);
        builder.addedLines(diffStats.getAddedLines())
               .removedLines(diffStats.getRemovedLines())
               .changedLines(diffStats.getChangedLines())
               .filesChanged(diffStats.getFilesChanged())
               .fileChanges(diffStats.getFileChanges());

        // Set merge commit flag
        try {
            List<String> parentShas = new ArrayList<>();
            for (String parentSha : ghCommit.getParentSHA1s()) {
                parentShas.add(parentSha);
            }
            builder.parentShas(parentShas)
                   .isMergeCommit(parentShas.size() > 1);
        } catch (Exception e) {
            log.debug("Could not get parent SHAs for commit {}", ghCommit.getSHA1());
        }

        return builder.build();
    }

    /**
     * Converts a GitHub pull request to domain MergeRequest object
     */
    private ScmMergeRequests convertToMergeRequest(GHPullRequest ghPr, String toolConfigId) throws IOException {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder()
            .processorItemId(new ObjectId(toolConfigId))
            .repositoryName(ghPr.getRepository().getFullName())
            .externalId(String.valueOf(ghPr.getNumber()))
            .title(ghPr.getTitle())
            .summary(ghPr.getBody())
            .fromBranch(ghPr.getHead().getRef())
            .toBranch(ghPr.getBase().getRef())
            .createdDate(ghPr.getCreatedAt().toInstant().toEpochMilli())
            .updatedDate(ghPr.getUpdatedAt().toInstant().toEpochMilli());

		if (ghPr.getState().name().equalsIgnoreCase(ScmMergeRequests.MergeRequestState.CLOSED.name())) {
			builder.state((ghPr.getMergedAt() != null) ? ScmMergeRequests.MergeRequestState.MERGED.name()
					: ScmMergeRequests.MergeRequestState.CLOSED.name());
		} else {
			builder.state(ScmMergeRequests.MergeRequestState.OPEN.name());
		}
        // Set merge/close timestamps
        if (ghPr.getMergedAt() != null) {
            builder.mergedAt(ghPr.getMergedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (ghPr.getClosedAt() != null) {
            builder.closedDate(ghPr.getClosedAt().toInstant().toEpochMilli());
        }

        // Set author information
        if (ghPr.getUser() != null) {
            User author = User.builder()
                .username(ghPr.getUser().getLogin())
                .displayName(ghPr.getUser().getName() != null ? ghPr.getUser().getName() : ghPr.getUser().getLogin())
                .email(ghPr.getUser().getEmail())
                .build();
            builder.authorId(author);
            builder.authorUserId(ghPr.getUser().getLogin());
        }

        // Set merge request URL
        builder.mergeRequestUrl(ghPr.getHtmlUrl().toString());

        // Extract pull request statistics
        PullRequestStats prStats = extractPullRequestStats(ghPr);
        builder.linesChanged(prStats.getLinesChanged())
               .commitCount(prStats.getCommitCount())
               .filesChanged(prStats.getFilesChanged())
               .addedLines(prStats.getAddedLines())
               .removedLines(prStats.getRemovedLines());

        builder.pickedForReviewOn(getPrPickupTime(ghPr));

        return builder.build();
    }

    /**
     * Extracts diff statistics from a GitHub commit
     */
    private GitHubDiffStats extractDiffStats(GHCommit ghCommit) {
        try {
            // Get commit files to extract detailed diff information
            List<GHCommit.File> files = ghCommit.getFiles();

            int totalAdditions = 0;
            int totalDeletions = 0;
            int totalChanges = 0;
            List<ScmCommits.FileChange> fileChanges = new ArrayList<>();

            for (GHCommit.File file : files) {
                // GitHub API uses different method names
                int additions = file.getLinesAdded();
                int deletions = file.getLinesDeleted();
                int changes = additions + deletions;

                totalAdditions += additions;
                totalDeletions += deletions;
                totalChanges += changes;

                // Create FileChange object
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

                fileChanges.add(fileChange);
            }

            return new GitHubDiffStats(totalAdditions, totalDeletions, totalChanges, files.size(), fileChanges);

        } catch (Exception e) {
            log.warn("Failed to extract diff stats from commit {}: {}", ghCommit.getSHA1(), e.getMessage());
            return new GitHubDiffStats(0, 0, 0, 0, new ArrayList<>());
        }
    }

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

    /**
     * Maps GitHub file status to our change type
     */
    private String mapGitHubStatus(String status) {
        if (status == null) return "MODIFIED";
        
        switch (status.toLowerCase()) {
            case "added": return "ADDED";
            case "removed": return "DELETED";
            case "modified": return "MODIFIED";
            case "renamed": return "RENAMED";
            default: return "MODIFIED";
        }
    }

    /**
     * Determines if a file is binary based on its extension
     */
    private boolean isBinaryFile(String fileName) {
        if (fileName == null) return false;
        
        String[] binaryExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".ico", ".svg",
                                   ".pdf", ".zip", ".tar", ".gz", ".rar", ".7z",
                                   ".exe", ".dll", ".so", ".dylib", ".class", ".jar"};
        
        String lowerFileName = fileName.toLowerCase();
        for (String ext : binaryExtensions) {
            if (lowerFileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts line numbers from diff content for both added and removed lines
     */
    private List<Integer> extractLineNumbers(String diff) {
        List<Integer> lineNumbers = new ArrayList<>();
        if (diff == null || diff.isEmpty()) {
            return lineNumbers;
        }

        try {
            String[] lines = diff.split("\n");
            Pattern hunkPattern = Pattern.compile("@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

            int currentLineNumber = 0;
            for (String line : lines) {
                Matcher matcher = hunkPattern.matcher(line);
                if (matcher.find()) {
                    currentLineNumber = Integer.parseInt(matcher.group(1));
                } else if ((line.startsWith("+") || line.startsWith("-")) &&
                          !line.startsWith("+++") && !line.startsWith("---")) {
                    lineNumbers.add(currentLineNumber);
                    if (line.startsWith("+")) {
                        currentLineNumber++;
                    }
                } else if (!line.startsWith("\\") && !line.startsWith("@@") && !line.startsWith("diff")) {
                    currentLineNumber++;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract line numbers from diff: {}", e.getMessage());
        }

        return lineNumbers;
    }

    /**
     * Extracts statistics from a GitHub pull request
     */
    private PullRequestStats extractPullRequestStats(GHPullRequest ghPr) {
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
     * Helper class for GitHub diff statistics
     */
    private static class GitHubDiffStats {
        private final int addedLines;
        private final int removedLines;
        private final int changedLines;
        private final int filesChanged;
        private final List<ScmCommits.FileChange> fileChanges;

        public GitHubDiffStats(int addedLines, int removedLines, int changedLines, int filesChanged, List<ScmCommits.FileChange> fileChanges) {
            this.addedLines = addedLines;
            this.removedLines = removedLines;
            this.changedLines = changedLines;
            this.filesChanged = filesChanged;
            this.fileChanges = fileChanges;
        }

        public int getAddedLines() { return addedLines; }
        public int getRemovedLines() { return removedLines; }
        public int getChangedLines() { return changedLines; }
        public int getFilesChanged() { return filesChanged; }
        public List<ScmCommits.FileChange> getFileChanges() { return fileChanges; }
    }

    /**
     * Helper class for pull request statistics
     */
    private static class PullRequestStats {
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

        public int getLinesChanged() { return linesChanged; }
        public int getCommitCount() { return commitCount; }
        public int getFilesChanged() { return filesChanged; }
        public int getAddedLines() { return addedLines; }
        public int getRemovedLines() { return removedLines; }
    }
}