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

package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformMergeRequestService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.extern.slf4j.Slf4j;
import org.azd.common.types.Author;
import org.azd.git.types.GitPullRequest;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AzureDevOpsMergeRequestServiceImpl implements GitPlatformMergeRequestService {

	private static final String PLATFORM_NAME = "AzureDevOps";

	private final AzureDevOpsClient azureDevOpsClient;
	private final AzureDevOpsCommonHelper commonHelper;

	public AzureDevOpsMergeRequestServiceImpl(AzureDevOpsClient azureDevOpsClient,
			AzureDevOpsCommonHelper commonHelper) {
		this.azureDevOpsClient = azureDevOpsClient;
		this.commonHelper = commonHelper;
	}

	@Override
	public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		try {
			log.info("Fetching merge requests for Azure DevOps repository: {}/{} (branch: {})", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");

			List<GitPullRequest> azurePRs = azureDevOpsClient.fetchPullRequests(gitUrlInfo.getOwner(),
					gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName(), token, since, branchName);

			List<ScmMergeRequests> mergeRequests = convertPullRequests(azurePRs, toolConfigId, gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName());

			log.info("Successfully converted {} Azure DevOps pull requests to domain objects", mergeRequests.size());
			return mergeRequests;

		} catch (Exception e) {
			log.error("Failed to fetch merge requests from Azure DevOps repository {}/{}: {}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch merge requests from Azure DevOps", e);
		}
	}

	private List<ScmMergeRequests> convertPullRequests(List<GitPullRequest> azurePRs, String toolConfigId, String owner,
			String repositoryName) {
		List<ScmMergeRequests> mergeRequests = new ArrayList<>();

		for (GitPullRequest azPr : azurePRs) {
			try {
				ScmMergeRequests mergeRequest = convertToMergeRequest(azPr, toolConfigId, owner, repositoryName);
				mergeRequests.add(mergeRequest);
			} catch (Exception e) {
				log.warn("Failed to convert Azure DevOps pull request: {}", e.getMessage());
			}
		}

		return mergeRequests;
	}

	private ScmMergeRequests convertToMergeRequest(GitPullRequest azPr, String toolConfigId, String owner,
			String repository) {
		Integer pullRequestId = azPr.getPullRequestId();
		String title = azPr.getTitle();
		String description = azPr.getDescription();
		String status = azPr.getStatus().name();

		String sourceRefName = azPr.getSourceRefName();
		String targetRefName = azPr.getTargetRefName();

		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder()
				.processorItemId(new ObjectId(toolConfigId)).repositoryName(owner + "/" + repository)
				.externalId(String.valueOf(pullRequestId)).title(title).summary(description).fromBranch(sourceRefName)
				.toBranch(targetRefName);

		commonHelper.setPullRequestState(builder, status);
		setMergeRequestTimestamps(builder, azPr);
		setMergeRequestAuthor(builder, azPr);
		setMergeRequestUrl(builder, azPr);

		return builder.build();
	}

	private void setMergeRequestTimestamps(ScmMergeRequests.ScmMergeRequestsBuilder builder, GitPullRequest azPr) {
		String creationDate = azPr.getCreationDate();
		String closedDate = azPr.getClosedDate();

		if (creationDate != null) {
			builder.createdDate(Instant.parse(creationDate).toEpochMilli());
		}

		Instant closedInstant = closedDate != null ? Instant.parse(closedDate) : null;
		commonHelper.setMergeRequestTimestamps(builder, closedInstant);

		if (closedDate != null) {
			builder.updatedDate(Instant.parse(closedDate).toEpochMilli());
		} else if (creationDate != null) {
			builder.updatedDate(Instant.parse(creationDate).toEpochMilli());
		}
	}

	private void setMergeRequestAuthor(ScmMergeRequests.ScmMergeRequestsBuilder builder, GitPullRequest azPr) {
		Author createdBy = azPr.getCreatedBy();
		if (createdBy != null) {
			String uniqueName = createdBy.getUniqueName();
			String displayName = createdBy.getDisplayName();
			User user = commonHelper.createUser(uniqueName, displayName);
			builder.authorId(user).authorUserId(uniqueName);
		}
	}

	private void setMergeRequestUrl(ScmMergeRequests.ScmMergeRequestsBuilder builder, GitPullRequest azPr) {
		String url = azPr.getUrl();
		if (url != null) {
			builder.mergeRequestUrl(url);
		}
	}

}
