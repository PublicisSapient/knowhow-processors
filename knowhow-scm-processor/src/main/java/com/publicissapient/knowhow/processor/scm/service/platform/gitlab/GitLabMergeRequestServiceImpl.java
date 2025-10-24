package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformMergeRequestService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class GitLabMergeRequestServiceImpl implements GitPlatformMergeRequestService {

    private static final String PLATFORM_NAME = "GitLab";
    private static final String PATH_SEPARATOR = "/";

    private final GitLabClient gitLabClient;
    private final GitLabCommonHelper commonHelper;

    @Value("${git.platforms.gitlab.api-url:https://gitlab.com}")
    private String defaultGitlabApiUrl;

    private static final ThreadLocal<String> currentRepositoryUrl = new ThreadLocal<>();

    public GitLabMergeRequestServiceImpl(GitLabClient gitLabClient, GitLabCommonHelper commonHelper) {
        this.gitLabClient = gitLabClient;
        this.commonHelper = commonHelper;
    }

    public void setRepositoryUrlContext(String repositoryUrl) {
        currentRepositoryUrl.set(repositoryUrl);
    }

    public void clearRepositoryUrlContext() {
        currentRepositoryUrl.remove();
    }

    @Override
    public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
                                                     String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching merge requests for GitLab repository: {}/{} (branch: {})", gitUrlInfo.getOwner(),
                    gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");

            String repositoryUrl = getRepositoryUrl(gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());
            String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();

            List<MergeRequest> gitlabMergeRequests = gitLabClient.fetchMergeRequests(owner,
                    gitUrlInfo.getRepositoryName(), branchName, token, since, until, repositoryUrl);

            List<ScmMergeRequests> mergeRequests = new ArrayList<>();
            for (MergeRequest gitlabMr : gitlabMergeRequests) {
                try {
                    ScmMergeRequests mergeRequest = convertToMergeRequest(gitlabMr, toolConfigId, owner,
                            gitUrlInfo.getRepositoryName(), token, repositoryUrl);
                    mergeRequests.add(mergeRequest);
                } catch (Exception e) {
                    log.warn("Failed to convert GitLab merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
                }
            }

            log.info("Successfully converted {} GitLab merge requests to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (GitLabApiException e) {
            log.error("Failed to fetch merge requests from GitLab repository {}/{}: {}", gitUrlInfo.getOwner(),
                    gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch merge requests from GitLab", e);
        }
    }

    private ScmMergeRequests convertToMergeRequest(MergeRequest gitlabMr, String toolConfigId, String owner,
                                                   String repository, String token, String repositoryUrl) throws GitLabApiException {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder()
                .processorItemId(new ObjectId(toolConfigId))
                .repositoryName(owner + PATH_SEPARATOR + repository)
                .externalId(gitlabMr.getIid().toString())
                .title(gitlabMr.getTitle())
                .summary(gitlabMr.getDescription())
                .state(gitlabMr.getState().toLowerCase())
                .fromBranch(gitlabMr.getSourceBranch())
                .toBranch(gitlabMr.getTargetBranch())
                .createdDate(gitlabMr.getCreatedAt().toInstant().toEpochMilli())
                .updatedDate(gitlabMr.getUpdatedAt().toInstant().toEpochMilli());

        commonHelper.setMergeRequestState(builder, gitlabMr);
        commonHelper.setMergeRequestTimestamps(builder, gitlabMr);
        commonHelper.setMergeRequestAuthor(builder, gitlabMr);

        if (gitlabMr.getWebUrl() != null) {
            builder.mergeRequestUrl(gitlabMr.getWebUrl());
        }
        if (gitlabMr.getWorkInProgress() != null) {
            builder.isDraft(gitlabMr.getWorkInProgress());
        }

        long pickUpTime = gitLabClient.getPrPickUpTimeStamp(owner, repository, token, repositoryUrl, gitlabMr.getIid());
        builder.pickedForReviewOn(pickUpTime);

        MergeRequestStats mrStats = extractMergeRequestStats(gitlabMr, owner, repository, token, repositoryUrl);
        builder.linesChanged(mrStats.getLinesChanged())
                .commitCount(mrStats.getCommitCount())
                .filesChanged(mrStats.getFilesChanged())
                .addedLines(mrStats.getAddedLines())
                .removedLines(mrStats.getRemovedLines());

        return builder.build();
    }

    private MergeRequestStats extractMergeRequestStats(MergeRequest gitlabMr, String owner, String repository,
                                                       String token, String repositoryUrl) {
        try {
            MergeRequestStatsBuilder statsBuilder = new MergeRequestStatsBuilder();

            try {
                List<Diff> changes = gitLabClient.fetchMergeRequestChanges(owner, repository, gitlabMr.getIid(), token, repositoryUrl);
                for (Diff diff : changes) {
                    if (diff.getDiff() != null) {
                        GitLabCommonHelper.DiffStats diffStats = commonHelper.parseDiffContent(diff.getDiff());
                        statsBuilder.addAddedLines(diffStats.getAddedLines());
                        statsBuilder.addRemovedLines(diffStats.getRemovedLines());
                        statsBuilder.incrementFilesChanged();
                    }
                }
            } catch (Exception e) {
                log.debug("Could not fetch detailed changes for merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
            }

            try {
                List<Commit> commits = gitLabClient.fetchMergeRequestCommits(owner, repository, gitlabMr.getIid(), token, repositoryUrl);
                statsBuilder.setCommitCount(commits.size());
            } catch (Exception e) {
                log.debug("Could not fetch commits for merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
            }

            if (statsBuilder.getLinesChanged() == 0 && gitlabMr.getChangesCount() != null) {
                try {
                    statsBuilder.setLinesChanged(Integer.parseInt(gitlabMr.getChangesCount()));
                } catch (NumberFormatException e) {
                    log.debug("Could not parse changes count '{}' for merge request !{}", gitlabMr.getChangesCount(), gitlabMr.getIid());
                }
            }

            return statsBuilder.build();
        } catch (Exception e) {
            log.warn("Failed to extract stats from merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
            return new MergeRequestStats(0, 0, 0, 0, 0);
        }
    }

    private String getRepositoryUrl(String owner, String repository) {
        String contextUrl = currentRepositoryUrl.get();
        if (contextUrl != null && !contextUrl.trim().isEmpty()) {
            return contextUrl;
        }
        return defaultGitlabApiUrl + PATH_SEPARATOR + owner + PATH_SEPARATOR + repository;
    }

    @Getter
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
    }

    private static class MergeRequestStatsBuilder {
        @Getter
        @Setter
        private int linesChanged = 0;
        @Setter
        private int commitCount = 0;
        private int filesChanged = 0;
        private int addedLines = 0;
        private int removedLines = 0;

        public void addAddedLines(int lines) {
            this.addedLines += lines;
            this.linesChanged += lines;
        }

        public void addRemovedLines(int lines) {
            this.removedLines += lines;
            this.linesChanged += lines;
        }

        public void incrementFilesChanged() {
            this.filesChanged++;
        }

        public MergeRequestStats build() {
            return new MergeRequestStats(linesChanged, commitCount, filesChanged, addedLines, removedLines);
        }
    }
}
