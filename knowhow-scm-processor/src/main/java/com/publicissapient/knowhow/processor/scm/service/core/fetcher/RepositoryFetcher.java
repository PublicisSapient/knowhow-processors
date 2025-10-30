package com.publicissapient.knowhow.processor.scm.service.core.fetcher;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.knowhow.processor.scm.service.platform.RepositoryServiceLocator;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmConnectionTraceLog;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import com.publicissapient.kpidashboard.common.repository.scm.ScmConnectionTraceLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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
			persistenceService.saveScmConnectionTraceLog(scanResult.isSuccess(),
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
