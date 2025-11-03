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
