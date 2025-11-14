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
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.exception.RepositoryException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubRepositoryServiceImpl implements GitPlatformRepositoryService {
	private final GitHubClient gitHubClient;
	private final GitUrlParser gitUrlParser;

	public GitHubRepositoryServiceImpl(GitHubClient gitHubClient, GitUrlParser gitUrlParser) {
		this.gitHubClient = gitHubClient;
        this.gitUrlParser = gitUrlParser;
	}

	@Override
	public List<ScmRepos> fetchRepositories(ScanRequest scanRequest) throws PlatformApiException {
		List<ScmRepos> repositoriesList = new ArrayList<>();
		try {
			List<GHRepository> repositories = gitHubClient.fetchRepositories(scanRequest.getToken(), scanRequest.getSince());
			repositories.forEach(repository -> {
				GitUrlParser.GitUrlInfo gitUrlInfo = gitUrlParser.parseGitUrl(repository.getHtmlUrl().toString(),
                        scanRequest.getToolType(), scanRequest.getUsername(), repository.getName());
				// Fetch branches for each repository
				try {
                    List<ScmBranch> branches = gitHubClient.fetchBranchesWithLastCommitDate(gitUrlInfo.getOwner(),
							gitUrlInfo.getRepositoryName(), scanRequest.getToken(), scanRequest.getSince());
					if (!CollectionUtils.isEmpty(branches)) {
						repositoriesList.add(ScmRepos.builder().url(repository.getHtmlUrl().toString())
								.repositoryName(gitUrlInfo.getRepositoryName())
								.lastUpdated(repository.getUpdatedAt().getTime()).branchList(branches)
								.connectionId(scanRequest.getConnectionId()).build());
					}
				} catch (IOException e) {
					throw new RepositoryException("Error while fetching repositories", e);
				}

			});
		} catch (Exception e) {
			throw new PlatformApiException("Error while fetching repositories", e.getMessage());
		}
		return repositoriesList;
	}

}
