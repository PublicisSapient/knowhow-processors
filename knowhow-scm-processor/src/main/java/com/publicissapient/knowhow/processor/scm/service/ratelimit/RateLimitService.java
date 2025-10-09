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

package com.publicissapient.knowhow.processor.scm.service.ratelimit;

import com.publicissapient.knowhow.processor.scm.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for managing rate limit monitoring across different SCM platforms.
 * 
 * This service acts as a facade for all rate limit monitoring operations,
 * delegating to platform-specific monitors while providing a unified interface.
 * 
 * Follows the Strategy pattern by using different monitors for different platforms.
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    private final Map<String, RateLimitMonitor> monitors;

    @Value("${git.scanner.rate-limit.threshold:0.8}")
    private double defaultThreshold;

    @Value("${git.scanner.rate-limit.enabled:true}")
    private boolean rateLimitCheckEnabled;

    @Value("${git.scanner.rate-limit.max-cooldown-hours:24}")
    private int maxCooldownHours;

    @Value("${git.scanner.rate-limit.fail-on-excessive-cooldown:false}")
    private boolean failOnExcessiveCooldown;

    @Autowired
    public RateLimitService(List<RateLimitMonitor> monitorList) {
        this.monitors = monitorList.stream()
                .collect(Collectors.toMap(
                    monitor -> monitor.getPlatformName().toLowerCase(),
                    Function.identity()
                ));
        
        logger.info("Initialized RateLimitService with {} monitors: {}", 
                   monitors.size(), monitors.keySet());
        logger.info("Rate limit configuration - enabled: {}, threshold: {}%, max-cooldown: {} hours, fail-on-excessive-cooldown: {}", 
                   rateLimitCheckEnabled, defaultThreshold * 100, maxCooldownHours, failOnExcessiveCooldown);
    }

    /**
     * Check rate limit for a specific platform before making API calls.
     * If rate limit threshold is exceeded, logs the information and waits for the platform's cooldown period.
     * 
     * @param platform platform name (e.g., "GitHub", "GitLab")
     * @param token authentication token
     * @param repositoryName repository name for logging context (optional)
     */
    public void checkRateLimit(String platform, String token, String repositoryName, String baseUrl) {
        if (!rateLimitCheckEnabled) {
            logger.debug("Rate limit checking is disabled");
            return;
        }

        if (token == null || token.trim().isEmpty()) {
            logger.warn("No token provided for rate limit check on platform: {}", platform);
            return;
        }

        RateLimitMonitor monitor = getMonitor(platform);
        if (monitor == null) {
            logger.warn("No rate limit monitor found for platform: {}", platform);
            return;
        }

        try {
            double threshold = getThresholdForPlatform(monitor);
            RateLimitStatus status = monitor.checkRateLimit(token, baseUrl);

            if (logger.isDebugEnabled()) {
                logger.debug("Rate limit status for {} ({}): {}/{} requests used ({}%), remaining: {}",
                            platform, repositoryName, status.getUsed(), status.getLimit(),
                            String.format("%.1f", status.getUsagePercentage() * 100), status.getRemaining());
            }

            if (status != null && status.exceedsThreshold(threshold)) {
                handleRateLimitExceeded(platform, status, threshold, repositoryName);
            } else {
                logger.debug("Rate limit check passed for platform: {} (repository: {})", platform, repositoryName);
            }
        } catch (Exception e) {
            logger.warn("Failed to check rate limit for platform {} (repository: {}): {}", platform, repositoryName, e.getMessage());
            // Don't fail the scan if we can't check rate limits, just log the warning
        }
    }


    /**
     * Handles rate limit exceeded scenario by logging detailed information and waiting for the platform's cooldown period.
     * The wait time is determined by the platform's rate limit reset time, not an arbitrary maximum.
     * 
     * @param platform platform name
     * @param status current rate limit status
     * @param threshold configured threshold
     * @param repositoryName repository name for context
     */
    private void handleRateLimitExceeded(String platform, RateLimitStatus status, double threshold, String repositoryName) {
        // Calculate wait time until reset based on platform's reset time
        long currentTimeMillis = System.currentTimeMillis();
        long resetTimeMillis = status.getResetTime();
        long waitTimeMillis = resetTimeMillis - currentTimeMillis;
        
        // Convert timestamps to readable format
        LocalDateTime resetTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(resetTimeMillis), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Log detailed rate limit information
        logger.warn("=== RATE LIMIT THRESHOLD EXCEEDED ===");
        logger.warn("Platform: {}", platform);
        logger.warn("Repository: {}", repositoryName != null ? repositoryName : "N/A");
        logger.warn("Current Usage: {}/{} requests ({}%)",
                   status.getUsed(), status.getLimit(),
                   String.format("%.1f", status.getUsagePercentage() * 100));
        logger.warn("Threshold: {}%", String.format("%.1f", threshold * 100));
        logger.warn("Remaining Requests: {}", status.getRemaining());
        logger.warn("Rate Limit Reset Time: {}", resetTime.format(formatter));
        
        if (waitTimeMillis > 0) {
            long waitTimeSeconds = waitTimeMillis / 1000;
            long waitTimeMinutes = waitTimeSeconds / 60;
            long waitTimeHours = waitTimeMinutes / 60;
            long maxCooldownMillis = maxCooldownHours * 60 * 60 * 1000L;
            
            logger.warn("Platform cooldown time: {} seconds ({} minutes, {} hours)", waitTimeSeconds, waitTimeMinutes, waitTimeHours);
            logger.warn("Maximum allowed cooldown: {} hours", maxCooldownHours);
            
            // Check if the platform's cooldown time is reasonable
            if (waitTimeMillis > maxCooldownMillis) {
                logger.error("Platform cooldown time ({} hours) exceeds maximum reasonable cooldown ({} hours)", 
                           waitTimeHours, maxCooldownHours);
                logger.error("This may indicate an issue with the platform's rate limit reset time or system clock");
                
                if (failOnExcessiveCooldown) {
                    logger.error("Failing operation due to excessive cooldown time and fail-on-excessive-cooldown=true");
                    throw new RateLimitExceededException(platform, status.getUsed(), status.getLimit(), threshold, status.getResetTime());
                } else {
                    logger.warn("Skipping rate limit wait due to excessive cooldown time. Continuing with reduced API calls.");
                    logger.warn("Consider checking system clock synchronization or platform status for: {}", platform);
                    return;
                }
            }
            
            logger.warn("Waiting for platform's rate limit cooldown: {} seconds ({} minutes, {} hours)", 
                       waitTimeSeconds, waitTimeMinutes, waitTimeHours);
            logger.warn("Thread will sleep until platform rate limit resets...");
            logger.info("RATE LIMIT SLEEP STARTED - Platform: {}, Repository: {}, Cooldown Time: {} hours {} minutes", 
                       platform, repositoryName, waitTimeHours, waitTimeMinutes % 60);
            
            try {
                // Add a small buffer (30 seconds) to ensure rate limit has reset
                long bufferMillis = 30 * 1000L;
                long totalWaitTime = waitTimeMillis + bufferMillis;
                
                logger.info("Sleeping for {} milliseconds (platform cooldown + 30s buffer)", totalWaitTime);
                Thread.sleep(totalWaitTime);
                
                logger.info("RATE LIMIT SLEEP COMPLETED - Platform: {}, Repository: {}", platform, repositoryName);
                logger.info("Platform rate limit cooldown completed for: {} (repository: {})", platform, repositoryName);
                
            } catch (InterruptedException e) {
                logger.error("Thread interrupted while waiting for platform rate limit cooldown: {}", e.getMessage());
                logger.error("RATE LIMIT SLEEP INTERRUPTED - Platform: {}, Repository: {}", platform, repositoryName);
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        } else {
            logger.warn("Platform rate limit reset time has already passed, continuing with API calls");
        }
        
        logger.warn("=== END RATE LIMIT HANDLING ===");
    }

    private RateLimitMonitor getMonitor(String platform) {
        return monitors.get(platform.toLowerCase());
    }

    private double getThresholdForPlatform(RateLimitMonitor monitor) {
        // Could be extended to have platform-specific thresholds
        return defaultThreshold > 0 ? defaultThreshold : monitor.getDefaultThreshold();
    }
}