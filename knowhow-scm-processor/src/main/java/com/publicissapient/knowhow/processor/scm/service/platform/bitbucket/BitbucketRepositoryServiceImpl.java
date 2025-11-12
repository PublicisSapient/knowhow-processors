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

package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.springframework.stereotype.Service;

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
