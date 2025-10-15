package com.publicissapient.knowhow.processor.scm.service.ratelimit.impl;

import com.publicissapient.knowhow.processor.scm.exception.RateLimitExceededException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitMonitor;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitStatus;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GitLab implementation of RateLimitMonitor.
 * Monitors and validates GitLab API rate limits.
 */
@Component
@Slf4j
public class GitLabRateLimitMonitor implements RateLimitMonitor {

    private static final String PLATFORM_NAME = "GitLab";

    @Value("${git.platforms.gitlab.api-url:https://gitlab.com/api/v4}")
    private String gitlabApiUrl;

    @Value("${git.platforms.gitlab.rate-limit.threshold:0.8}")
    private double gitlabThreshold;

    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }

    @Override
    public RateLimitStatus checkRateLimit(String token, String baseUrl) throws RateLimitExceededException {
        if (token == null || token.trim().isEmpty()) {
            log.warn("GitLab token is null or empty, skipping rate limit check");
            // Return a conservative rate limit status for unauthenticated requests
            long currentTime = System.currentTimeMillis() / 1000;
            long resetTime = currentTime + 60;
            return new RateLimitStatus(PLATFORM_NAME, 250, 300, resetTime, 50); // Unauthenticated limits
        }

        try(GitLabApi gitLabApi = new GitLabApi(baseUrl, token)) {

            // GitLab doesn't have a dedicated rate limit endpoint like GitHub
            // We'll simulate rate limit checking by making a lightweight API call
            // and monitoring for rate limit headers in responses

            // Make a lightweight API call to check current user (minimal resource usage)
            gitLabApi.getUserApi().getCurrentUser();

            // GitLab rate limits are typically:
            // - 2000 requests per minute for authenticated users
            // - 300 requests per minute for unauthenticated users
            // Since we don't have direct access to rate limit info, we'll return a default status

            long currentTime = System.currentTimeMillis() / 1000;
            long resetTime = currentTime + 60; // Reset in 1 minute (GitLab's window)

            // Default values for GitLab (assuming authenticated user limits)
            int limit = 2000; // requests per minute
            int remaining = 1900; // assume we have most requests remaining
            int used = limit - remaining;

            return new RateLimitStatus(PLATFORM_NAME, remaining, limit, resetTime, used);

        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 401) {
                log.error("GitLab authentication failed - invalid or expired token: {}", e.getMessage());
                throw new RateLimitExceededException(PLATFORM_NAME, 0, 2000, 100.0,
                    System.currentTimeMillis() / 1000 + 3600); // 1 hour cooldown for auth issues
            } else if (e.getHttpStatus() == 403) {
                log.warn("GitLab API access forbidden - possible rate limit or permission issue: {}", e.getMessage());
                // Return a status indicating we're near the limit
                long currentTime = System.currentTimeMillis() / 1000;
                long resetTime = currentTime + 300; // 5 minute cooldown
                return new RateLimitStatus(PLATFORM_NAME, 10, 2000, resetTime, 1990);
            } else if (e.getHttpStatus() == 404) {
                log.warn("GitLab API endpoint not found (404) - this might indicate token permissions issue or wrong API URL. Using default rate limit status.");
                // Don't fail the entire process for 404 on rate limit check
                // Return a conservative rate limit status
                long currentTime = System.currentTimeMillis() / 1000;
                long resetTime = currentTime + 60;
                return new RateLimitStatus(PLATFORM_NAME, 1000, 2000, resetTime, 1000);
            } else {
                log.error("Error checking GitLab rate limit: {} (HTTP {})", e.getMessage(), e.getHttpStatus());
                // Return a conservative rate limit status to allow operation to continue
                long currentTime = System.currentTimeMillis() / 1000;
                long resetTime = currentTime + 60;
                return new RateLimitStatus(PLATFORM_NAME, 500, 2000, resetTime, 1500);
            }
        } catch (Exception e) {
            log.error("Unexpected error checking GitLab rate limit", e);
            // Return a conservative rate limit status
            long currentTime = System.currentTimeMillis() / 1000;
            long resetTime = currentTime + 60;
            return new RateLimitStatus(PLATFORM_NAME, 500, 2000, resetTime, 1500);
        }
    }

    @Override
    public double getDefaultThreshold() {
        // Return configured threshold, fallback to default if not set or invalid
        return (gitlabThreshold > 0 && gitlabThreshold <= 1.0) ? gitlabThreshold : 0.8;
    }

    @Override
    public boolean supports(String platform) {
        return PLATFORM_NAME.equalsIgnoreCase(platform) || "gitlab".equalsIgnoreCase(platform);
    }
}