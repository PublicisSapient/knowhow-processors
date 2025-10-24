package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BitbucketRepositoryServiceImpl implements GitPlatformRepositoryService {
    @Override
    public List<ScmRepos> fetchRepositories(String connectionId, String username, String toolType, String token, LocalDateTime since) throws PlatformApiException {
        return List.of();
    }
}
