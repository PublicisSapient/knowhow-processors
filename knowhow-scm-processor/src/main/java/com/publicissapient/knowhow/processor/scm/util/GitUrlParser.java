package com.publicissapient.knowhow.processor.scm.util;

import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing Git repository URLs.
 * 
 * Supports parsing URLs from various Git platforms including:
 * - GitHub (https://github.com/owner/repo.git)
 * - GitLab (https://gitlab.com/owner/repo.git)
 * - Azure DevOps (https://dev.azure.com/org/project/_git/repo)
 * - Bitbucket (https://bitbucket.org/owner/repo.git)
 */
@Component
public class GitUrlParser {

    // Regex patterns for different Git URL formats
    private static final Pattern GITHUB_PATTERN = Pattern.compile(
            "https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    // Updated GitLab pattern to support any GitLab instance (not just gitlab.com)
    private static final Pattern GITLAB_PATTERN = Pattern.compile(
            "https?://([^/]+)/(.+)/([^/]+?)(?:\\.git)?/?$");

    // More specific pattern for gitlab.com to maintain backward compatibility
    private static final Pattern GITLAB_COM_PATTERN = Pattern.compile(
            "https?://gitlab\\.com/(.+)/([^/]+?)(?:\\.git)?/?$");
    
    private static final Pattern AZURE_DEVOPS_PATTERN = Pattern.compile(
            "https?://dev\\.azure\\.com/([^/]+)/([^/]+)/_git/([^/]+?)/?$");
    
    private static final Pattern BITBUCKET_PATTERN = Pattern.compile(
            "https?://bitbucket\\.org/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    // Known GitLab hosts for enhanced detection
    private static final String[] knownGitLabHosts = {
        "gitlab.com",
        "gitlab.example.com",
        "git.company.com",
        "pscode.lioncloud.net"  // Add the specific host from the user's URL
    };

    /**
     * Parses a Git repository URL and extracts platform-specific information.
     * 
     * @param gitUrl the Git repository URL
     * @return GitUrlInfo containing parsed information
     * @throws IllegalArgumentException if the URL format is not supported
     */
    public GitUrlInfo parseGitUrl(String gitUrl, String toolType, String username, String repositoryName) {
        if (gitUrl == null || gitUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Git URL cannot be null or empty");
        }

        String normalizedUrl = gitUrl.trim();

        if(toolType.equalsIgnoreCase(ProcessorConstants.GITHUB)) {
            // Try GitHub pattern

            Matcher githubMatcher = GITHUB_PATTERN.matcher(normalizedUrl);
            if (githubMatcher.matches()) {
                return new GitUrlInfo(
                        GitPlatform.GITHUB,
                        githubMatcher.group(1), // owner
                        githubMatcher.group(2), // repository
                        null, // organization (not applicable for GitHub)
                        normalizedUrl
                );
            }
        }

        if(toolType.equalsIgnoreCase(ProcessorConstants.GITLAB)) {

            // Try GitLab.com pattern first for backward compatibility
            Matcher gitlabComMatcher = GITLAB_COM_PATTERN.matcher(normalizedUrl);
            if (gitlabComMatcher.matches()) {
                return new GitUrlInfo(
                        GitPlatform.GITLAB,
                        gitlabComMatcher.group(1), // owner
                        gitlabComMatcher.group(2), // repository
                        null, // organization (not applicable for GitLab)
                        normalizedUrl
                );
            }

            // Try generic GitLab pattern for on-premise instances
            // This should come after other specific patterns to avoid false positives
            else if(isGitLabUrl(normalizedUrl)) {
                Matcher gitlabMatcher = GITLAB_PATTERN.matcher(normalizedUrl);
                if (gitlabMatcher.matches()) {
                    String host = gitlabMatcher.group(1);
                    String ownerAndGroups = gitlabMatcher.group(2);
                    String repository = gitlabMatcher.group(3);

                    // For GitLab, the owner could be a user or a group/subgroup path
                    // We'll take the last part before the repository as the immediate owner
                    String[] pathParts = ownerAndGroups.split("/");
                    String owner = pathParts[pathParts.length - 1];

                    return new GitUrlInfo(
                            GitPlatform.GITLAB,
                            owner, // immediate owner/group
                            repository, // repository
                            ownerAndGroups, // full path as organization for GitLab
                            normalizedUrl
                    );
                }
            }

        } else if (toolType.equalsIgnoreCase(ProcessorConstants.BITBUCKET)) {

            Matcher bitbucketMatcher = BITBUCKET_PATTERN.matcher(normalizedUrl);
            if (bitbucketMatcher.matches()) {
                return new GitUrlInfo(
                        GitPlatform.BITBUCKET,
                        bitbucketMatcher.group(1), // owner
                        bitbucketMatcher.group(2), // repository
                        null, // organization (not applicable for Bitbucket)
                        normalizedUrl
                );
            } else if (repositoryName != null && username != null) {
                return new GitUrlInfo(
                        GitPlatform.BITBUCKET,
                        username, // owner
                        repositoryName, // repository
                        null, // organization (not applicable for Bitbucket)
                        gitUrl
                );
            }

        }

        // Try Azure DevOps pattern
        Matcher azureMatcher = AZURE_DEVOPS_PATTERN.matcher(normalizedUrl);
        if (azureMatcher.matches()) {
            return new GitUrlInfo(
                    GitPlatform.AZURE_DEVOPS,
                    null, // owner (not directly available in Azure DevOps URL)
                    azureMatcher.group(3), // repository
                    azureMatcher.group(1), // organization
                    normalizedUrl
            );
        }

        throw new IllegalArgumentException("Unsupported Git URL format: " + gitUrl);
    }

    /**
     * Determines the Git platform from a repository URL.
     * 
     * @param gitUrl the Git repository URL
     * @return the Git platform
     */
    /**
     * Checks if a URL is a GitLab URL by looking for GitLab-specific indicators.
     * This method helps identify GitLab instances (including on-premise) that might not match
     * the standard patterns but contain GitLab-specific paths or structures.
     */
    private boolean isGitLabUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();

            // Check if it's a configured known GitLab host
            if (host != null && knownGitLabHosts != null) {
                for (String knownHost : knownGitLabHosts) {
                    if (host.equals(knownHost) || host.endsWith("." + knownHost)) {
                        return true;
                    }
                }
            }

            // Check if it's a host with "gitlab" in the name (case insensitive)
            if (host != null && host.toLowerCase().contains("gitlab")) {
                return true;
            }

            // Enhanced heuristics for GitLab detection
            if (host != null && path != null) {
                // Check for common GitLab path patterns
                // GitLab typically has paths like: /group/subgroup/project or /user/project
                String[] pathParts = path.split("/");

                // Remove empty parts and .git suffix
                java.util.List<String> validParts = new java.util.ArrayList<>();
                for (String part : pathParts) {
                    if (!part.isEmpty()) {
                        if (part.endsWith(".git")) {
                            part = part.substring(0, part.length() - 4);
                        }
                        validParts.add(part);
                    }
                }

                // GitLab URLs typically have at least 2 path segments (owner/repo or group/subgroup/repo)
                if (validParts.size() >= 2) {
                    // Additional heuristics for GitLab:
                    // 1. Check if the URL structure suggests a GitLab instance
                    // 2. GitLab supports nested groups, so paths with 3+ segments are common
                    // 3. Exclude known patterns from other platforms

                    // Exclude Azure DevOps patterns
                    if (host.contains("dev.azure.com") || path.contains("/_git/")) {
                        return false;
                    }

                    // Exclude GitHub patterns
                    if (host.contains("github.com")) {
                        return false;
                    }

                    // Exclude Bitbucket patterns
                    if (host.contains("bitbucket.org")) {
                        return false;
                    }

                    // Additional check for common GitLab API paths
                    if (path.contains("/api/v4/") || path.contains("/explore")) {
                        return true;
                    }

                    // Additional exclusions for common non-GitLab patterns
                    // Exclude URLs that look like generic web paths rather than Git repositories
                    if (validParts.contains("not") || validParts.contains("a") ||
                        validParts.contains("the") || validParts.contains("and") ||
                        (validParts.contains("repo") && !path.endsWith(".git"))) {
                        return false;
                    }

                    // If it's not a known non-GitLab platform and has the right path structure,
                    // assume it's GitLab (this covers on-premise instances)
                    return true;
                }
            }

            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Extracts the host from a GitLab URL for API base URL construction.
     *
     * @param gitUrl the GitLab repository URL
     * @return the host (e.g., "gitlab.com", "gitlab.example.com")
     * @throws IllegalArgumentException if the URL is not a valid GitLab URL
     */
    public String extractGitLabHost(String gitUrl) {
        if (gitUrl == null || gitUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Git URL cannot be null or empty");
        }

        try {
            URI uri = new URI(gitUrl.trim());
            String host = uri.getHost();

            if (host == null) {
                throw new IllegalArgumentException("Invalid URL format: " + gitUrl);
            }

            return host;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: " + gitUrl, e);
        }
    }

    /**
     * Constructs the GitLab API base URL from a repository URL.
     *
     * @param gitUrl the GitLab repository URL
     * @return the API base URL (e.g., "https://gitlab.com", "https://gitlab.example.com")
     */
    public String getGitLabApiBaseUrl(String gitUrl) {
        try {
            URI uri = new URI(gitUrl.trim());
            String scheme = uri.getScheme();
            String host = extractGitLabHost(gitUrl);

            return scheme + "://" + host;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: " + gitUrl, e);
        }
    }

    /**
     * Validates if a Git URL is supported.
     * 
     * @param gitUrl the Git repository URL
     * @return true if the URL is supported, false otherwise
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
     * Normalizes a Git URL by removing trailing slashes and .git extensions.
     * 
     * @param gitUrl the Git repository URL
     * @return normalized URL
     */
    public String normalizeGitUrl(String gitUrl) {
        if (gitUrl == null || gitUrl.trim().isEmpty()) {
            return gitUrl;
        }

        String normalized = gitUrl.trim();
        
        // Remove trailing slash
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        // Remove .git extension
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        
        return normalized;
    }

    /**
     * Data class containing parsed Git URL information.
     */
    public static class GitUrlInfo {
        private final GitPlatform platform;
        private final String owner;
        private final String repositoryName;
        private final String organization;
        private final String originalUrl;

        public GitUrlInfo(GitPlatform platform, String owner, String repositoryName, 
                         String organization, String originalUrl) {
            this.platform = platform;
            this.owner = owner;
            this.repositoryName = repositoryName;
            this.organization = organization;
            this.originalUrl = originalUrl;
        }

        public GitPlatform getPlatform() { return platform; }
        public String getOwner() { return owner; }
        public String getRepositoryName() { return repositoryName; }
        public String getOrganization() { return organization; }
        public String getOriginalUrl() { return originalUrl; }

        @Override
        public String toString() {
            return String.format("GitUrlInfo{platform=%s, owner='%s', repository='%s', organization='%s', url='%s'}",
                    platform, owner, repositoryName, organization, originalUrl);
        }
    }

    /**
     * Enumeration of supported Git platforms.
     */
    public enum GitPlatform {
        GITHUB("GitHub"),
        GITLAB("GitLab"),
        AZURE_DEVOPS("Azure DevOps"),
        BITBUCKET("Bitbucket"),
        UNKNOWN("Unknown");

        private final String displayName;

        GitPlatform(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}