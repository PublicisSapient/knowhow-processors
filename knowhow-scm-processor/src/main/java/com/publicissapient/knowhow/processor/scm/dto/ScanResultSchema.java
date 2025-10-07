package com.publicissapient.knowhow.processor.scm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Schema class for documenting ScanResult in OpenAPI/Swagger.
 * This mirrors the GitScannerService.ScanResult structure for API documentation.
 */
@Schema(
    name = "ScanResult",
    description = "Result of a Git repository scan operation containing statistics and metadata"
)
public class ScanResultSchema {

    @Schema(
        description = "The URL of the scanned repository",
        example = "https://github.com/owner/repository"
    )
    private String repositoryUrl;

    @Schema(
        description = "The name of the scanned repository",
        example = "owner/repository"
    )
    private String repositoryName;

    @Schema(
        description = "Timestamp when the scan started",
        example = "2024-01-15T10:30:00"
    )
    private LocalDateTime startTime;

    @Schema(
        description = "Timestamp when the scan completed",
        example = "2024-01-15T10:32:30"
    )
    private LocalDateTime endTime;

    @Schema(
        description = "Total scan duration in milliseconds",
        example = "150000"
    )
    private long durationMs;

    @Schema(
        description = "Number of commits found and processed",
        example = "245"
    )
    private int commitsFound;

    @Schema(
        description = "Number of merge requests/pull requests found and processed",
        example = "32"
    )
    private int mergeRequestsFound;

    @Schema(
        description = "Number of unique users found and processed",
        example = "15"
    )
    private int usersFound;

    @Schema(
        description = "Whether the scan completed successfully",
        example = "true"
    )
    private boolean success;

    @Schema(
        description = "Error message if the scan failed (null if successful)",
        example = "null"
    )
    private String errorMessage;

    // Getters and setters for schema documentation
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public int getCommitsFound() { return commitsFound; }
    public void setCommitsFound(int commitsFound) { this.commitsFound = commitsFound; }

    public int getMergeRequestsFound() { return mergeRequestsFound; }
    public void setMergeRequestsFound(int mergeRequestsFound) { this.mergeRequestsFound = mergeRequestsFound; }

    public int getUsersFound() { return usersFound; }
    public void setUsersFound(int usersFound) { this.usersFound = usersFound; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}