package com.publicissapient.knowhow.processor.scm.service.core.fetcher;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.knowhow.processor.scm.service.platform.RepositoryServiceLocator;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class RepositoryFetcher {
    private final RepositoryServiceLocator repositoryServiceLocator;
    private final PersistenceService persistenceService;
    private final GitUrlParser gitUrlParser;
    @Value("${git.scanner.first-scan-from:6}")
    private int firstScanFromMonths;

    public RepositoryFetcher(RepositoryServiceLocator repositoryServiceLocator, PersistenceService persistenceService, GitUrlParser gitUrlParser) {
        this.repositoryServiceLocator = repositoryServiceLocator;
        this.persistenceService = persistenceService;
        this.gitUrlParser = gitUrlParser;
    }

    public List<ScmRepos> fetchRepositories(ScanRequest scanRequest) throws PlatformApiException {

        GitPlatformRepositoryService gitPlatformRepositoryService = repositoryServiceLocator.getRepositoryService(scanRequest.getToolType());
        LocalDateTime reposSince = calculateRepositoriesSince(scanRequest);
        List<ScmRepos> repositoryList = gitPlatformRepositoryService.fetchRepositories(scanRequest.getConnectionId(), scanRequest.getUsername(), scanRequest.getToolType(), scanRequest.getToken(), reposSince);
        return repositoryList;

    }

    private LocalDateTime calculateRepositoriesSince(ScanRequest scanRequest) {
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

}
