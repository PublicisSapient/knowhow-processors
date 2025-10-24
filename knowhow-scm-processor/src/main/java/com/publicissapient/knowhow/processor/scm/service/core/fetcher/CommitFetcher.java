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

package com.publicissapient.knowhow.processor.scm.service.core.fetcher;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;

import com.publicissapient.knowhow.processor.scm.service.platform.CommitsServiceLocator;
import com.publicissapient.knowhow.processor.scm.service.strategy.CommitDataFetchStrategy;
import com.publicissapient.knowhow.processor.scm.service.strategy.CommitStrategySelector;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser.GitUrlInfo;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Responsible for fetching commits using appropriate strategies. Follows Single
 * Responsibility Principle.
 */
@Component
@Slf4j
public class CommitFetcher {

	private final CommitStrategySelector strategySelector;
	private final GitUrlParser gitUrlParser;

	@Value("${git.scanner.first-scan-from:6}")
	private int firstScanFromMonths;

	@Autowired
	public CommitFetcher(CommitStrategySelector strategySelector, GitUrlParser gitUrlParser) {
		this.strategySelector = strategySelector;
		this.gitUrlParser = gitUrlParser;
	}

	public List<ScmCommits> fetchCommits(ScanRequest scanRequest) throws DataProcessingException {
		log.debug("Fetching commits for repository: {} ({})", scanRequest.getRepositoryName(),
				scanRequest.getRepositoryUrl());

		CommitDataFetchStrategy strategy = strategySelector.selectStrategy(scanRequest);
		if (strategy == null) {
			throw new DataProcessingException(
					"No suitable commit fetch strategy found for repository: " + scanRequest.getRepositoryUrl());
		}

		log.debug("Using {} strategy for commit fetching", strategy.getStrategyName());

		CommitDataFetchStrategy.RepositoryCredentials credentials = CommitDataFetchStrategy.RepositoryCredentials
				.builder().username(scanRequest.getUsername()).token(scanRequest.getToken()).build();

		LocalDateTime commitsSince = calculateCommitsSince(scanRequest);
		GitUrlInfo urlInfo = parseGitUrl(scanRequest, credentials);

		return strategy.fetchCommits(scanRequest.getToolType(), scanRequest.getToolConfigId().toString(), urlInfo,
				scanRequest.getBranchName(), credentials, commitsSince);
	}

	private LocalDateTime calculateCommitsSince(ScanRequest scanRequest) {
		if (scanRequest.getLastScanFrom() != null && scanRequest.getLastScanFrom() != 0L) {
			LocalDateTime commitsSince = LocalDateTime.ofEpochSecond(scanRequest.getLastScanFrom() / 1000, 0,
					java.time.ZoneOffset.UTC);
			log.debug("Using lastScanFrom timestamp for commits: {}", commitsSince);
			return commitsSince;
		} else if (scanRequest.getSince() != null) {
			return scanRequest.getSince();
		} else {
			LocalDateTime commitsSince = LocalDateTime.now().minusMonths(firstScanFromMonths);
			log.debug("Using firstScanFrom ({} months) for commits: {}", firstScanFromMonths, commitsSince);
			return commitsSince;
		}
	}

	private GitUrlInfo parseGitUrl(ScanRequest scanRequest, CommitDataFetchStrategy.RepositoryCredentials credentials)
			throws DataProcessingException {
		GitUrlInfo urlInfo = gitUrlParser.parseGitUrl(scanRequest.getRepositoryUrl(), scanRequest.getToolType(),
				credentials.getUsername(), scanRequest.getRepositoryName());

		if (urlInfo == null) {
			throw new DataProcessingException("Invalid repository URL: " + scanRequest.getRepositoryName());
		}
		return urlInfo;
	}
}