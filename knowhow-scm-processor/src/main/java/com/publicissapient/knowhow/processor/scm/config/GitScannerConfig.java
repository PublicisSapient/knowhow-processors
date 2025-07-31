package com.publicissapient.knowhow.processor.scm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Git scanning operations.
 * 
 * This class holds configuration for scanning behavior, performance tuning,
 * and operational parameters.
 */
@Configuration
@ConfigurationProperties(prefix = "git.scanner")
public class GitScannerConfig {

    private String defaultCommitStrategy = "jgit";
    private boolean useRestApiForCommits = false;
    private int defaultCommitLimit = 1000;
    private int defaultMergeRequestLimit = 500;
    
    private Scheduled scheduled = new Scheduled();
    private Performance performance = new Performance();
    private Storage storage = new Storage();

    // Getters and setters
    public String getDefaultCommitStrategy() { return defaultCommitStrategy; }
    public void setDefaultCommitStrategy(String defaultCommitStrategy) { this.defaultCommitStrategy = defaultCommitStrategy; }

    public boolean isUseRestApiForCommits() { return useRestApiForCommits; }
    public void setUseRestApiForCommits(boolean useRestApiForCommits) { this.useRestApiForCommits = useRestApiForCommits; }

    public int getDefaultCommitLimit() { return defaultCommitLimit; }
    public void setDefaultCommitLimit(int defaultCommitLimit) { this.defaultCommitLimit = defaultCommitLimit; }

    public int getDefaultMergeRequestLimit() { return defaultMergeRequestLimit; }
    public void setDefaultMergeRequestLimit(int defaultMergeRequestLimit) { this.defaultMergeRequestLimit = defaultMergeRequestLimit; }

    public Scheduled getScheduled() { return scheduled; }
    public void setScheduled(Scheduled scheduled) { this.scheduled = scheduled; }

    public Performance getPerformance() { return performance; }
    public void setPerformance(Performance performance) { this.performance = performance; }

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    /**
     * Scheduled scanning configuration.
     */
    public static class Scheduled {
        private boolean enabled = true;
        private String cronExpression = "0 0 2 * * ?"; // Daily at 2 AM
        private int batchSize = 10;
        private int parallelThreads = 3;
        private int timeoutMinutes = 30;
        private boolean skipRecentlyScanned = true;
        private int skipRecentlyScannedHours = 24;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getParallelThreads() { return parallelThreads; }
        public void setParallelThreads(int parallelThreads) { this.parallelThreads = parallelThreads; }

        public int getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(int timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }

        public boolean isSkipRecentlyScanned() { return skipRecentlyScanned; }
        public void setSkipRecentlyScanned(boolean skipRecentlyScanned) { this.skipRecentlyScanned = skipRecentlyScanned; }

        public int getSkipRecentlyScannedHours() { return skipRecentlyScannedHours; }
        public void setSkipRecentlyScannedHours(int skipRecentlyScannedHours) { this.skipRecentlyScannedHours = skipRecentlyScannedHours; }
    }

    /**
     * Performance and resource configuration.
     */
    public static class Performance {
        private int maxConcurrentScans = 5;
        private int httpTimeoutSeconds = 30;
        private int httpRetryAttempts = 3;
        private long httpRetryDelayMs = 1000;
        private int jgitCloneTimeoutMinutes = 10;
        private boolean enableRateLimiting = true;
        private int rateLimitRequestsPerSecond = 10;

        // Getters and setters
        public int getMaxConcurrentScans() { return maxConcurrentScans; }
        public void setMaxConcurrentScans(int maxConcurrentScans) { this.maxConcurrentScans = maxConcurrentScans; }

        public int getHttpTimeoutSeconds() { return httpTimeoutSeconds; }
        public void setHttpTimeoutSeconds(int httpTimeoutSeconds) { this.httpTimeoutSeconds = httpTimeoutSeconds; }

