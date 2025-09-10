package com.publicissapient.knowhow.processor.scm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Git platform integrations.
 * 
 * This class holds configuration for different Git platforms including
 * API endpoints, authentication tokens, and platform-specific settings.
 */
@Configuration
@ConfigurationProperties(prefix = "git.platforms")
public class GitPlatformConfig {

    private GitHub github = new GitHub();
    private GitLab gitlab = new GitLab();
    private Azure azure = new Azure();
    private Bitbucket bitbucket = new Bitbucket();

    // Getters and setters
    public GitHub getGithub() { return github; }
    public void setGithub(GitHub github) { this.github = github; }

    public GitLab getGitlab() { return gitlab; }
    public void setGitlab(GitLab gitlab) { this.gitlab = gitlab; }

    public Azure getAzure() { return azure; }
    public void setAzure(Azure azure) { this.azure = azure; }

    public Bitbucket getBitbucket() { return bitbucket; }
    public void setBitbucket(Bitbucket bitbucket) { this.bitbucket = bitbucket; }

    /**
     * GitHub platform configuration.
     */
    public static class GitHub {
        private boolean enabled = true;
        private String apiUrl = "https://api.github.com";
        private String token;
        private int rateLimitPerHour = 5000;
        private int timeoutSeconds = 30;
        private int retryAttempts = 3;
        private Map<String, String> customHeaders = new HashMap<>();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public int getRateLimitPerHour() { return rateLimitPerHour; }
        public void setRateLimitPerHour(int rateLimitPerHour) { this.rateLimitPerHour = rateLimitPerHour; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

        public Map<String, String> getCustomHeaders() { return customHeaders; }
        public void setCustomHeaders(Map<String, String> customHeaders) { this.customHeaders = customHeaders; }
    }

    /**
     * GitLab platform configuration.
     */
    public static class GitLab {
        private boolean enabled = true;
        private String apiUrl = "https://gitlab.com/api/v4";
        private String token;
        private int rateLimitPerMinute = 300;
        private int timeoutSeconds = 30;
        private int retryAttempts = 3;
        private Map<String, String> customHeaders = new HashMap<>();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public int getRateLimitPerMinute() { return rateLimitPerMinute; }
        public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

        public Map<String, String> getCustomHeaders() { return customHeaders; }
        public void setCustomHeaders(Map<String, String> customHeaders) { this.customHeaders = customHeaders; }
    }

    /**
     * Azure DevOps platform configuration.
     */
    public static class Azure {
        private boolean enabled = true;
        private String apiUrl = "https://dev.azure.com";
        private String organization;
        private String personalAccessToken;
        private int rateLimitPerHour = 1000;
        private int timeoutSeconds = 30;
        private int retryAttempts = 3;
        private Map<String, String> customHeaders = new HashMap<>();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

        public String getOrganization() { return organization; }
        public void setOrganization(String organization) { this.organization = organization; }

        public String getPersonalAccessToken() { return personalAccessToken; }
        public void setPersonalAccessToken(String personalAccessToken) { this.personalAccessToken = personalAccessToken; }

        public int getRateLimitPerHour() { return rateLimitPerHour; }
        public void setRateLimitPerHour(int rateLimitPerHour) { this.rateLimitPerHour = rateLimitPerHour; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

        public Map<String, String> getCustomHeaders() { return customHeaders; }
        public void setCustomHeaders(Map<String, String> customHeaders) { this.customHeaders = customHeaders; }
    }

    /**
     * Bitbucket platform configuration.
     */
    public static class Bitbucket {
        private boolean enabled = true;
        private String apiUrl = "https://api.bitbucket.org/2.0";
        private String username;
        private String appPassword;
        private int rateLimitPerHour = 1000;
        private int timeoutSeconds = 30;
        private int retryAttempts = 3;
        private Map<String, String> customHeaders = new HashMap<>();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getAppPassword() { return appPassword; }
        public void setAppPassword(String appPassword) { this.appPassword = appPassword; }

        public int getRateLimitPerHour() { return rateLimitPerHour; }
        public void setRateLimitPerHour(int rateLimitPerHour) { this.rateLimitPerHour = rateLimitPerHour; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

        public Map<String, String> getCustomHeaders() { return customHeaders; }
        public void setCustomHeaders(Map<String, String> customHeaders) { this.customHeaders = customHeaders; }
    }
}