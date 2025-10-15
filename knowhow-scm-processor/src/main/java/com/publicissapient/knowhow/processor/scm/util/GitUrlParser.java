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

package com.publicissapient.knowhow.processor.scm.util;

import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing Git repository URLs. Supports parsing URLs from
 * various Git platforms including: - GitHub
 * (<a href="https://github.com/owner/repo.git">...</a>) - GitLab
 * (https://gitlab.com/owner/repo.git) - Azure DevOps
 * (<a href="https://dev.azure.com/org/project/_git/repo">...</a>) - Bitbucket
 * (<a href="https://bitbucket.org/owner/repo.git">...</a>)
 */
@Component
public class GitUrlParser {

	private static final Pattern GITHUB_PATTERN = Pattern
			.compile("https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

	private static final Pattern GITLAB_PATTERN = Pattern.compile("https?://([^/]+)/(.+)/([^/]+?)(?:\\.git)?/?$");

	private static final Pattern GITLAB_COM_PATTERN = Pattern
			.compile("https?://gitlab\\.com/(.+)/([^/]+?)(?:\\.git)?/?$");

	private static final Pattern AZURE_DEVOPS_PATTERN = Pattern
			.compile("https?://(?:[\\w.-]+@)?dev\\.azure\\.com/([^/]+)/([^/]+)/_git/([^/]+?)/?$");

	private static final Pattern BITBUCKET_PATTERN = Pattern
			.compile("https?://bitbucket\\.org/([^/]+)/([^/]+?)(?:\\.git)?/?$");

	private static final Pattern BITBUCKET_SERVER_PATTERN = Pattern
			.compile("https?://[^/]+/bitbucket/scm/([^/]+)/([^/]+?)(?:\\.git)?/?$");

	private static final String INVALID_URL_FORMAT_STRING = "Invalid URL format: ";

	private static final List<String> KNOWN_GITLAB_HOSTS = Arrays.asList("gitlab.com", "gitlab.example.com",
			"git.company.com", "pscode.lioncloud.net");

	private static final String GIT_EXTENSION = ".git";
	private static final String SLASH = "/";
	private static final String URL_SCHEME_SEPARATOR = "://";

	/**
	 * Parses a Git repository URL and extracts platform-specific information.
	 *
	 * @param gitUrl
	 *            the Git repository URL
	 * @param toolType
	 *            the type of Git tool
	 * @param username
	 *            the username (optional)
	 * @param repositoryName
	 *            the repository name (optional)
	 * @return GitUrlInfo containing parsed information
	 * @throws IllegalArgumentException
	 *             if the URL format is not supported
	 */
	public GitUrlInfo parseGitUrl(String gitUrl, String toolType, String username, String repositoryName) {
		validateGitUrl(gitUrl);
		String normalizedUrl = gitUrl.trim();

		GitUrlInfo result = null;

		if (ProcessorConstants.GITHUB.equalsIgnoreCase(toolType)) {
			result = parseGitHubUrl(normalizedUrl, username, repositoryName);
		} else if (ProcessorConstants.GITLAB.equalsIgnoreCase(toolType)) {
			result = parseGitLabUrl(normalizedUrl);
		} else if (ProcessorConstants.BITBUCKET.equalsIgnoreCase(toolType)) {
			result = parseBitbucketUrl(normalizedUrl, gitUrl, username, repositoryName);
		}

		if (result != null) {
			return result;
		}

		// Try Azure DevOps pattern
		result = parseAzureDevOpsUrl(normalizedUrl);
		if (result != null) {
			return result;
		}

		throw new IllegalArgumentException("Unsupported Git URL format: " + gitUrl);
	}

	private GitUrlInfo parseGitHubUrl(String normalizedUrl, String username, String repositoryName) {
		Matcher githubMatcher = GITHUB_PATTERN.matcher(normalizedUrl);
		if (githubMatcher.matches()) {
			return new GitUrlInfo(GitPlatform.GITHUB, githubMatcher.group(1), githubMatcher.group(2), null,
					normalizedUrl);
		} else if (repositoryName != null && username != null) {
			String[] parts = repositoryName.split(SLASH);
			if (parts.length >= 2) {
				return new GitUrlInfo(GitPlatform.GITHUB, parts[0], parts[1], null, normalizedUrl);
			}
		}
		return null;
	}

	private GitUrlInfo parseGitLabUrl(String normalizedUrl) {
		Matcher gitlabComMatcher = GITLAB_COM_PATTERN.matcher(normalizedUrl);
		if (gitlabComMatcher.matches()) {
			return new GitUrlInfo(GitPlatform.GITLAB, gitlabComMatcher.group(1), gitlabComMatcher.group(2), null,
					normalizedUrl);
		}

		if (isGitLabUrl(normalizedUrl)) {
			Matcher gitlabMatcher = GITLAB_PATTERN.matcher(normalizedUrl);
			if (gitlabMatcher.matches()) {
				String ownerAndGroups = gitlabMatcher.group(2);
				String repository = gitlabMatcher.group(3);
				String[] pathParts = ownerAndGroups.split(SLASH);
				String owner = pathParts[pathParts.length - 1];

				return new GitUrlInfo(GitPlatform.GITLAB, owner, repository, ownerAndGroups, normalizedUrl);
			}
		}
		return null;
	}

	private GitUrlInfo parseBitbucketUrl(String normalizedUrl, String gitUrl, String username, String repositoryName) {
		Matcher bitbucketMatcher = normalizedUrl.contains("bitbucket.org") ? BITBUCKET_PATTERN.matcher(normalizedUrl)
				: BITBUCKET_SERVER_PATTERN.matcher(normalizedUrl);

		if (bitbucketMatcher.matches()) {
			return new GitUrlInfo(GitPlatform.BITBUCKET, bitbucketMatcher.group(1), bitbucketMatcher.group(2), null,
					normalizedUrl);
		} else if (repositoryName != null && username != null) {
			String[] parts = repositoryName.split(SLASH);
			if (parts.length >= 2) {
				return new GitUrlInfo(GitPlatform.BITBUCKET, parts[0], parts[1], null, gitUrl);
			}
		}
		return null;
	}

	private GitUrlInfo parseAzureDevOpsUrl(String normalizedUrl) {
		Matcher azureMatcher = AZURE_DEVOPS_PATTERN.matcher(normalizedUrl);
		if (azureMatcher.matches()) {
			return new GitUrlInfo(GitPlatform.AZURE_DEVOPS, null, azureMatcher.group(3), azureMatcher.group(1),
					azureMatcher.group(2), normalizedUrl);
		}
		return null;
	}

	private void validateGitUrl(String gitUrl) {
		if (gitUrl == null || gitUrl.trim().isEmpty()) {
			throw new IllegalArgumentException("Git URL cannot be null or empty");
		}
	}

	/**
	 * Checks if a URL is a GitLab URL by looking for GitLab-specific indicators.
	 */
	private boolean isGitLabUrl(String url) {
		try {
			URI uri = new URI(url);
			String host = uri.getHost();
			String path = uri.getPath();

			if (host == null) {
				return false;
			}

			if (isKnownGitLabHost(host) || host.toLowerCase().contains("gitlab")) {
				return true;
			}

			return isValidGitLabPath(host, path);
		} catch (URISyntaxException e) {
			return false;
		}
	}

	private boolean isKnownGitLabHost(String host) {
		return KNOWN_GITLAB_HOSTS.stream()
				.anyMatch(knownHost -> host.equals(knownHost) || host.endsWith("." + knownHost));
	}

	private boolean isValidGitLabPath(String host, String path) {
		if (path == null) {
			return false;
		}

		// Exclude known non-GitLab platforms
		if (isNonGitLabPlatform(host, path)) {
			return false;
		}

		// Check for GitLab API paths
		if (path.contains("/api/v4/") || path.contains("/explore")) {
			return true;
		}

		// Validate path structure
		List<String> validParts = extractValidPathParts(path);
		return validParts.size() >= 2 && !containsGenericWebTerms(validParts);
	}

	private boolean isNonGitLabPlatform(String host, String path) {
		return host.contains("dev.azure.com") || path.contains("/_git/") || host.contains("github.com")
				|| host.contains("bitbucket.org");
	}

	private List<String> extractValidPathParts(String path) {
		String[] pathParts = path.split(SLASH);
		List<String> validParts = new java.util.ArrayList<>();

		for (String part : pathParts) {
			if (!part.isEmpty()) {
				String cleanPart = part.endsWith(GIT_EXTENSION) ? part.substring(0, part.length() - 4) : part;
				validParts.add(cleanPart);
			}
		}
		return validParts;
	}

	private boolean containsGenericWebTerms(List<String> parts) {
		List<String> genericTerms = Arrays.asList("not", "a", "the", "and");
		return parts.stream().anyMatch(genericTerms::contains)
				|| (parts.contains("repo") && !parts.get(parts.size() - 1).endsWith(GIT_EXTENSION));
	}

	/**
	 * Extracts the host from a GitLab URL for API base URL construction.
	 */
	public String extractGitLabHost(String gitUrl) {
		validateGitUrl(gitUrl);

		try {
			URI uri = new URI(gitUrl.trim());
			String host = uri.getHost();

			if (host == null) {
				throw new IllegalArgumentException(INVALID_URL_FORMAT_STRING + gitUrl);
			}

			return host;
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(INVALID_URL_FORMAT_STRING + gitUrl, e);
		}
	}

	/**
	 * Constructs the GitLab API base URL from a repository URL.
	 */
	public String getGitLabApiBaseUrl(String gitUrl) {
		try {
			URI uri = new URI(gitUrl.trim());
			String scheme = uri.getScheme();
			String host = extractGitLabHost(gitUrl);

			return scheme + URL_SCHEME_SEPARATOR + host;
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(INVALID_URL_FORMAT_STRING + gitUrl, e);
		}
	}

	/**
	 * Validates if a Git URL is supported.
	 */
	public boolean isValidGitUrl(String gitUrl, String toolType) {
		try {
			parseGitUrl(gitUrl, toolType, null, null);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Data class containing parsed Git URL information.
	 */
	@Getter
	@Setter
	@NoArgsConstructor(force = true)
	public static class GitUrlInfo {
		private final GitPlatform platform;
		private final String owner;
		private final String repositoryName;
		private final String organization;
		private final String project;
		private final String originalUrl;

		public GitUrlInfo(GitPlatform platform, String owner, String repositoryName, String organization,
				String originalUrl) {
			this(platform, owner, repositoryName, organization, null, originalUrl);
		}

		public GitUrlInfo(GitPlatform platform, String owner, String repositoryName, String organization,
				String project, String originalUrl) {
			this.platform = platform;
			this.owner = owner;
			this.repositoryName = repositoryName;
			this.organization = organization;
			this.project = project;
			this.originalUrl = originalUrl;
		}

		@Override
		public String toString() {
			return String.format(
					"GitUrlInfo{platform=%s, owner='%s', repository='%s', organization='%s', project='%s', url='%s'}",
					platform, owner, repositoryName, organization, project, originalUrl);
		}
	}

	/**
	 * Enumeration of supported Git platforms.
	 */
	@Getter
	public enum GitPlatform {
		GITHUB("GitHub"), GITLAB("GitLab"), AZURE_DEVOPS("Azure DevOps"), BITBUCKET("Bitbucket"), UNKNOWN("Unknown");

		private final String displayName;

		GitPlatform(String displayName) {
			this.displayName = displayName;
		}
	}
}
