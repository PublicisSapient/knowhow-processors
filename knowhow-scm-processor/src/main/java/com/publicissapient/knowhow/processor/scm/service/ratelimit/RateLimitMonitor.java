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

/**
 * Interface for monitoring and managing API rate limits across different platforms.
 * 
 * This interface follows the Open/Closed Principle by allowing new platforms
 * to be added without modifying existing code. Each platform can implement
 * its own rate limit monitoring strategy.
 */
public interface RateLimitMonitor {

    /**
     * Get the platform name this monitor handles.
     * 
     * @return platform name (e.g., "GitHub", "GitLab", "Azure DevOps")
     */
    String getPlatformName();

    /**
     * Check the current rate limit status for the platform.
     * 
     * @param token authentication token for the platform
     * @return current rate limit status
     * @throws Exception if unable to check rate limit status
     */
    RateLimitStatus checkRateLimit(String token, String baseUrl) throws Exception;

    /**
     * Get the default threshold percentage for this platform.
     * 
     * @return default threshold as a decimal (e.g., 0.8 for 80%)
     */
    default double getDefaultThreshold() {
        return 0.8; // 80% by default
    }

    /**
     * Check if this monitor supports the given platform.
     * 
     * @param platform platform name to check
     * @return true if this monitor supports the platform
     */
    default boolean supports(String platform) {
        return getPlatformName().equalsIgnoreCase(platform);
    }
}