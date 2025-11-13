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

package com.publicissapient.knowhow.processor.scm.service.ratelimit.impl;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitMonitor;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitStatus;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * GitHub-specific implementation of RateLimitMonitor.
 * 
 * This monitor uses the GitHub API to check current rate limit status
 * and validates against configured thresholds.
 */
@Component
public class GitHubRateLimitMonitor implements RateLimitMonitor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRateLimitMonitor.class);
    private static final String PLATFORM_NAME = "GitHub";

    @Value("${git.platforms.github.api-url:https://api.github.com}")
    private String githubApiUrl;

    @Value("${git.scanner.rate-limit.github.threshold:0.8}")
    private double githubThreshold;

    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }

    @Override
    public RateLimitStatus checkRateLimit(String token, String baseUrl) throws IOException {
        logger.debug("Checking GitHub rate limit status");

        GitHub github = createGitHubClient(token);
        GHRateLimit rateLimit = github.getRateLimit();

        // GitHub provides core rate limit info
        GHRateLimit.Record coreLimit = rateLimit.getCore();

        int limit = coreLimit.getLimit();
        int remaining = coreLimit.getRemaining();
        int used = limit - remaining;
        long resetTime = coreLimit.getResetDate().getTime();

        RateLimitStatus status = new RateLimitStatus(PLATFORM_NAME, remaining, limit, resetTime, used);

        logger.debug("GitHub rate limit status: {}", status);
        return status;
    }

    @Override
    public double getDefaultThreshold() {
        return githubThreshold > 0 ? githubThreshold : 0.8;
    }

    private GitHub createGitHubClient(String token) throws IOException {
        if (token != null && !token.isEmpty()) {
            return new GitHubBuilder()
                    .withEndpoint(githubApiUrl)
                    .withOAuthToken(token)
                    .build();
        } else {
            return GitHub.connectAnonymously();
        }
    }
}