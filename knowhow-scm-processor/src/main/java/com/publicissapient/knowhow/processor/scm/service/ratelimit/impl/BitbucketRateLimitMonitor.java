package com.publicissapient.knowhow.processor.scm.service.ratelimit.impl;

import com.publicissapient.knowhow.processor.scm.exception.RateLimitExceededException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitMonitor;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;


/**
 * Rate limit monitor for Bitbucket API.
 * Supports both Bitbucket Cloud and Bitbucket Server (on-premise).
 */
@Component
public class BitbucketRateLimitMonitor implements RateLimitMonitor {

    private static final Logger logger = LoggerFactory.getLogger(BitbucketRateLimitMonitor.class);
    private static final String PLATFORM_NAME = "Bitbucket";

    @Value("${git-scanner.platforms.bitbucket.api-url:https://api.bitbucket.org/2.0}")
    private String bitbucketApiUrl;

    @Value("${git-scanner.platforms.bitbucket.rate-limit.threshold:0.8}")
    private double bitbucketThreshold;

    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }

    @Override
    public RateLimitStatus checkRateLimit(String token, String baseUrl) {
        try {
            
            String apiUrl = baseUrl != null ? baseUrl : bitbucketApiUrl;

            // Bitbucket Cloud doesn't expose rate limit headers in the same way as GitHub/GitLab
            // For Bitbucket Cloud, we return a conservative status
            // For Bitbucket Server, rate limiting is typically configured differently
            
            if (apiUrl.contains("api.bitbucket.org")) {
                // Bitbucket Cloud - conservative approach
                return new RateLimitStatus(PLATFORM_NAME, 4000, 5000, System.currentTimeMillis() + 3600000, 1000);
            } else {
                // Bitbucket Server - even more conservative
                return new RateLimitStatus(PLATFORM_NAME, 800, 1000, System.currentTimeMillis() + 3600000, 200);
            }

        } catch (WebClientResponseException e) {
            logger.warn("Rate limit check failed for Bitbucket: {} - {}", e.getStatusCode(), e.getMessage());
            
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                logger.error("Unauthorized access to Bitbucket API. Please check your credentials.");
                throw new RateLimitExceededException(PLATFORM_NAME, 0, 1000, 100.0, System.currentTimeMillis() + 3600000);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                logger.error("Forbidden access to Bitbucket API. Rate limit may be exceeded.");
                throw new RateLimitExceededException(PLATFORM_NAME, 1000, 1000, 100.0, System.currentTimeMillis() + 3600000);
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                logger.error("Too many requests to Bitbucket API. Rate limit exceeded.");
                throw new RateLimitExceededException(PLATFORM_NAME, 1000, 1000, 100.0, System.currentTimeMillis() + 3600000);
            }
            
            // For other errors, return a conservative status
            return new RateLimitStatus(PLATFORM_NAME, 500, 1000, System.currentTimeMillis() + 3600000, 500);
            
        } catch (Exception e) {
            logger.error("Unexpected error checking Bitbucket rate limit: {}", e.getMessage(), e);
            // Return conservative status in case of errors
            return new RateLimitStatus(PLATFORM_NAME, 500, 1000, System.currentTimeMillis() + 3600000, 500);
        }
    }

    @Override
    public double getDefaultThreshold() {
        return bitbucketThreshold > 0 && bitbucketThreshold <= 1.0 ? bitbucketThreshold : 0.8;
    }

    @Override
    public boolean supports(String platform) {
        return PLATFORM_NAME.equalsIgnoreCase(platform);
    }

}