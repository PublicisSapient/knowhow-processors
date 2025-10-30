package com.publicissapient.knowhow.processor.scm.service.platform;

import java.util.List;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;

public interface GitPlatformRepositoryService {
	List<ScmRepos> fetchRepositories(ScanRequest scanRequest) throws PlatformApiException;
}
