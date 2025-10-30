package com.publicissapient.knowhow.processor.scm.service.platform;

import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;

import java.time.LocalDateTime;
import java.util.List;

public interface GitPlatformCommitsService {

    List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlParser,
			 String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException;

}
