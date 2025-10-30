package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.exception.RepositoryException;
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
public class BitbucketRepositoryServiceImpl implements GitPlatformRepositoryService {

	private final BitbucketClient bitbucketClient;

	public BitbucketRepositoryServiceImpl(BitbucketClient bitbucketClient) {
		this.bitbucketClient = bitbucketClient;
	}

	@Override
	public List<ScmRepos> fetchRepositories(ScanRequest scanRequest) throws PlatformApiException {
		List<ScmRepos> repositoriesList;
		try {
			repositoriesList = bitbucketClient.fetchRepositories(scanRequest.getBaseUrl(), scanRequest.getUsername(),
					scanRequest.getToken(), scanRequest.getSince(), scanRequest.getConnectionId());

		} catch (Exception e) {
			throw new PlatformApiException("Error while fetching repositories", e.getMessage());
		}
		return repositoriesList;
	}
}
