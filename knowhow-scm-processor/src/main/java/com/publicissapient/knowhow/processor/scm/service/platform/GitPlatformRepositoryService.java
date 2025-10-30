package com.publicissapient.knowhow.processor.scm.service.platform;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

public interface GitPlatformRepositoryService {
	List<ScmRepos> fetchRepositories(ScanRequest scanRequest) throws PlatformApiException;
}
