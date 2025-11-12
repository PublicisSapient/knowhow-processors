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

package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.constants.ScmConstants;
import com.publicissapient.knowhow.processor.scm.service.platform.CommitsServiceLocator;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformCommitsService;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API-based implementation of CommitDataFetchStrategy. This strategy
 * fetches commit data using platform-specific REST APIs without cloning the
 * repository locally. Advantages: - Faster for small datasets - No local disk
 * space required - Platform-specific optimizations Disadvantages: - API rate
 * limiting - Limited by platform API capabilities - Requires platform-specific
 * implementations
 */
@Component("restApiCommitDataFetchStrategy")
@Slf4j
public class RestApiCommitDataFetchStrategy implements CommitDataFetchStrategy {

	private final CommitsServiceLocator commitsServiceLocator;

	public RestApiCommitDataFetchStrategy(CommitsServiceLocator commitsServiceLocator) {
		this.commitsServiceLocator = commitsServiceLocator;
	}

	@Override
	public List<ScmCommits> fetchCommits(String toolType, String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, RepositoryCredentials credentials, LocalDateTime since) throws DataProcessingException {

		log.info("Fetching commits using REST API strategy for repository: {}", gitUrlInfo.getOriginalUrl());

		try {
			GitPlatformCommitsService commitsService = commitsServiceLocator.getCommitsService(toolType);
			if (commitsService == null) {
				throw new DataProcessingException("No commits service found for toolType: " + toolType);
			}

			String token = toolType.equalsIgnoreCase(ScmConstants.BITBUCKET)
					? credentials.getUsername() + ":" + credentials.getToken()
					: credentials.getToken();

			List<ScmCommits> commitDetails = commitsService.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, null);

			log.info("Successfully fetched {} commits from repository: {}", commitDetails.size(),
					gitUrlInfo.getOriginalUrl());
			return commitDetails;

		} catch (Exception e) {
			log.error("Error fetching commits from repository {}: {}", gitUrlInfo.getOriginalUrl(), e.getMessage(), e);
			throw new DataProcessingException("Failed to fetch commits using REST API strategy", e);
		}
	}

	@Override
	public boolean supports(String repositoryUrl, String toolType) {
		return commitsServiceLocator.getCommitsService(toolType) != null;
	}

	@Override
	public String getStrategyName() {
		return "REST_API";
	}


}