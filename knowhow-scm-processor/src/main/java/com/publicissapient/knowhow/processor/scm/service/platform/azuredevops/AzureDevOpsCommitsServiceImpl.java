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

package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.azd.git.types.GitCommitRef;
import org.azd.git.types.GitUserDate;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformCommitsService;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AzureDevOpsCommitsServiceImpl implements GitPlatformCommitsService {

    private static final String PLATFORM_NAME = "AzureDevOps";

    private AzureDevOpsClient azureDevOpsClient;
    private RateLimitService rateLimitService;

    @Autowired
    public AzureDevOpsCommitsServiceImpl(AzureDevOpsClient azureDevOpsClient, RateLimitService rateLimitService) {
        this.azureDevOpsClient = azureDevOpsClient;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                         String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching commits for Azure DevOps repository: {}/{}/{}", gitUrlInfo.getOrganization(),
                    gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName());

            // Check rate limit before making API calls
            rateLimitService.checkRateLimit(PLATFORM_NAME, token, gitUrlInfo.getRepositoryName(),
                    gitUrlInfo.getOriginalUrl());

            // CHANGE: Extract repository details to reduce complexity
            RepositoryDetails repoDetails = extractRepositoryDetails(gitUrlInfo);

            List<GitCommitRef> azureCommits = azureDevOpsClient.fetchCommits(repoDetails.organization(),
                    repoDetails.project(), repoDetails.repository(), branchName, token, since, until);

            // CHANGE: Extract commit conversion to separate method
            List<ScmCommits> commitDetails = convertAzureCommitsToScmCommits(azureCommits, toolConfigId, repoDetails,
                    branchName);

            log.info("Successfully converted {} Azure DevOps commits to domain objects", commitDetails.size());
            return commitDetails;

        } catch (Exception e) {
            log.error("Failed to fetch commits from Azure DevOps repository {}/{}/{}: {}", gitUrlInfo.getOrganization(),
                    gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commits from Azure DevOps", e);
        }
    }

    private RepositoryDetails extractRepositoryDetails(GitUrlParser.GitUrlInfo gitUrlInfo) {
        String organization = gitUrlInfo.getOrganization();
        String project = gitUrlInfo.getProject() != null ? gitUrlInfo.getProject() : gitUrlInfo.getRepositoryName();
        String repository = gitUrlInfo.getRepositoryName();
        return new RepositoryDetails(organization, project, repository);
    }

    private List<ScmCommits> convertAzureCommitsToScmCommits(List<GitCommitRef> azureCommits, String toolConfigId,
                                                             RepositoryDetails repoDetails, String branchName) {
        List<ScmCommits> commitDetails = new ArrayList<>();

        for (GitCommitRef azureCommit : azureCommits) {
            try {
                ScmCommits commitDetail = convertToCommit(azureCommit, toolConfigId, repoDetails.organization(),
                        repoDetails.project(), repoDetails.repository(), branchName);
                commitDetails.add(commitDetail);
            } catch (Exception e) {
                log.warn("Failed to convert Azure DevOps commit {}: {}", azureCommit.getCommitId(), e.getMessage());
            }
        }

        return commitDetails;
    }

    /**
     * Converts Azure DevOps GitCommit to ScmCommits domain object.
     */
    private ScmCommits convertToCommit(GitCommitRef azureCommit, String toolConfigId, String organization,
                                       String project, String repository, String branchName) {
        ScmCommits commit = new ScmCommits();

        // Basic commit information
        commit.setId(new ObjectId());
        commit.setProcessorItemId(new ObjectId(toolConfigId));
        commit.setSha(azureCommit.getCommitId());
        commit.setCommitLog(azureCommit.getComment());
        commit.setRepositoryName(organization + "/" + project + "/" + repository);

        // CHANGE: Extract author information setting to reduce complexity
        setCommitAuthorInfo(commit, azureCommit);

        // URL
        if (azureCommit.getUrl() != null) {
            commit.setUrl(azureCommit.getUrl());
        }

        // Set branch
        commit.setBranch(branchName);

        return commit;
    }

    // CHANGE: New method to set commit author information
    private void setCommitAuthorInfo(ScmCommits commit, GitCommitRef azureCommit) {
        if (azureCommit.getAuthor() == null) {
            return;
        }

        GitUserDate author = azureCommit.getAuthor();

        // Create user object
        User user = User.builder().displayName(author.getName()).email(author.getEmail()).username(author.getName())
                .build();

        commit.setCommitAuthor(user);
        commit.setAuthor(author.getName());
        commit.setAuthorEmail(author.getEmail());
        commit.setAuthorName(author.getName());
        commit.setCommitAuthorId(author.getName());
        commit.setCommitterEmail(author.getEmail());

        // Set timestamp
        if (author.getDate() != null) {
            parseAndSetTimestamp(author.getDate()).ifPresent(commit::setCommitTimestamp);
        }
    }

    private Optional<Long> parseAndSetTimestamp(String dateString) {
        try {
            if (dateString != null) {
                Instant instant = Instant.parse(dateString);
                return Optional.of(instant.toEpochMilli());
            }
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateString, e);
        }
        return Optional.empty();
    }

    private record RepositoryDetails(String organization, String project, String repository) {
    }
}
