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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;

@Service
public class AzureDevOpsRepositoryServiceImpl implements GitPlatformRepositoryService {

	private final AzureDevOpsClient azureDevOpsClient;
	private final GitUrlParser gitUrlParser;

    private static final Pattern AZURE_DEVOPS_PATTERN = Pattern
            .compile("https?://(?:[\\w.-]+@)?dev\\.azure\\.com/([^/]+)/([^/]+)");

	public AzureDevOpsRepositoryServiceImpl(AzureDevOpsClient azureDevOpsClient, GitUrlParser gitUrlParser) {
		this.azureDevOpsClient = azureDevOpsClient;
		this.gitUrlParser = gitUrlParser;
	}

	@Override
	public List<ScmRepos> fetchRepositories(ScanRequest scanRequest) throws PlatformApiException {
		List<ScmRepos> scmRepos;
		try {
            Matcher azureMatcher = AZURE_DEVOPS_PATTERN.matcher(scanRequest.getBaseUrl());
            if (!azureMatcher.matches()) {
                throw new PlatformApiException("Invalid Azure DevOps URL", "The provided URL is not a valid Azure DevOps URL");
            }
			String organization = azureMatcher.group(1);
			scmRepos = azureDevOpsClient.fetchRepositories(scanRequest.getToken(), organization,
					scanRequest.getSince(), scanRequest.getConnectionId());

		} catch (Exception e) {
			throw new PlatformApiException("Error while fetching repositories", e.getMessage());
		}
		return scmRepos;
	}

}
