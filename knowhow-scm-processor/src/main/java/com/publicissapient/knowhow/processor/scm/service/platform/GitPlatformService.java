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

package com.publicissapient.knowhow.processor.scm.service.platform;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface for Git platform-specific services.
 * 
 * This interface defines the contract for interacting with different Git
 * platforms (GitHub, GitLab, Azure DevOps, Bitbucket) through their REST APIs.
 * 
 * Each platform implementation should handle: - Authentication and
 * authorization using provided tokens - Rate limiting and retry logic -
 * Platform-specific API differences - Data mapping to common domain models
 * 
 * Implements the Strategy pattern to allow switching between different
 * platforms.
 */
public interface GitPlatformService {

	List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlParser, String branchName,
			String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException;

	List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException;


	/**
	 * Gets the platform name.
	 * 
	 * @return the platform name (e.g., "GitHub", "GitLab")
	 */
	String getPlatformName();

}