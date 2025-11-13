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

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformMergeRequestService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.kohsuke.github.GHPullRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GitHubMergeRequestServiceImpl implements GitPlatformMergeRequestService {

    private static final String PLATFORM_NAME = "GitHub";

    private final GitHubClient gitHubClient;
    private final GitHubCommonHelper commonHelper;

    public GitHubMergeRequestServiceImpl(GitHubClient gitHubClient, GitHubCommonHelper commonHelper) {
        this.gitHubClient = gitHubClient;
        this.commonHelper = commonHelper;
    }

    @Override
    public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching merge requests for GitHub repository: {}/{} (branch: {})", gitUrlInfo.getOwner(),
                    gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");

            List<GHPullRequest> ghPullRequests = gitHubClient.fetchPullRequests(gitUrlInfo.getOwner(),
                    gitUrlInfo.getRepositoryName(), token, since, until);
            List<ScmMergeRequests> mergeRequests = new ArrayList<>();

            if (branchName != null && !branchName.trim().isEmpty()) {
                ghPullRequests = filterPullRequestsByBranch(ghPullRequests, branchName);
            }

            for (GHPullRequest ghPr : ghPullRequests) {
                processPullRequest(ghPr, toolConfigId, mergeRequests);
            }

            log.info("Successfully converted {} GitHub pull requests to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (IOException e) {
            log.error("Failed to fetch merge requests from GitHub repository {}/{}: {}", gitUrlInfo.getOwner(),
                    gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch merge requests from GitHub", e);
        }
    }

    private void processPullRequest(GHPullRequest ghPr, String toolConfigId, List<ScmMergeRequests> mergeRequests) {
        try {
            ScmMergeRequests mergeRequest = convertToMergeRequest(ghPr, toolConfigId);
            mergeRequests.add(mergeRequest);
        } catch (Exception e) {
            log.warn("Failed to convert GitHub pull request #{}: {}", ghPr.getNumber(), e.getMessage());
        }
    }

    private ScmMergeRequests convertToMergeRequest(GHPullRequest ghPr, String toolConfigId) throws IOException {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder()
                .processorItemId(new ObjectId(toolConfigId)).repositoryName(ghPr.getRepository().getFullName())
                .externalId(String.valueOf(ghPr.getNumber())).title(ghPr.getTitle()).summary(ghPr.getBody())
                .fromBranch(ghPr.getHead().getRef()).toBranch(ghPr.getBase().getRef())
                .createdDate(ghPr.getCreatedAt() != null ? ghPr.getCreatedAt().toInstant().toEpochMilli() : null)
                .updatedDate(ghPr.getUpdatedAt().toInstant().toEpochMilli());

        commonHelper.setPullRequestState(builder, ghPr);
        commonHelper.setMergeAndCloseTimestamps(builder, ghPr);
        commonHelper.setPullRequestAuthor(builder, ghPr);

        builder.mergeRequestUrl(ghPr.getHtmlUrl().toString());

        GitHubCommonHelper.PullRequestStats prStats = commonHelper.extractPullRequestStats(ghPr);
        builder.linesChanged(prStats.getLinesChanged()).commitCount(prStats.getCommitCount())
                .filesChanged(prStats.getFilesChanged()).addedLines(prStats.getAddedLines())
                .removedLines(prStats.getRemovedLines());

        builder.pickedForReviewOn(commonHelper.getPrPickupTime(ghPr));

        return builder.build();
    }

    private List<GHPullRequest> filterPullRequestsByBranch(List<GHPullRequest> pullRequests, String branchName) {
        return pullRequests.stream().filter(pr -> {
            try {
                String baseBranch = pr.getBase().getRef();
                return baseBranch != null && baseBranch.equals(branchName);
            } catch (Exception e) {
                log.warn("Failed to get base branch for PR #{}: {}", pr.getNumber(), e.getMessage());
                return false;
            }
        }).toList();
    }

}
