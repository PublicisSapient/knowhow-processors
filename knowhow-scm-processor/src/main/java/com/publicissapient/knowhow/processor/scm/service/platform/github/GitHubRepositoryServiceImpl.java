package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubRepositoryServiceImpl implements GitPlatformRepositoryService {
	private final GitHubClient gitHubClient;
	private final GitUrlParser gitUrlParser = new GitUrlParser();

	public GitHubRepositoryServiceImpl(GitHubClient gitHubClient) {
		this.gitHubClient = gitHubClient;
	}

	@Override
	public List<ScmRepos> fetchRepositories(ObjectId connectionId, String username, String toolType, String token,
                                            LocalDateTime since) throws PlatformApiException {
		List<ScmRepos> repositoriesList = new ArrayList<>();
		try {
			List<GHRepository> repositories = gitHubClient.fetchRepositories(token, since);
			repositories.forEach(repository -> {
				GitUrlParser.GitUrlInfo gitUrlInfo = gitUrlParser.parseGitUrl(repository.getHtmlUrl().toString(),
						toolType, username, repository.getName());
				// Fetch branches for each repository
				try {
                    List<ScmBranch> branches = gitHubClient.fetchBranchesWithLastCommitDate(gitUrlInfo.getOwner(),
							gitUrlInfo.getRepositoryName(), token, since);
					if (!CollectionUtils.isEmpty(branches)) {
						repositoriesList.add(ScmRepos.builder().url(repository.getHtmlUrl().toString())
								.repositoryName(gitUrlInfo.getRepositoryName())
								.lastUpdated(repository.getUpdatedAt().getTime()).branchList(branches)
								.connectionId(connectionId).build());
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

			});
		} catch (Exception e) {
			throw new PlatformApiException("Error while fetching repositories", e.getMessage());
		}
		return repositoriesList;
	}

}
