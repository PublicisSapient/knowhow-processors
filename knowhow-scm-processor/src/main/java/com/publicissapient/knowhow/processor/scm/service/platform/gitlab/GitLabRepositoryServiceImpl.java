package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.gitlab4j.api.GitLabApiException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GitLabRepositoryServiceImpl implements GitPlatformRepositoryService {

    private final GitLabClient gitLabClient;

    public GitLabRepositoryServiceImpl(GitLabClient gitLabClient) {
        this.gitLabClient = gitLabClient;
    }

    @Override
    public List<ScmRepos> fetchRepositories(ScanRequest scanRequest) throws PlatformApiException {

        List<ScmRepos> scmReposList;

        try {
            scmReposList = gitLabClient.fetchRepositories(scanRequest.getToken(), scanRequest.getSince(), scanRequest.getBaseUrl(), scanRequest.getConnectionId());

        } catch (GitLabApiException e) {
            throw new PlatformApiException("Error while fetching repositories", e.getMessage());
        }
        return scmReposList;
    }
}
