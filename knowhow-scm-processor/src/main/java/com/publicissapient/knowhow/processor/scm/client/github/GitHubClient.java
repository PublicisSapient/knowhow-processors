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

package com.publicissapient.knowhow.processor.scm.client.github;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;

/**
 * GitHub API client for interacting with GitHub repositories. Handles
 * authentication, rate limiting, and data fetching operations.
 */
@Component
@Slf4j
public class GitHubClient {

	private static final String PLATFORM_NAME = "GitHub";
	// CHANGE: Added constant for magic number
	private static final int RATE_LIMIT_CHECK_INTERVAL = 100;

	@Value("${git.platforms.github.api-url:https://api.github.com}")
	private String githubApiUrl;

	private final RateLimitService rateLimitService;

	GitHubClient(RateLimitService rateLimitService) {
		this.rateLimitService = rateLimitService;
	}

	/**
	 * Creates and returns an authenticated GitHub client instance.
	 *
	 * @param token
	 *            GitHub personal access token
	 * @return GitHub client instance
	 * @throws IOException
	 *             if authentication fails
	 */
	public GitHub getGitHubClient(String token) throws IOException {
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("GitHub token cannot be null or empty");
		}

		try {
			GitHub github = new GitHubBuilder().withEndpoint(githubApiUrl).withOAuthToken(token).build();

			// Test the connection
			github.checkApiUrlValidity();
			log.debug("Successfully authenticated with GitHub API");
			return github;

		} catch (IOException e) {
			log.error("Failed to authenticate with GitHub API: {}", e.getMessage());
			throw new IOException("GitHub authentication failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Gets a GitHub repository instance.
	 *
	 * @param owner
	 *            Repository owner
	 * @param repository
	 *            Repository name
	 * @param token
	 *            GitHub access token
	 * @return GHRepository instance
	 * @throws IOException
	 *             if repository access fails
	 */
	public GHRepository getRepository(String owner, String repository, String token) throws IOException {
		// CHANGE: Added parameter validation
		validateRepositoryParameters(owner, repository);

		GitHub github = getGitHubClient(token);
		String repositoryName = owner + "/" + repository;

		try {
			GHRepository repo = github.getRepository(repositoryName);
			log.debug("Successfully accessed GitHub repository: {}", repositoryName);
			return repo;
		} catch (IOException e) {
			log.error("Failed to access GitHub repository {}: {}", repositoryName, e.getMessage());
			throw new IOException("Failed to access repository: " + repositoryName, e);
		}
	}

	/**
	 * Fetches commits from a GitHub repository with pagination and date filtering.
	 *
	 * @param owner
	 *            Repository owner
	 * @param repository
	 *            Repository name
	 * @param branchName
	 *            Branch name (optional)
	 * @param token
	 *            GitHub access token
	 * @param since
	 *            Start date for filtering
	 * @param until
	 *            End date for filtering
	 * @return List of GitHub commits
	 * @throws IOException
	 *             if API call fails
	 */
	public List<GHCommit> fetchCommits(String owner, String repository, String branchName, String token,
			LocalDateTime since, LocalDateTime until) throws IOException {
		// CHANGE: Added parameter validation
		validateRepositoryParameters(owner, repository);

		String repositoryName = owner + "/" + repository;
		log.debug("Fetching commits from GitHub repository: {} since: {} until: {}", repositoryName, since, until);

		// CHANGE: Removed try-catch that was just logging and rethrowing
		// Check rate limit before making API calls
		rateLimitService.checkRateLimit(PLATFORM_NAME, token, repositoryName, null);

		GHRepository repo = getRepository(owner, repository, token);

		List<GHCommit> allCommits = new ArrayList<>();
		int totalFetched = 0;

		PagedIterable<GHCommit> commits = getCommitsIterable(repo, branchName);

		for (GHCommit commit : commits) {
			// CHANGE: Extracted rate limit check logic
			checkRateLimitIfNeeded(totalFetched, token, repositoryName);

			// CHANGE: Extracted date filtering logic
			DateFilterResult filterResult = filterByCommitDate(commit, since, until);

			if (filterResult.shouldInclude()) {
				allCommits.add(commit);
				totalFetched++;
			} else if (filterResult.shouldStop()) {
				log.debug("Reached commits older than since date, stopping fetch");
				break;
			}
		}

		log.info("Successfully fetched {} commits from GitHub repository: {}", allCommits.size(), repositoryName);
		return allCommits;
	}

	/**
	 * Fetches pull requests from a GitHub repository with date filtering based on
	 * updated date
	 *
	 * @param owner
	 *            Repository owner
	 * @param repository
	 *            Repository name
	 * @param token
	 *            GitHub access token
	 * @param since
	 *            Start date for filtering (based on updated date)
	 * @param until
	 *            End date for filtering (based on updated date)
	 * @return List of GitHub pull requests
	 * @throws IOException
	 *             if API call fails
	 */
	public List<GHPullRequest> fetchPullRequests(String owner, String repository, String token, LocalDateTime since,
			LocalDateTime until) throws IOException {
		// CHANGE: Delegating to fetchPullRequestsByState to eliminate duplication
		return fetchPullRequestsByState(owner, repository, "all", token, since, until);
	}

	/**
	 * Fetches pull requests by state with date filtering based on updated date
	 *
	 * @param owner
	 *            Repository owner
	 * @param repository
	 *            Repository name
	 * @param state
	 *            Pull request state (open, closed, all)
	 * @param token
	 *            GitHub access token
	 * @param since
	 *            Start date for filtering (based on updated date)
	 * @param until
	 *            End date for filtering (based on updated date)
	 * @return List of GitHub pull requests
	 * @throws IOException
	 *             if API call fails
	 */
	public List<GHPullRequest> fetchPullRequestsByState(String owner, String repository, String state, String token,
			LocalDateTime since, LocalDateTime until) throws IOException {
		// CHANGE: Added parameter validation
		validateRepositoryParameters(owner, repository);

		log.info("Fetching {} pull requests for {}/{} from {} to {} (based on updated date)", state, owner, repository,
				since, until);

		GHRepository repo = getRepository(owner, repository, token);

		List<GHPullRequest> allPullRequests = new ArrayList<>();
		int totalFetched = 0;

		// CHANGE: Removed try-catch that was just logging and rethrowing
		GHIssueState ghState = parseGitHubState(state);

		PagedIterable<GHPullRequest> pullRequests = repo.queryPullRequests().state(ghState)
				.sort(GHPullRequestQueryBuilder.Sort.UPDATED).direction(GHDirection.DESC).list();

		for (GHPullRequest pr : pullRequests) {
			// CHANGE: Extracted rate limit check logic
			checkRateLimitIfNeeded(totalFetched, token, owner + "/" + repository);

			// CHANGE: Extracted date filtering logic for pull requests
			DateFilterResult filterResult = filterByPullRequestDate(pr, since, until);

			if (filterResult.shouldInclude()) {
				allPullRequests.add(pr);
				totalFetched++;
			} else if (filterResult.shouldStop()) {
				log.debug("Reached pull requests older than since date, stopping fetch");
				break;
			}
		}

		log.info("Successfully fetched {} {} pull requests for {}/{} (based on updated date)", allPullRequests.size(),
				state, owner, repository);
		return allPullRequests;
	}

	/**
	 * Fetches the latest pull requests from a GitHub repository up to a specified
	 * limit.
	 *
	 * @param owner
	 *            Repository owner
	 * @param repository
	 *            Repository name
	 * @param token
	 *            GitHub access token
	 * @param limit
	 *            Maximum number of pull requests to fetch
	 * @return List of GitHub pull requests
	 * @throws IOException
	 *             if API call fails
	 */
	public List<GHPullRequest> fetchLatestPullRequests(String owner, String repository, String token, int limit)
			throws IOException {
		// CHANGE: Added parameter validation
		validateRepositoryParameters(owner, repository);
		if (limit <= 0) {
			throw new IllegalArgumentException("Limit must be greater than 0");
		}

		String repositoryName = owner + "/" + repository;
		log.debug("Fetching latest {} pull requests from GitHub repository: {}", limit, repositoryName);

		// CHANGE: Removed try-catch that was just logging and rethrowing
		// Check rate limit before making API calls
		rateLimitService.checkRateLimit(PLATFORM_NAME, token, repositoryName, null);

		GHRepository repo = getRepository(owner, repository, token);

		List<GHPullRequest> pullRequests = new ArrayList<>();
		int fetched = 0;

		PagedIterable<GHPullRequest> prIterable = repo.queryPullRequests().state(GHIssueState.ALL)
				.sort(GHPullRequestQueryBuilder.Sort.UPDATED).direction(GHDirection.DESC).list();

		for (GHPullRequest pr : prIterable) {
			if (fetched >= limit) {
				break;
			}
			pullRequests.add(pr);
			fetched++;
		}

		log.info("Successfully fetched {} latest pull requests from GitHub repository: {}", pullRequests.size(),
				repositoryName);
		return pullRequests;
	}

	/**
	 * Tests the connection to GitHub API.
	 *
	 * @param token
	 *            GitHub access token
	 * @return true if connection is successful
	 * @throws IOException
	 *             if connection fails
	 */
	public boolean testConnection(String token) throws IOException {
		try {
			GitHub github = getGitHubClient(token);
			github.checkApiUrlValidity();
			log.info("GitHub API connection test successful");
			return true;
		} catch (IOException e) {
			log.error("GitHub API connection test failed: {}", e.getMessage());
			throw e;
		}
	}

	/**
	 * Gets the GitHub API URL.
	 *
	 * @return GitHub API URL
	 */
	public String getApiUrl() {
		return githubApiUrl;
	}

	/**
	 * Helper method to parse GitHub state string to GitHub API enum
	 */
	private GHIssueState parseGitHubState(String state) {
		if (state == null)
			return GHIssueState.ALL;

		return switch (state.toLowerCase()) {
		case "open", "opened" -> GHIssueState.OPEN;
		case "closed" -> GHIssueState.CLOSED;
		default -> GHIssueState.ALL;
		};
	}

	// CHANGE: Added new helper methods to reduce complexity and duplication

	/**
	 * Validates repository parameters
	 */
	private void validateRepositoryParameters(String owner, String repository) {
		Objects.requireNonNull(owner, "Repository owner cannot be null");
		Objects.requireNonNull(repository, "Repository name cannot be null");

		if (owner.trim().isEmpty()) {
			throw new IllegalArgumentException("Repository owner cannot be empty");
		}
		if (repository.trim().isEmpty()) {
			throw new IllegalArgumentException("Repository name cannot be empty");
		}
	}

	/**
	 * Gets commits iterable based on branch name
	 */
	private PagedIterable<GHCommit> getCommitsIterable(GHRepository repo, String branchName) {
		if (branchName != null && !branchName.isEmpty()) {
			return repo.queryCommits().from(branchName).list();
		} else {
			return repo.queryCommits().list();
		}
	}

	/**
	 * Checks rate limit if needed based on the interval
	 */
	private void checkRateLimitIfNeeded(int totalFetched, String token, String repositoryName) {
		if (totalFetched % RATE_LIMIT_CHECK_INTERVAL == 0) {
			rateLimitService.checkRateLimit(PLATFORM_NAME, token, repositoryName, null);
		}
	}

	/**
	 * Filters commits by date range
	 */
	private DateFilterResult filterByCommitDate(GHCommit commit, LocalDateTime since, LocalDateTime until)
			throws IOException {
		if (commit.getCommitDate() == null) {
			return DateFilterResult.skip();
		}

		LocalDateTime commitDate = convertToLocalDateTime(commit.getCommitDate());
		return evaluateDateFilter(commitDate, since, until);
	}

	/**
	 * Filters pull requests by date range
	 */
	private DateFilterResult filterByPullRequestDate(GHPullRequest pr, LocalDateTime since, LocalDateTime until)
			throws IOException {
		if (pr.getUpdatedAt() == null) {
			return DateFilterResult.skip();
		}

		LocalDateTime updatedAt = convertToLocalDateTime(pr.getUpdatedAt());
		return evaluateDateFilter(updatedAt, since, until);
	}

	/**
	 * Converts Date to LocalDateTime
	 */
	private LocalDateTime convertToLocalDateTime(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
	}

	/**
	 * Evaluates if a date falls within the specified range
	 */
	private DateFilterResult evaluateDateFilter(LocalDateTime date, LocalDateTime since, LocalDateTime until) {
		boolean afterSince = since == null || !date.isBefore(since);
		boolean beforeUntil = until == null || !date.isAfter(until);

		if (afterSince && beforeUntil) {
			return DateFilterResult.include();
		} else if (since != null && date.isBefore(since)) {
			return DateFilterResult.stop();
		} else {
			return DateFilterResult.skip();
		}
	}

	/**
	 * Inner class to represent date filter results
	 */
	private static class DateFilterResult {
		private final boolean include;
		private final boolean stop;

		private DateFilterResult(boolean include, boolean stop) {
			this.include = include;
			this.stop = stop;
		}

		public static DateFilterResult include() {
			return new DateFilterResult(true, false);
		}

		public static DateFilterResult skip() {
			return new DateFilterResult(false, false);
		}

		public static DateFilterResult stop() {
			return new DateFilterResult(false, true);
		}

		public boolean shouldInclude() {
			return include;
		}

		public boolean shouldStop() {
			return stop;
		}
	}
}
