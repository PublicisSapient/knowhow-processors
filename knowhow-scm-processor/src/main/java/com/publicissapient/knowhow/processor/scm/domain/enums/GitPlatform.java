package com.publicissapient.knowhow.processor.scm.domain.enums;

/**
 * Enumeration representing different Git platforms supported by the scanner.
 * 
 * This enum defines the various SCM platforms that the application can integrate with,
 * each with their specific API endpoints and authentication mechanisms.
 */
public enum GitPlatform {
    
    /**
     * GitHub platform - supports both GitHub.com and GitHub Enterprise
     */
    GITHUB("GitHub", "github.com"),
    
    /**
     * GitLab platform - supports both GitLab.com and self-hosted GitLab instances
     */
    GITLAB("GitLab", "gitlab.com"),
    
    /**
     * Azure DevOps platform - Microsoft's DevOps solution
     */
    AZURE_DEVOPS("Azure DevOps", "dev.azure.com"),
    
    /**
     * Bitbucket platform - Atlassian's Git solution
     */
    BITBUCKET("Bitbucket", "bitbucket.org");

    private final String displayName;
    private final String defaultHost;

    GitPlatform(String displayName, String defaultHost) {
        this.displayName = displayName;
        this.defaultHost = defaultHost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultHost() {
        return defaultHost;
    }

    /**
     * Determines the Git platform based on the repository URL.
     * 
     * @param repositoryUrl the repository URL to analyze
     * @return the corresponding GitPlatform, or null if not recognized
     */
    public static GitPlatform fromUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.trim().isEmpty()) {
            return null;
        }
        
        String lowerUrl = repositoryUrl.toLowerCase();
        
        if (lowerUrl.contains("github.com") || lowerUrl.contains("github")) {
            return GITHUB;
        } else if (lowerUrl.contains("gitlab.com") || lowerUrl.contains("gitlab")) {
            return GITLAB;
        } else if (lowerUrl.contains("dev.azure.com") || lowerUrl.contains("visualstudio.com")) {
            return AZURE_DEVOPS;
        } else if (lowerUrl.contains("bitbucket.org") || lowerUrl.contains("bitbucket")) {
            return BITBUCKET;
        }
        
        return null;
    }
}