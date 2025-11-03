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
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.knowhow.processor.scm.service.platform.RepositoryServiceLocator;
import com.publicissapient.kpidashboard.common.model.scm.ScmConnectionTraceLog;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@Slf4j
public class RepositoryFetcher {
	private final RepositoryServiceLocator repositoryServiceLocator;
	private final PersistenceService persistenceService;

	@Value("${git.scanner.first-scan-from:6}")
	private int firstScanFromMonths;

	public RepositoryFetcher(RepositoryServiceLocator repositoryServiceLocator, PersistenceService persistenceService) {
		this.repositoryServiceLocator = repositoryServiceLocator;
		this.persistenceService = persistenceService;
	}

	public ScanResult fetchRepositories(ScanRequest scanRequest) throws PlatformApiException {

		ScmConnectionTraceLog scmConnectionTraceLog = persistenceService
				.getScmConnectionTraceLog(scanRequest.getConnectionId().toString());
        persistenceService.saveScmConnectionTraceLog(false, false,
                scanRequest.getConnectionId().toString(), scmConnectionTraceLog);
		GitPlatformRepositoryService gitPlatformRepositoryService = repositoryServiceLocator
				.getRepositoryService(scanRequest.getToolType());
		LocalDateTime reposSince = calculateRepositoriesSince(scmConnectionTraceLog);
		scanRequest.setSince(reposSince);
		List<ScmRepos> scmReposList = gitPlatformRepositoryService.fetchRepositories(scanRequest);
		ScanResult scanResult = ScanResult.builder().success(!scmReposList.isEmpty())
				.repositoriesFound(scmReposList.size()).build();
		// Persists repository data
		if (!scmReposList.isEmpty()) {
			persistenceService.saveRepositoryData(scmReposList);
			persistenceService.saveScmConnectionTraceLog(scanResult.isSuccess(), false,
					scanRequest.getConnectionId().toString(), scmConnectionTraceLog);
			log.info("Persisted {} repository data for repository: {} ({})", scmReposList.size(),
					scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());

		}
		return scanResult;

	}

	private LocalDateTime calculateRepositoriesSince(ScmConnectionTraceLog scmConnectionTraceLog) {
		if (scmConnectionTraceLog != null && scmConnectionTraceLog.isFetchSuccessful()) {
			return LocalDateTime.ofInstant(Instant.ofEpochMilli(scmConnectionTraceLog.getLastSyncTimeTimeStamp()),
					ZoneOffset.UTC);
		} else {
			LocalDateTime commitsSince = LocalDateTime.now().minusMonths(firstScanFromMonths);
			log.debug("Using firstScanFrom ({} months) for commits: {}", firstScanFromMonths, commitsSince);
			return commitsSince;
		}
	}

}
