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
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformCommitsService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GitHubCommitsServiceImpl implements GitPlatformCommitsService {

    private static final String DEFAULT_BRANCH_NAME = "main";
    private static final String PLATFORM_NAME = "GitHub";

    private final GitHubClient gitHubClient;
    private final GitHubCommonHelper commonHelper;

    @Autowired
    public GitHubCommitsServiceImpl(GitHubClient gitHubClient, GitHubCommonHelper commonHelper) {
        this.gitHubClient = gitHubClient;
        this.commonHelper = commonHelper;
    }

    public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching commits for GitHub repository: {}/{}", gitUrlInfo.getOwner(),
                    gitUrlInfo.getRepositoryName());
            String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();
            List<GHCommit> ghCommits = gitHubClient.fetchCommits(owner, gitUrlInfo.getRepositoryName(), branchName,
                    token, since, until);
            List<ScmCommits> commitDetails = new ArrayList<>();

            for (GHCommit ghCommit : ghCommits) {
                processCommit(ghCommit, toolConfigId, owner, gitUrlInfo.getRepositoryName(), commitDetails);
            }

            log.info("Successfully converted {} GitHub commits to domain objects", commitDetails.size());
            return commitDetails;

        } catch (IOException e) {
            log.error("Failed to fetch commits from GitHub repository {}/{}: {}", gitUrlInfo.getOwner(),
                    gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commits from GitHub", e);
        }
    }

    private void processCommit(GHCommit ghCommit, String toolConfigId, String owner, String repositoryName,
                               List<ScmCommits> commitDetails) {
        try {
            ScmCommits commitDetail = convertToCommit(ghCommit, toolConfigId, owner, repositoryName);
            commitDetails.add(commitDetail);
        } catch (Exception e) {
            log.warn("Failed to convert GitHub commit {}: {}", ghCommit.getSHA1(), e.getMessage());
        }
    }

    private ScmCommits convertToCommit(GHCommit ghCommit, String toolConfigId, String owner, String repository)
            throws IOException {
        ScmCommits.ScmCommitsBuilder builder = ScmCommits.builder().processorItemId(new ObjectId(toolConfigId))
                .repositoryName(owner + "/" + repository).sha(ghCommit.getSHA1())
                .commitMessage(ghCommit.getCommitShortInfo().getMessage())
                .commitTimestamp(ghCommit.getCommitDate().toInstant().toEpochMilli());

        setCommitAuthor(builder, ghCommit);
        builder.branchName(DEFAULT_BRANCH_NAME);

        GitHubDiffStats diffStats = extractDiffStats(ghCommit);
        builder.addedLines(diffStats.getAddedLines()).removedLines(diffStats.getRemovedLines())
                .changedLines(diffStats.getChangedLines()).filesChanged(diffStats.getFilesChanged())
                .fileChanges(diffStats.getFileChanges());

        setParentInformation(builder, ghCommit);

        return builder.build();
    }

    private void setCommitAuthor(ScmCommits.ScmCommitsBuilder builder, GHCommit ghCommit) throws IOException {
        GHUser user = ghCommit.getAuthor() != null ? ghCommit.getAuthor() : ghCommit.getCommitter();

        if (user != null) {
            User commitUser = commonHelper.createUser(user);
            builder.commitAuthor(commitUser).authorName(user.getLogin());
        }
    }

    private void setParentInformation(ScmCommits.ScmCommitsBuilder builder, GHCommit ghCommit) {
        try {
            List<String> parentShas = new ArrayList<>(ghCommit.getParentSHA1s());
            builder.parentShas(parentShas).isMergeCommit(parentShas.size() > 1);
        } catch (Exception e) {
            log.debug("Could not get parent SHAs for commit {}", ghCommit.getSHA1());
        }
    }

    private GitHubDiffStats extractDiffStats(GHCommit ghCommit) {
        try {
            List<GHCommit.File> files = ghCommit.getFiles();

            int totalAdditions = 0;
            int totalDeletions = 0;
            int totalChanges = 0;
            List<ScmCommits.FileChange> fileChanges = new ArrayList<>();

            for (GHCommit.File file : files) {
                GitHubCommonHelper.FileChangeStats stats = commonHelper.processFileChange(file);

                totalAdditions += stats.getAdditions();
                totalDeletions += stats.getDeletions();
                totalChanges += stats.getChanges();
                fileChanges.add(stats.getFileChange());
            }

            return new GitHubDiffStats(totalAdditions, totalDeletions, totalChanges, files.size(), fileChanges);

        } catch (Exception e) {
            log.warn("Failed to extract diff stats from commit {}: {}", ghCommit.getSHA1(), e.getMessage());
            return new GitHubDiffStats(0, 0, 0, 0, new ArrayList<>());
        }
    }

    @Getter
    static class GitHubDiffStats {
        private final int addedLines;
        private final int removedLines;
        private final int changedLines;
        private final int filesChanged;
        private final List<ScmCommits.FileChange> fileChanges;

        public GitHubDiffStats(int addedLines, int removedLines, int changedLines, int filesChanged,
                               List<ScmCommits.FileChange> fileChanges) {
            this.addedLines = addedLines;
            this.removedLines = removedLines;
            this.changedLines = changedLines;
            this.filesChanged = filesChanged;
            this.fileChanges = fileChanges;
        }
    }
}
