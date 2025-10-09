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

package com.publicissapient.knowhow.processor.scm.exception;

/**
 * Exception thrown when API rate limit threshold is exceeded.
 * 
 * This exception is thrown when the rate limit usage reaches the configured
 * threshold (default 80%) to prevent hitting the actual rate limit and avoid
 * long wait times.
 */
public class RateLimitExceededException extends DataProcessingException {

	private final String platform;
	private final int currentUsage;
	private final int totalLimit;
	private final double thresholdPercentage;
	private final long resetTime;

	public RateLimitExceededException(String platform, int currentUsage, int totalLimit, double thresholdPercentage,
			long resetTime) {
		super(String.format(
				"Rate limit threshold (%.0f%%) exceeded for %s platform. "
						+ "Current usage: %d/%d. Reset time: %d. Stopping scan to prevent rate limit violation.",
				thresholdPercentage * 100, platform, currentUsage, totalLimit, resetTime));
		this.platform = platform;
		this.currentUsage = currentUsage;
		this.totalLimit = totalLimit;
		this.thresholdPercentage = thresholdPercentage;
		this.resetTime = resetTime;
	}

	/**
	 * Gets the platform name for this exception.
	 *
	 * @return the platform name (e.g., "GitHub", "GitLab")
	 */
	public String getPlatform() {
		return platform;
	}

	public int getCurrentUsage() {
		return currentUsage;
	}

	public int getTotalLimit() {
		return totalLimit;
	}

	public double getThresholdPercentage() {
		return thresholdPercentage;
	}

	public long getResetTime() {
		return resetTime;
	}

	/**
	 * Gets the current usage percentage of the rate limit.
	 *
	 * @return the current usage as a percentage (0.0 to 1.0)
	 */
	public double getCurrentUsagePercentage() {
		return (double) currentUsage / totalLimit;
	}
}