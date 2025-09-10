package com.publicissapient.knowhow.processor.scm.service.ratelimit;

/**
 * Represents the current rate limit status for an API platform.
 * 
 * This immutable data class contains all the information needed
 * to make rate limiting decisions.
 */
public class RateLimitStatus {
    
    private final String platform;
    private final int remaining;
    private final int limit;
    private final long resetTime;
    private final int used;

    public RateLimitStatus(String platform, int remaining, int limit, long resetTime, int used) {
        this.platform = platform;
        this.remaining = remaining;
        this.limit = limit;
        this.resetTime = resetTime;
        this.used = used;
    }

    /**
     * Gets the platform name (e.g., "GitHub", "GitLab").
     *
     * @return the platform name
     */
    public String getPlatform() {
        return platform;
    }

    /**
     * Gets the number of remaining API calls before hitting the limit.
     *
     * @return the remaining API calls
     */
    public int getRemaining() {
        return remaining;
    }

    /**
     * Gets the total API rate limit.
     *
     * @return the maximum number of API calls allowed
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Gets the Unix timestamp when the rate limit will reset.
     *
     * @return the reset time as Unix timestamp
     */
    public long getResetTime() {
        return resetTime;
    }

    /**
     * Gets the number of API calls already used.
     *
     * @return the number of used API calls
     */
    public int getUsed() {
        return used;
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