package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.knowhow.processor.scm.service.strategy.CommitDataFetchStrategy;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AzureDevOpsRepositoryServiceImpl implements GitPlatformRepositoryService {

	private final AzureDevOpsClient azureDevOpsClient;
	private final GitUrlParser gitUrlParser;

	public AzureDevOpsRepositoryServiceImpl(AzureDevOpsClient azureDevOpsClient, GitUrlParser gitUrlParser) {
		this.azureDevOpsClient = azureDevOpsClient;
		this.gitUrlParser = gitUrlParser;
	}

	@Override
	public List<ScmRepos> fetchRepositories(ScanRequest scanRequest) throws PlatformApiException {
		List<ScmRepos> scmRepos;
		try {
			GitUrlParser.GitUrlInfo urlInfo = gitUrlParser.parseGitUrl(scanRequest.getRepositoryUrl(),
					scanRequest.getToolType(), scanRequest.getUsername(), scanRequest.getRepositoryName());
			scmRepos = azureDevOpsClient.fetchRepositories(scanRequest.getToken(), urlInfo.getOrganization(),
					scanRequest.getSince(), scanRequest.getConnectionId());

		} catch (Exception e) {
			throw new PlatformApiException("Error while fetching repositories", e.getMessage());
		}
		return scmRepos;
	}

}
