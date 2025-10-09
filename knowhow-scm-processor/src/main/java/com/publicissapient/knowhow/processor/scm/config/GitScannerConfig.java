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

package com.publicissapient.knowhow.processor.scm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Git scanning operations.
 * 
 * This class holds configuration for scanning behavior, performance tuning,
 * and operational parameters.
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "git.scanner")
public class GitScannerConfig {

    // Getters and setters
    private String defaultCommitStrategy = "jgit";
    private boolean useRestApiForCommits = false;
    private int defaultCommitLimit = 1000;
    private int defaultMergeRequestLimit = 500;
    
    private Scheduled scheduled = new Scheduled();
    private Performance performance = new Performance();
    private Storage storage = new Storage();

    /**
     * Scheduled scanning configuration.
     */
    @Setter
    @Getter
    public static class Scheduled {
        // Getters and setters
        private boolean enabled = true;
        private String cronExpression = "0 0 2 * * ?"; // Daily at 2 AM
        private int batchSize = 10;
        private int parallelThreads = 3;
        private int timeoutMinutes = 30;
        private boolean skipRecentlyScanned = true;
        private int skipRecentlyScannedHours = 24;

    }

    /**
     * Performance and resource configuration.
     */
    @Setter
    @Getter
    public static class Performance {
        // Getters and setters
        private int maxConcurrentScans = 5;
        private int httpTimeoutSeconds = 30;
        private int httpRetryAttempts = 3;
        private long httpRetryDelayMs = 1000;
        private int jgitCloneTimeoutMinutes = 10;
        private boolean enableRateLimiting = true;
        private int rateLimitRequestsPerSecond = 10;

    }

    /**
     * Storage and cleanup configuration.
     */
    @Setter
    @Getter
    public static class Storage {
        // Getters and setters
        private String tempDirectory = System.getProperty("java.io.tmpdir");
        private boolean cleanupTempFiles = true;
        private int tempFileRetentionHours = 24;
        private boolean enableDataCompression = false;
        private int batchInsertSize = 100;
        private boolean enableTransactionBatching = true;

        // Enhanced cleanup getters and setters
        // Enhanced cleanup configuration for Windows file handle issues
        private int cleanupRetryAttempts = 3;
        private int cleanupRetryDelayMs = 100;
        private int cleanupFinalDelayMs = 500;
        private boolean forceGcOnCleanupFailure = true;

    }
}