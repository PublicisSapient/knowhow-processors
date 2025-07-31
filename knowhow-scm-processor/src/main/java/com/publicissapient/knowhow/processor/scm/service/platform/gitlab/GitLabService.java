package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.kpidashboard.common.model.scm.CommitDetails;
import com.publicissapient.kpidashboard.common.model.scm.MergeRequests;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Diff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitLab implementation of GitPlatformService.
 * 
 * This service focuses on business logic and data transformation,
 * delegating all GitLab API interactions to GitLabClient.
 */
@Service
@Slf4j
public class GitLabService implements GitPlatformService {

    // Manual logger field since Lombok @Slf4j is not working properly
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitLabService.class);

    private final GitLabClient gitLabClient;
    private final RateLimitService rateLimitService;
    private final GitUrlParser gitUrlParser;

    @Value("${git.platforms.gitlab.api-url:https://gitlab.com}")
    private String defaultGitlabApiUrl;

    // Thread-local storage to hold the current repository URL for the request context
    private static final ThreadLocal<String> currentRepositoryUrl = new ThreadLocal<>();

    @Autowired
    public GitLabService(GitLabClient gitLabClient, RateLimitService rateLimitService, GitUrlParser gitUrlParser) {
        this.gitLabClient = gitLabClient;
        this.rateLimitService = rateLimitService;
        this.gitUrlParser = gitUrlParser;
    }

    /**
     * Sets the repository URL for the current thread context.
     * This should be called before any GitLab operations to ensure
     * the correct GitLab instance is used for on-premise installations.
     *
     * @param repositoryUrl the repository URL from the scan request
     */
    public void setRepositoryUrlContext(String repositoryUrl) {
        currentRepositoryUrl.set(repositoryUrl);
        log.debug("Set repository URL context: {}", repositoryUrl);
    }

    /**
     * Clears the repository URL context for the current thread.
     * This should be called after GitLab operations are complete.
     */
    public void clearRepositoryUrlContext() {
        currentRepositoryUrl.remove();
        log.debug("Cleared repository URL context");
    }

    /**
     * Gets the repository URL from the current thread context.
     * If not set, falls back to constructing URL using default GitLab URL.
     *
     * @param owner the repository owner
     * @param repository the repository name
     * @return the repository URL
     */
    private String getRepositoryUrl(String owner, String repository) {
        String contextUrl = currentRepositoryUrl.get();
        if (contextUrl != null && !contextUrl.trim().isEmpty()) {
            log.debug("Using repository URL from context: {}", contextUrl);
            return contextUrl;
        }

        // Fallback to constructing URL using default GitLab URL
        String fallbackUrl = constructRepositoryUrl(owner, repository);
        log.debug("Using fallback repository URL: {}", fallbackUrl);
        return fallbackUrl;
    }

    /**
     * Constructs a repository URL from owner and repository name using default GitLab URL.
     * This method is used as a fallback when repository URL context is not available.
     */
    private String constructRepositoryUrl(String owner, String repository) {
        // Try to detect if owner contains a full path (for GitLab groups/subgroups)
        // This is a heuristic approach for on-premise instances
        if (owner.contains(".") && !owner.contains("/")) {
            // If owner looks like a domain, it might be an on-premise instance
            // This is a best-effort approach
            return "https://" + owner.split("/")[0] + "/" + owner + "/" + repository;
        }

        return defaultGitlabApiUrl + "/" + owner + "/" + repository;
    }

    @Override
    public List<CommitDetails> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                            String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching commits for GitLab repository: {}/{}", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());

            String repositoryUrl = getRepositoryUrl(gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());
            String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();
            log.debug("Using repository URL: {}", repositoryUrl);
            List<org.gitlab4j.api.models.Commit> gitlabCommits = gitLabClient.fetchCommits(owner, gitUrlInfo.getRepositoryName(), branchName, token, since, until, repositoryUrl);
            List<CommitDetails> commitDetails = new ArrayList<>();

            for (org.gitlab4j.api.models.Commit gitlabCommit : gitlabCommits) {
                try {
                    CommitDetails commitDetail = convertToCommit(gitlabCommit, toolConfigId, owner, gitUrlInfo.getRepositoryName(), token, repositoryUrl);
                    commitDetails.add(commitDetail);
                } catch (Exception e) {
                    log.warn("Failed to convert GitLab commit {}: {}", gitlabCommit.getId(), e.getMessage());
                }
            }
            
            log.info("Successfully converted {} GitLab commits to domain objects", commitDetails.size());
            return commitDetails;
            
        } catch (GitLabApiException e) {
            log.error("Failed to fetch commits from GitLab repository {}/{}: {}", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException("GitLab", "Failed to fetch commits from GitLab", e);
        }
    }

    @Override
    public List<MergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                                  String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching merge requests for GitLab repository: {}/{} (branch: {})", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");
            String repositoryUrl = getRepositoryUrl(gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());
            String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();
            List<org.gitlab4j.api.models.MergeRequest> gitlabMergeRequests = gitLabClient.fetchMergeRequests(owner, gitUrlInfo.getRepositoryName(), branchName, token, since, until, repositoryUrl);
            List<MergeRequests> mergeRequests = new ArrayList<>();

            for (org.gitlab4j.api.models.MergeRequest gitlabMr : gitlabMergeRequests) {
                try {
                    MergeRequests mergeRequest = convertToMergeRequest(gitlabMr, toolConfigId, owner, gitUrlInfo.getRepositoryName(), token, repositoryUrl);
                    mergeRequests.add(mergeRequest);
                } catch (Exception e) {
                    log.warn("Failed to convert GitLab merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
                }
            }
            
            log.info("Successfully converted {} GitLab merge requests to domain objects", mergeRequests.size());
            return mergeRequests;
            
        } catch (GitLabApiException e) {
            log.error("Failed to fetch merge requests from GitLab repository {}/{}: {}", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException("GitLab", "Failed to fetch merge requests from GitLab", e);
        }
    }

    @Override
    public List<MergeRequests> fetchMergeRequestsByState(String toolConfigId, String owner, String repository, String branchName, String state,
                                                         String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching {} merge requests for GitLab repository: {}/{} (branch: {})", state, owner, repository, branchName != null ? branchName : "all");

            String repositoryUrl = getRepositoryUrl(owner, repository);
            log.debug("Using repository URL: {}", repositoryUrl);

            List<org.gitlab4j.api.models.MergeRequest> gitlabMergeRequests = gitLabClient.fetchMergeRequestsByState(owner, repository, branchName, state, token, since, until, repositoryUrl);
            List<MergeRequests> mergeRequests = new ArrayList<>();

            for (org.gitlab4j.api.models.MergeRequest gitlabMr : gitlabMergeRequests) {
                try {
                    MergeRequests mergeRequest = convertToMergeRequest(gitlabMr, toolConfigId, owner, repository, token, repositoryUrl);
                    mergeRequests.add(mergeRequest);
                } catch (Exception e) {
                    log.warn("Failed to convert GitLab merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
                }
            }

            log.info("Successfully converted {} {} GitLab merge requests to domain objects", mergeRequests.size(), state);
            return mergeRequests;

        } catch (GitLabApiException e) {
            log.error("Failed to fetch {} merge requests from GitLab repository {}/{}: {}", state, owner, repository, e.getMessage());
            throw new PlatformApiException("GitLab", "Failed to fetch merge requests from GitLab", e);
        }
    }

    @Override
    public List<MergeRequests> fetchLatestMergeRequests(String toolConfigId, String owner, String repository, String branchName,
                                                        String token, int limit) throws PlatformApiException {
        try {
            log.info("Fetching latest {} merge requests for GitLab repository: {}/{} (branch: {})", limit, owner, repository, branchName != null ? branchName : "all");

            String repositoryUrl = getRepositoryUrl(owner, repository);
            log.debug("Using repository URL: {}", repositoryUrl);

            List<org.gitlab4j.api.models.MergeRequest> gitlabMergeRequests = gitLabClient.fetchLatestMergeRequests(owner, repository, token, branchName, limit, repositoryUrl);
            List<MergeRequests> mergeRequests = new ArrayList<>();

            for (org.gitlab4j.api.models.MergeRequest gitlabMr : gitlabMergeRequests) {
                try {
                    MergeRequests mergeRequest = convertToMergeRequest(gitlabMr, toolConfigId, owner, repository, token, repositoryUrl);
                    mergeRequests.add(mergeRequest);
                } catch (Exception e) {
                    log.warn("Failed to convert GitLab merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
                }
            }

            log.info("Successfully converted {} latest GitLab merge requests to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (GitLabApiException e) {
            log.error("Failed to fetch latest merge requests from GitLab repository {}/{}: {}", owner, repository, e.getMessage());
            throw new PlatformApiException("GitLab", "Failed to fetch latest merge requests from GitLab", e);
        }
    }

    @Override
    public boolean testConnection(String token) throws PlatformApiException {
        try {
            return gitLabClient.testConnection(token);
        } catch (GitLabApiException e) {
            throw new PlatformApiException("GitLab", "GitLab connection test failed", e);
        }
    }

    @Override
    public String getPlatformName() {
        return "GitLab";
    }

    @Override
    public String getApiBaseUrl() {
        return gitLabClient.getApiUrl();
    }


    /**
     * Converts a GitLab commit to domain Commit object
     */
    private CommitDetails convertToCommit(org.gitlab4j.api.models.Commit gitlabCommit, String toolConfigId, String owner, String repository, String token, String repositoryUrl) {
        CommitDetails.CommitBuilder builder = CommitDetails.builder()
            .toolConfigId(new ObjectId(toolConfigId))
            .repositoryName(owner + "/" + repository)
            .sha(gitlabCommit.getId())
            .commitMessage(gitlabCommit.getMessage())
            .commitTimestamp(gitlabCommit.getCreatedAt().toInstant().toEpochMilli());

        // Set author information
        if (gitlabCommit.getAuthorName() != null) {
            builder.commitAuthorId(gitlabCommit.getAuthorName())
                   .authorName(gitlabCommit.getAuthorName())
                   .authorEmail(gitlabCommit.getAuthorEmail());
        }

        // Set committer information
        if (gitlabCommit.getCommitterName() != null) {
            builder.committerId(gitlabCommit.getCommitterName())
                   .committerName(gitlabCommit.getCommitterName())
                   .committerEmail(gitlabCommit.getCommitterEmail());
        }

        // Set committer timestamp if available
        if (gitlabCommit.getCommittedDate() != null) {
            builder.committerTimestamp(gitlabCommit.getCommittedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }

        // Set parent SHAs and merge commit flag
        if (gitlabCommit.getParentIds() != null && !gitlabCommit.getParentIds().isEmpty()) {
            builder.parentShas(gitlabCommit.getParentIds())
                   .isMergeCommit(gitlabCommit.getParentIds().size() > 1);
        }

        // Extract diff statistics and file changes
        GitLabDiffStats diffStats = extractDiffStats(gitlabCommit, owner, repository, token, repositoryUrl);
        builder.addedLines(diffStats.getAddedLines())
               .removedLines(diffStats.getRemovedLines())
               .changedLines(diffStats.getChangedLines())
               .filesChanged(diffStats.getFilesChanged())
               .fileChanges(diffStats.getFileChanges());

        return builder.build();
    }

    /**
     * Converts a GitLab commit to domain Commit object (backward compatibility)
     */
    private CommitDetails convertToCommit(org.gitlab4j.api.models.Commit gitlabCommit, String toolConfigId, String owner, String repository, String token) {
        String repositoryUrl = getRepositoryUrl(owner, repository);
        return convertToCommit(gitlabCommit, toolConfigId, owner, repository, token, repositoryUrl);
    }

    /**
     * Converts a GitLab merge request to domain MergeRequest object
     */
    private MergeRequests convertToMergeRequest(org.gitlab4j.api.models.MergeRequest gitlabMr, String toolConfigId, String owner, String repository, String token, String repositoryUrl) {
        MergeRequests.MergeRequestsBuilder builder = MergeRequests.builder()
            .processorItemId(new ObjectId(toolConfigId))
            .repositoryName(owner + "/" + repository)
            .externalId(gitlabMr.getIid().toString())
            .title(gitlabMr.getTitle())
            .summary(gitlabMr.getDescription())
            .state(gitlabMr.getState().toString().toLowerCase())
            .fromBranch(gitlabMr.getSourceBranch())
            .toBranch(gitlabMr.getTargetBranch())
            .createdDate(gitlabMr.getCreatedAt().toInstant().toEpochMilli())
            .updatedDate(gitlabMr.getUpdatedAt().toInstant().toEpochMilli());

        // Set merge/close timestamps
        if (gitlabMr.getMergedAt() != null) {
            builder.mergedAt(gitlabMr.getMergedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (gitlabMr.getClosedAt() != null) {
            builder.closedDate(gitlabMr.getClosedAt().toInstant().toEpochMilli());
        }

        // Set author information
        if (gitlabMr.getAuthor() != null) {
            builder.authorUserId(gitlabMr.getAuthor().getUsername());
        }

        // Set merge request URL
        if (gitlabMr.getWebUrl() != null) {
            builder.mergeRequestUrl(gitlabMr.getWebUrl());
        }

        // Set draft flag
        if (gitlabMr.getWorkInProgress() != null) {
            builder.isDraft(gitlabMr.getWorkInProgress());
        }

        // Extract merge request statistics
        MergeRequestStats mrStats = extractMergeRequestStats(gitlabMr, owner, repository, token, repositoryUrl);
        builder.linesChanged(mrStats.getLinesChanged())
               .commitCount(mrStats.getCommitCount())
               .filesChanged(mrStats.getFilesChanged())
               .addedLines(mrStats.getAddedLines())
               .removedLines(mrStats.getRemovedLines());

        return builder.build();
    }

    /**
     * Converts a GitLab merge request to domain MergeRequest object (backward compatibility)
     */
    private MergeRequests convertToMergeRequest(org.gitlab4j.api.models.MergeRequest gitlabMr, String toolConfigId, String owner, String repository, String token) {
        String repositoryUrl = getRepositoryUrl(owner, repository);
        return convertToMergeRequest(gitlabMr, toolConfigId, owner, repository, token, repositoryUrl);
    }

    /**
     * Extracts diff statistics from a GitLab commit
     */
    private GitLabDiffStats extractDiffStats(org.gitlab4j.api.models.Commit gitlabCommit, String owner, String repository, String token, String repositoryUrl) {
        try {
            // First try to get stats from the commit object itself
            if (gitlabCommit.getStats() != null) {
                int additions = gitlabCommit.getStats().getAdditions();
                int deletions = gitlabCommit.getStats().getDeletions();
                int total = gitlabCommit.getStats().getTotal();

                // Try to get detailed file changes via additional API call
                List<CommitDetails.FileChange> fileChanges = extractFileChangesFromCommit(gitlabCommit, owner, repository, token, repositoryUrl);

                return new GitLabDiffStats(additions, deletions, total, fileChanges.size(), fileChanges);
            }

            // Fallback: try to get file changes without stats
            List<CommitDetails.FileChange> fileChanges = extractFileChangesFromCommit(gitlabCommit, owner, repository, token, repositoryUrl);
            int totalAdditions = fileChanges.stream().mapToInt(fc -> fc.getAddedLines() != null ? fc.getAddedLines() : 0).sum();
            int totalDeletions = fileChanges.stream().mapToInt(fc -> fc.getRemovedLines() != null ? fc.getRemovedLines() : 0).sum();
            int totalChanges = fileChanges.stream().mapToInt(fc -> fc.getChangedLines() != null ? fc.getChangedLines() : 0).sum();

            return new GitLabDiffStats(totalAdditions, totalDeletions, totalChanges, fileChanges.size(), fileChanges);

        } catch (Exception e) {
            log.warn("Failed to extract diff stats from commit {}: {}", gitlabCommit.getId(), e.getMessage());
            return new GitLabDiffStats(0, 0, 0, 0, new ArrayList<>());
        }
    }

    /**
     * Extracts diff stats from a GitLab commit (backward compatibility)
     */
    private GitLabDiffStats extractDiffStats(org.gitlab4j.api.models.Commit gitlabCommit, String owner, String repository, String token) {
        String repositoryUrl = getRepositoryUrl(owner, repository);
        return extractDiffStats(gitlabCommit, owner, repository, token, repositoryUrl);
    }

    /**
     * Extracts file changes from a GitLab commit using additional API calls
     */
    private List<CommitDetails.FileChange> extractFileChangesFromCommit(org.gitlab4j.api.models.Commit gitlabCommit, String owner, String repository, String token, String repositoryUrl) {
        List<CommitDetails.FileChange> fileChanges = new ArrayList<>();

        try {
            // Fetch commit diffs from GitLab API
            List<Diff> diffs = gitLabClient.fetchCommitDiffs(owner, repository, gitlabCommit.getId(), token, repositoryUrl);

            for (Diff diff : diffs) {
                try {
                    CommitDetails.FileChange fileChange = convertDiffToFileChange(diff);
                    if (fileChange != null) {
                        fileChanges.add(fileChange);
                    }
                } catch (Exception e) {
                    log.debug("Failed to convert diff for file {}: {}", diff.getNewPath(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.debug("Could not extract detailed file changes for commit {}: {}", gitlabCommit.getId(), e.getMessage());
        }

        return fileChanges;
    }

    /**
     * Extracts file changes from a GitLab commit (backward compatibility)
     */
    private List<CommitDetails.FileChange> extractFileChangesFromCommit(org.gitlab4j.api.models.Commit gitlabCommit, String owner, String repository, String token) {
        String repositoryUrl = getRepositoryUrl(owner, repository);
        return extractFileChangesFromCommit(gitlabCommit, owner, repository, token, repositoryUrl);
    }

    /**
     * Converts a GitLab Diff to a FileChange object
     */
    private CommitDetails.FileChange convertDiffToFileChange(Diff diff) {
        if (diff == null) return null;
        
        String filePath = diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
        if (filePath == null) return null;
        
        // Parse diff content to extract line changes
        String diffContent = diff.getDiff();
        DiffStats stats = parseDiffContent(diffContent);
        
        return CommitDetails.FileChange.builder()
            .filePath(filePath)
            .changeType(mapGitLabStatus(determineChangeType(diff)))
            .addedLines(stats.getAddedLines())
            .removedLines(stats.getRemovedLines())
            .changedLines(stats.getAddedLines() + stats.getRemovedLines())
            .isBinary(isBinaryFile(filePath))
            .changedLineNumbers(extractLineNumbers(diffContent))
            .build();
    }

    /**
     * Determines the change type from a GitLab Diff
     */
    private String determineChangeType(Diff diff) {
        if (diff.getNewFile() != null && diff.getNewFile()) {
            return "new";
        } else if (diff.getDeletedFile() != null && diff.getDeletedFile()) {
            return "deleted";
        } else if (diff.getRenamedFile() != null && diff.getRenamedFile()) {
            return "renamed";
        } else {
            return "modified";
        }
    }

    /**
     * Parses diff content to extract line statistics
     */
    private DiffStats parseDiffContent(String diffContent) {
        if (diffContent == null || diffContent.isEmpty()) {
            return new DiffStats(0, 0);
        }
        
        int addedLines = 0;
        int removedLines = 0;
        
        String[] lines = diffContent.split("\n");
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                addedLines++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removedLines++;
            }
        }
        
        return new DiffStats(addedLines, removedLines);
    }

    /**
     * Extracts statistics from a GitLab merge request
     */
    private MergeRequestStats extractMergeRequestStats(org.gitlab4j.api.models.MergeRequest gitlabMr, String owner, String repository, String token, String repositoryUrl) {
        try {
            int linesChanged = 0;
            int commitCount = 0;
            int filesChanged = 0;
            int addedLines = 0;
            int removedLines = 0;

            // Try to get merge request changes
            try {
                List<Diff> changes = gitLabClient.fetchMergeRequestChanges(owner, repository, gitlabMr.getIid(), token, repositoryUrl);

                for (Diff diff : changes) {
                    if (diff.getDiff() != null) {
                        String[] lines = diff.getDiff().split("\n");
                        for (String line : lines) {
                            if (line.startsWith("+") && !line.startsWith("+++")) {
                                addedLines++;
                            } else if (line.startsWith("-") && !line.startsWith("---")) {
                                removedLines++;
                            }
                        }
                        linesChanged += addedLines + removedLines;
                        filesChanged++;
                    }
                }

            } catch (Exception e) {
                log.debug("Could not fetch detailed changes for merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
            }

            // Try to get commit count
            try {
                List<org.gitlab4j.api.models.Commit> commits = gitLabClient.fetchMergeRequestCommits(owner, repository, gitlabMr.getIid(), token, repositoryUrl);
                commitCount = commits.size();
            } catch (Exception e) {
                log.debug("Could not fetch commits for merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
            }

            // Fallback to basic stats if available
            if (linesChanged == 0 && gitlabMr.getChangesCount() != null) {
                try {
                    linesChanged = Integer.parseInt(gitlabMr.getChangesCount());
                } catch (NumberFormatException e) {
                    log.debug("Could not parse changes count '{}' for merge request !{}",
                             gitlabMr.getChangesCount(), gitlabMr.getIid());
                }
            }

            return new MergeRequestStats(linesChanged, commitCount, filesChanged, addedLines, removedLines);

        } catch (Exception e) {
            log.warn("Failed to extract stats from merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
            return new MergeRequestStats(0, 0, 0, 0, 0);
        }
    }

    /**
     * Extracts merge request stats (backward compatibility)
     */
    private MergeRequestStats extractMergeRequestStats(org.gitlab4j.api.models.MergeRequest gitlabMr, String owner, String repository, String token) {
        String repositoryUrl = getRepositoryUrl(owner, repository);
        return extractMergeRequestStats(gitlabMr, owner, repository, token, repositoryUrl);
    }

    /**
     * Maps GitLab diff status to our change type
     */
    private String mapGitLabStatus(String status) {
        if (status == null) return "MODIFIED";
        
        switch (status.toLowerCase()) {
            case "new": return "ADDED";
            case "deleted": return "DELETED";
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
     * Helper class for diff statistics
     */
    private static class DiffStats {
        private final int addedLines;
        private final int removedLines;

        public DiffStats(int addedLines, int removedLines) {
            this.addedLines = addedLines;
            this.removedLines = removedLines;
        }

        public int getAddedLines() { return addedLines; }
        public int getRemovedLines() { return removedLines; }
    }

    /**
     * Helper class for GitLab diff statistics
     */
    private static class GitLabDiffStats {
        private final int addedLines;
        private final int removedLines;
        private final int changedLines;
        private final int filesChanged;
        private final List<CommitDetails.FileChange> fileChanges;

        public GitLabDiffStats(int addedLines, int removedLines, int changedLines, int filesChanged, List<CommitDetails.FileChange> fileChanges) {
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
        public List<CommitDetails.FileChange> getFileChanges() { return fileChanges; }
    }

    /**
     * Helper class for merge request statistics
     */
    private static class MergeRequestStats {
        private final int linesChanged;
        private final int commitCount;
        private final int filesChanged;
        private final int addedLines;
        private final int removedLines;

        public MergeRequestStats(int linesChanged, int commitCount, int filesChanged, int addedLines, int removedLines) {
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