        public int getHttpRetryAttempts() { return httpRetryAttempts; }
        public void setHttpRetryAttempts(int httpRetryAttempts) { this.httpRetryAttempts = httpRetryAttempts; }

        public long getHttpRetryDelayMs() { return httpRetryDelayMs; }
        public void setHttpRetryDelayMs(long httpRetryDelayMs) { this.httpRetryDelayMs = httpRetryDelayMs; }

        public int getJgitCloneTimeoutMinutes() { return jgitCloneTimeoutMinutes; }
        public void setJgitCloneTimeoutMinutes(int jgitCloneTimeoutMinutes) { this.jgitCloneTimeoutMinutes = jgitCloneTimeoutMinutes; }

        public boolean isEnableRateLimiting() { return enableRateLimiting; }
        public void setEnableRateLimiting(boolean enableRateLimiting) { this.enableRateLimiting = enableRateLimiting; }

        public int getRateLimitRequestsPerSecond() { return rateLimitRequestsPerSecond; }
        public void setRateLimitRequestsPerSecond(int rateLimitRequestsPerSecond) { this.rateLimitRequestsPerSecond = rateLimitRequestsPerSecond; }
    }

    /**
     * Storage and cleanup configuration.
     */
    public static class Storage {
        private String tempDirectory = System.getProperty("java.io.tmpdir");
        private boolean cleanupTempFiles = true;
        private int tempFileRetentionHours = 24;
        private boolean enableDataCompression = false;
        private int batchInsertSize = 100;
        private boolean enableTransactionBatching = true;

        // Enhanced cleanup configuration for Windows file handle issues
        private int cleanupRetryAttempts = 3;
        private int cleanupRetryDelayMs = 100;
        private int cleanupFinalDelayMs = 500;
        private boolean forceGcOnCleanupFailure = true;

        // Getters and setters
        public String getTempDirectory() { return tempDirectory; }
        public void setTempDirectory(String tempDirectory) { this.tempDirectory = tempDirectory; }

        public boolean isCleanupTempFiles() { return cleanupTempFiles; }
        public void setCleanupTempFiles(boolean cleanupTempFiles) { this.cleanupTempFiles = cleanupTempFiles; }

        public int getTempFileRetentionHours() { return tempFileRetentionHours; }
        public void setTempFileRetentionHours(int tempFileRetentionHours) { this.tempFileRetentionHours = tempFileRetentionHours; }

        public boolean isEnableDataCompression() { return enableDataCompression; }
        public void setEnableDataCompression(boolean enableDataCompression) { this.enableDataCompression = enableDataCompression; }

        public int getBatchInsertSize() { return batchInsertSize; }
        public void setBatchInsertSize(int batchInsertSize) { this.batchInsertSize = batchInsertSize; }

        public boolean isEnableTransactionBatching() { return enableTransactionBatching; }
        public void setEnableTransactionBatching(boolean enableTransactionBatching) { this.enableTransactionBatching = enableTransactionBatching; }

        // Enhanced cleanup getters and setters
        public int getCleanupRetryAttempts() { return cleanupRetryAttempts; }
        public void setCleanupRetryAttempts(int cleanupRetryAttempts) { this.cleanupRetryAttempts = cleanupRetryAttempts; }

        public int getCleanupRetryDelayMs() { return cleanupRetryDelayMs; }
        public void setCleanupRetryDelayMs(int cleanupRetryDelayMs) { this.cleanupRetryDelayMs = cleanupRetryDelayMs; }

        public int getCleanupFinalDelayMs() { return cleanupFinalDelayMs; }
        public void setCleanupFinalDelayMs(int cleanupFinalDelayMs) { this.cleanupFinalDelayMs = cleanupFinalDelayMs; }

        public boolean isForceGcOnCleanupFailure() { return forceGcOnCleanupFailure; }
        public void setForceGcOnCleanupFailure(boolean forceGcOnCleanupFailure) { this.forceGcOnCleanupFailure = forceGcOnCleanupFailure; }
    }
}