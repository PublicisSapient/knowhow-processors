package com.publicissapient.knowhow.processor.scm.adapter.impl;

import com.publicissapient.knowhow.processor.scm.adapter.ScmToolAdapter;
import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.kpidashboard.common.model.scm.Repository;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GitHubAdapter implements ScmToolAdapter {

    private final Map<String, String> repoNameToIdCache = new ConcurrentHashMap<>();

    GitHubClient gitHubClient;

    public GitHubAdapter(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    @Override
    public List<Repository> fetchRepositories(ScanRequest scanRequest) {
        List<GHRepository> gitHubRepos = gitHubClient.fetchRepositories(scanRequest);
        List<Repository> repositories = gitHubRepos.stream()
                .map(this::mapToRepository)
                .collect(Collectors.toList());
        repositories.forEach(repo -> {
            if (repo.getRepositoryName() != null && repo.getRepositoryUrl() != null) {
                repoNameToIdCache.put(repo.getRepositoryName(), repo.getRepositoryUrl());
            }
        });
        return repositories;
    }

    @Override
    public void setActiveBranches(List<Repository> repositories) {
        return;
    }

    private Repository mapToRepository(GHRepository data) {
        Repository repository = new Repository();
        repository.setRepositoryName(data.getName());
        repository.setRepositoryUrl(data.getGitTransportUrl());
        return repository;
    }
}
