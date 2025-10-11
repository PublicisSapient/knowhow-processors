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

import lombok.Getter;

/**
 * Represents the current rate limit status for an API platform.
 * 
 * This immutable data class contains all the information needed
 * to make rate limiting decisions.
 */
@Getter
public class RateLimitStatus {

    /**
     * -- GETTER --
     *  Gets the platform name (e.g., "GitHub", "GitLab").
     *
     * @return the platform name
     */
    private final String platform;
    /**
     * -- GETTER --
     *  Gets the number of remaining API calls before hitting the limit.
     *
     * @return the remaining API calls
     */
    private final int remaining;
    /**
     * -- GETTER --
     *  Gets the total API rate limit.
     *
     * @return the maximum number of API calls allowed
     */
    private final int limit;
    /**
     * -- GETTER --
     *  Gets the Unix timestamp when the rate limit will reset.
     *
     * @return the reset time as Unix timestamp
     */
    private final long resetTime;
    /**
     * -- GETTER --
     *  Gets the number of API calls already used.
     *
     * @return the number of used API calls
     */
    private final int used;

    public RateLimitStatus(String platform, int remaining, int limit, long resetTime, int used) {
        this.platform = platform;
        this.remaining = remaining;
        this.limit = limit;
        this.resetTime = resetTime;
        this.used = used;
    }

    /**
     * Calculate the current usage percentage.
     * 
     * @return usage percentage as a decimal (0.0 to 1.0)
     */
    public double getUsagePercentage() {
        if (limit == 0) {
            return 0.0;
        }
        return (double) used / limit;
    }

    /**
     * Check if the usage has exceeded the given threshold.
     * 
     * @param threshold threshold as a decimal (e.g., 0.8 for 80%)
     * @return true if usage exceeds threshold
     */
    public boolean exceedsThreshold(double threshold) {
        return getUsagePercentage() >= threshold;
    }

    @Override
    public String toString() {
        return String.format("RateLimitStatus{platform='%s', used=%d, remaining=%d, limit=%d, resetTime=%d, usage=%.2f%%}",
                platform, used, remaining, limit, resetTime, getUsagePercentage() * 100);
    }
}