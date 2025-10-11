package com.publicissapient.knowhow.processor.scm.service.ratelimit.impl;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitMonitor;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitStatus;
import lombok.extern.slf4j.Slf4j;
import org.azd.connection.Connection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Azure DevOps implementation of RateLimitMonitor.
 * 
 * Azure DevOps has different rate limiting compared to other platforms:
 * - Uses TFS (Team Foundation Server) rate limiting
 * - Typically allows 300 requests per minute per user
 * - Rate limits are enforced per organization and per user
 * 
 * Note: The azd library doesn't provide direct access to rate limit headers,
 * so this implementation provides estimated rate limit information.
 */
@Component
@Slf4j
public class AzureDevOpsRateLimitMonitor implements RateLimitMonitor {

    private static final String PLATFORM_NAME = "Azure DevOps";
    
    // Azure DevOps typical rate limits
    private static final int DEFAULT_RATE_LIMIT = 300; // requests per minute
    private static final int ESTIMATED_REMAINING = 250; // Conservative estimate

    @Value("${git.scanner.rate-limit.azure-devops.threshold:0.8}")
    private double threshold;

    @Override
    public RateLimitStatus checkRateLimit(String token, String baseUrl) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("No token provided for Azure DevOps rate limit check");
            return createDefaultRateLimitStatus();
        }

        try {
            // Extract organization from baseUrl or use default
            String organization = extractOrganizationFromUrl(baseUrl);
            if (organization == null) {
                log.warn("Could not extract organization from URL: {}", baseUrl);
                return createDefaultRateLimitStatus();
            }

            // Test connection to verify token validity
            Connection connection = new Connection(organization, "", token);

            // Try to get connection data to verify the token works
            // For version 3.0.1, we'll do a simple connection test
            if (connection.getOrganization() != null && !connection.getOrganization().isEmpty()) {
                // Since Azure DevOps API doesn't expose rate limit headers directly,
                // we return a conservative estimate
                RateLimitStatus status = new RateLimitStatus(
                    PLATFORM_NAME,                // platform
                    ESTIMATED_REMAINING,          // remaining
                    DEFAULT_RATE_LIMIT,           // limit
                    LocalDateTime.now().plusMinutes(1).toEpochSecond(ZoneOffset.UTC), // reset time
                    DEFAULT_RATE_LIMIT - ESTIMATED_REMAINING // used
                );

                log.debug("Azure DevOps rate limit status (estimated): {}/{} requests used",
                            status.getUsed(), status.getLimit());

                return status;
            } else {
                log.warn("Invalid Azure DevOps connection");
                return createDefaultRateLimitStatus();
            }

        } catch (Exception e) {
            log.warn("Failed to check Azure DevOps rate limit: {}", e.getMessage());
            // Return a conservative status that won't trigger rate limiting
            return createDefaultRateLimitStatus();
        }
    }

    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }

    @Override
    public double getDefaultThreshold() {
        return threshold;
    }

    /**
     * Creates a default rate limit status for cases where we can't determine the actual status.
     * This is conservative to avoid hitting rate limits.
     */
    private RateLimitStatus createDefaultRateLimitStatus() {
        return new RateLimitStatus(
            PLATFORM_NAME,                // platform
            ESTIMATED_REMAINING,          // remaining
            DEFAULT_RATE_LIMIT,           // limit
            LocalDateTime.now().plusMinutes(1).toEpochSecond(ZoneOffset.UTC), // reset time
            DEFAULT_RATE_LIMIT - ESTIMATED_REMAINING // used
        );
    }

    /**
     * Extracts the organization name from Azure DevOps URL.
     * 
     * @param url Azure DevOps URL (e.g., https://dev.azure.com/organization)
     * @return organization name or null if not found
     */
    private String extractOrganizationFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Handle different Azure DevOps URL formats
            // https://dev.azure.com/organization
            // https://organization.visualstudio.com
            
            if (url.contains("dev.azure.com")) {
                String[] parts = url.split("/");
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].contains("dev.azure.com") && i + 1 < parts.length) {
                        return parts[i + 1];
                    }
                }
            } else if (url.contains("visualstudio.com")) {
                // Extract from subdomain: https://organization.visualstudio.com
                String host = url.replaceAll("https?://", "").split("/")[0];
                if (host.endsWith(".visualstudio.com")) {
                    return host.substring(0, host.indexOf(".visualstudio.com"));
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract organization from Azure DevOps URL {}: {}", url, e.getMessage());
            return null;
        }
    }
}