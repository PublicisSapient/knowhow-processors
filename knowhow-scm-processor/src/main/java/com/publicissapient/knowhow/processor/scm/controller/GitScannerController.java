package com.publicissapient.knowhow.processor.scm.controller;

import com.publicissapient.knowhow.processor.scm.domain.model.ScmProcessor;
import com.publicissapient.knowhow.processor.scm.dto.ScanResultSchema;
import com.publicissapient.knowhow.processor.scm.service.core.GitScannerService;
import com.publicissapient.kpidashboard.common.constant.ProcessorType;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionBasicConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for Git scanning operations.
 * 
 * Provides endpoints for:
 * - Triggering repository scans
 * - Checking scan status
 * - Retrieving scan results
 */
@RestController
@RequestMapping("/api/v1/git-scanner")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Repository Scanning", description = "Operations for scanning Git repositories and collecting metadata")
public class GitScannerController {

    private static final Logger logger = LoggerFactory.getLogger(GitScannerController.class);

    @Autowired
    private GitScannerService gitScannerService;

    /**
     * Triggers a synchronous repository scan.
     * 
     * @param request the scan request
     * @return scan results
     */
    @PostMapping("/scan")
    @Operation(
        summary = "Scan Git Repository (Synchronous)",
        description = "Triggers a synchronous scan of a Git repository to collect commits, merge requests, and user data. " +
                     "The operation will wait for completion and return detailed scan results including statistics.",
        tags = {"Repository Scanning"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Scan completed successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitScannerApiResponse.class),
                examples = @ExampleObject(
                    name = "Successful Scan",
                    value = """
                        {
                          "success": true,
                          "message": "Success",
                          "data": {
                            "repositoryUrl": "https://github.com/owner/repo",
                            "repositoryName": "owner/repo",
                            "startTime": "2024-01-15T10:30:00",
                            "endTime": "2024-01-15T10:32:30",
                            "durationMs": 150000,
                            "commitsFound": 245,
                            "mergeRequestsFound": 32,
                            "usersFound": 15,
                            "success": true,
                            "errorMessage": null
                          },
                          "timestamp": "2024-01-15T10:32:30"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitScannerApiResponse.class),
                examples = @ExampleObject(
                    name = "Validation Error",
                    value = """
                        {
                          "success": false,
                          "message": "Repository URL is required",
                          "data": null,
                          "timestamp": "2024-01-15T10:30:00"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error during scanning",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitScannerApiResponse.class),
                examples = @ExampleObject(
                    name = "Scan Error",
                    value = """
                        {
                          "success": false,
                          "message": "Scan failed: Repository not accessible",
                          "data": null,
                          "timestamp": "2024-01-15T10:30:00"
                        }
                        """
                )
            )
        )
    })
    public ResponseEntity<GitScannerApiResponse<GitScannerService.ScanResult>> scanRepository(
            @Parameter(description = "Repository scan request details", required = true)
            @Valid @RequestBody ScanRepositoryRequest request) {
        
        logger.info("Received scan request for repository: {}", request.getRepositoryUrl());
        
        try {
            GitScannerService.ScanRequest scanRequest = GitScannerService.ScanRequest.builder()
                    .repositoryUrl(request.getRepositoryUrl())
                    .repositoryName(request.getRepositoryName())
                    .token(request.getAccessToken())
                    .username(request.getUsername())
                    .branchName(request.getBranch())
                    .cloneEnabled(request.getIsCloneEnabled())
                    .toolType(request.getToolType().name())
                    .toolConfigId(new ObjectId())
                    .lastScanFrom(request.getLastScanFrom())
                    .build();

            GitScannerService.ScanResult result = gitScannerService.scanRepository(scanRequest);
            
            GitScannerApiResponse<GitScannerService.ScanResult> response = GitScannerApiResponse.<GitScannerService.ScanResult>builder()
                    .success(true)
                    .message("Success")
                    .data(result)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            logger.info("Scan completed successfully for repository: {}", request.getRepositoryUrl());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error scanning repository: {}", request.getRepositoryUrl(), e);
            
            GitScannerApiResponse<GitScannerService.ScanResult> response = GitScannerApiResponse.<GitScannerService.ScanResult>builder()
                    .success(false)
                    .message("Scan failed: " + e.getMessage())
                    .data(null)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

//    @PostMapping("/scan/project")
//    public ResponseEntity<GitScannerApiResponse<AsyncScanResponse>> scanProject(
//            @Parameter(description = "Repository scan request details", required = true)
//            @Valid @RequestBody ProcessorExecutionBasicConfig processorExecutionBasicConfig) {
//
//        try {
//
//            CompletableFuture<GitScannerService.ScanResult> futureResult = gitScannerService.scanRepositoryAsync(scanRequest);
//
//            String taskId = "task_" + System.currentTimeMillis() + "_" + Math.abs(request.getRepositoryUrl().hashCode());
//
//            AsyncScanResponse asyncResponse = AsyncScanResponse.builder()
//                    .taskId(taskId)
//                    .repositoryUrl(request.getRepositoryUrl())
//                    .status("STARTED")
//                    .timestamp(LocalDateTime.now())
//                    .build();
//
//            GitScannerApiResponse<AsyncScanResponse> response = GitScannerApiResponse.<AsyncScanResponse>builder()
//                    .success(true)
//                    .message("Success")
//                    .data(asyncResponse)
//                    .timestamp(LocalDateTime.now())
//                    .build();
//
//            logger.info("Async scan started for repository: {}", request.getRepositoryUrl());
//            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
//
//        } catch (Exception e) {
//            logger.error("Error starting async scan for repository: {}", request.getRepositoryUrl(), e);
//
//            GitScannerApiResponse<AsyncScanResponse> response = GitScannerApiResponse.<AsyncScanResponse>builder()
//                    .success(false)
//                    .message("Failed to start async scan: " + e.getMessage())
//                    .data(null)
//                    .timestamp(LocalDateTime.now())
//                    .build();
//
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
//        }
//    }

    /**
     * Triggers an asynchronous repository scan.
     * 
     * @param request the scan request
     * @return async scan response with task ID
     */
    @PostMapping("/scan/async")
    @Operation(
        summary = "Scan Git Repository (Asynchronous)",
        description = "Triggers an asynchronous scan of a Git repository. Returns immediately with a task ID " +
                     "that can be used to check the scan status and retrieve results when completed.",
        tags = {"Repository Scanning"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Scan started successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitScannerApiResponse.class),
                examples = @ExampleObject(
                    name = "Async Scan Started",
                    value = """
                        {
                          "success": true,
                          "message": "Success",
                          "data": {
                            "taskId": "task_1705312200000_123456789",
                            "repositoryUrl": "https://github.com/owner/repo",
                            "status": "STARTED",
                            "timestamp": "2024-01-15T10:30:00"
                          },
                          "timestamp": "2024-01-15T10:30:00"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitScannerApiResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error starting async scan",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitScannerApiResponse.class)
            )
        )
    })
    public ResponseEntity<GitScannerApiResponse<AsyncScanResponse>> scanRepositoryAsync(
            @Parameter(description = "Repository scan request details", required = true)
            @Valid @RequestBody ScanRepositoryRequest request) {
        
        logger.info("Received async scan request for repository: {}", request.getRepositoryUrl());
        
        try {
            GitScannerService.ScanRequest scanRequest = GitScannerService.ScanRequest.builder()
                    .repositoryUrl(request.getRepositoryUrl())
                    .repositoryName(request.getRepositoryName())
                    .token(request.getAccessToken())
                    .username(request.getUsername())
                    .branchName(request.getBranch())
                    .cloneEnabled(request.getIsCloneEnabled())
                    .toolType(request.getToolType().name())
                    .toolConfigId(new ObjectId())
                    .lastScanFrom(request.getLastScanFrom())
                    .build();

            CompletableFuture<GitScannerService.ScanResult> futureResult = gitScannerService.scanRepositoryAsync(scanRequest);

            String taskId = "task_" + System.currentTimeMillis() + "_" + Math.abs(request.getRepositoryUrl().hashCode());
            
            AsyncScanResponse asyncResponse = AsyncScanResponse.builder()
                    .taskId(taskId)
                    .repositoryUrl(request.getRepositoryUrl())
                    .status("STARTED")
                    .timestamp(LocalDateTime.now())
                    .build();
            
            GitScannerApiResponse<AsyncScanResponse> response = GitScannerApiResponse.<AsyncScanResponse>builder()
                    .success(true)
                    .message("Success")
                    .data(asyncResponse)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            logger.info("Async scan started for repository: {}", request.getRepositoryUrl());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            logger.error("Error starting async scan for repository: {}", request.getRepositoryUrl(), e);
            
            GitScannerApiResponse<AsyncScanResponse> response = GitScannerApiResponse.<AsyncScanResponse>builder()
                    .success(false)
                    .message("Failed to start async scan: " + e.getMessage())
                    .data(null)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Health check endpoint.
     * 
     * @return health status
     */
    @GetMapping("/health")
    @Operation(
        summary = "Health Check",
        description = "Returns the current health status of the Git Scanner service. " +
                     "Use this endpoint to verify that the service is running and operational.",
        tags = {"Health & Monitoring"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Service is healthy",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitScannerApiResponse.class),
                examples = @ExampleObject(
                    name = "Healthy Service",
                    value = """
                        {
                          "success": true,
                          "message": "Success",
                          "data": {
                            "status": "UP",
                            "timestamp": "2024-01-15T10:30:00",
                            "message": "Git Scanner is running"
                          },
                          "timestamp": "2024-01-15T10:30:00"
                        }
                        """
                )
            )
        )
    })
    public ResponseEntity<GitScannerApiResponse<HealthStatus>> health() {
        HealthStatus healthStatus = HealthStatus.builder()
                .status("UP")
                .timestamp(LocalDateTime.now())
                .message("Git Scanner is running")
                .build();
        
        GitScannerApiResponse<HealthStatus> response = GitScannerApiResponse.<HealthStatus>builder()
                .success(true)
                .message("Success")
                .data(healthStatus)
                .timestamp(LocalDateTime.now())
                .build();
        
        return ResponseEntity.ok(response);
    }

    // DTOs and inner classes

    /**
     * Request DTO for repository scanning operations.
     */
    @Schema(
        name = "ScanRepositoryRequest",
        description = "Request payload for scanning a Git repository"
    )
    public static class ScanRepositoryRequest {
        
        @Schema(
            description = "The URL of the Git repository to scan",
            example = "https://github.com/owner/repository",
            required = true
        )
        @NotBlank(message = "Repository URL is required")
        private String repositoryUrl;

        @Schema(
            description = "The name of the repository (typically owner/repo format)",
            example = "owner/repository",
            required = true
        )
        @NotBlank(message = "Repository name is required")
        private String repositoryName;

        @Schema(
            description = "Access token for authenticating with the Git platform",
            example = "ghp_xxxxxxxxxxxxxxxxxxxx",
            required = true
        )
        @NotBlank(message = "Access token is required")
        private String accessToken;

        @Schema(
            description = "Username for the Git platform account",
            example = "john.doe",
            required = true
        )
        @NotBlank(message = "Username is required")
        private String username;

        @Schema(
            description = "The branch to scan (typically 'main' or 'master')",
            example = "main",
            required = true
        )
        @NotBlank(message = "Branch is required")
        private String branch;

        @Schema(
            description = "Whether to enable local cloning for scanning (JGit strategy)",
            example = "true",
            required = true
        )
        @NotNull(message = "Clone enabled flag is required")
        private Boolean isCloneEnabled;

        @Schema(
            description = "The type of Git platform",
            example = "GITHUB",
            required = true
        )
        @NotNull(message = "Tool type is required")
        private ToolType toolType;

        @Schema(
            description = "Optional tool configuration ID for project-based scanning",
            example = "project-123-github-main"
        )
        private String toolConfigId;

        @Schema(
            description = "Optional timestamp (Unix epoch) to scan from for incremental scanning",
            example = "1705312200000"
        )
        private Long lastScanFrom;

        // Constructors
        public ScanRepositoryRequest() {}

        public ScanRepositoryRequest(String repositoryUrl, String repositoryName, String accessToken, 
                                   String username, String branch, Boolean isCloneEnabled, ToolType toolType,
                                   String toolConfigId, Long lastScanFrom) {
            this.repositoryUrl = repositoryUrl;
            this.repositoryName = repositoryName;
            this.accessToken = accessToken;
            this.username = username;
            this.branch = branch;
            this.isCloneEnabled = isCloneEnabled;
            this.toolType = toolType;
            this.toolConfigId = toolConfigId;
            this.lastScanFrom = lastScanFrom;
        }

        // Getters and Setters
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

        public String getRepositoryName() { return repositoryName; }
        public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }

        public Boolean getIsCloneEnabled() { return isCloneEnabled; }
        public void setIsCloneEnabled(Boolean isCloneEnabled) { this.isCloneEnabled = isCloneEnabled; }

        public ToolType getToolType() { return toolType; }
        public void setToolType(ToolType toolType) { this.toolType = toolType; }

        public String getToolConfigId() { return toolConfigId; }
        public void setToolConfigId(String toolConfigId) { this.toolConfigId = toolConfigId; }

        public Long getLastScanFrom() { return lastScanFrom; }
        public void setLastScanFrom(Long lastScanFrom) { this.lastScanFrom = lastScanFrom; }
    }

    /**
     * Response DTO for asynchronous scan operations.
     */
    @Schema(
        name = "AsyncScanResponse",
        description = "Response payload for asynchronous scan operations"
    )
    public static class AsyncScanResponse {
        
        @Schema(
            description = "Unique task identifier for tracking the async scan",
            example = "task_1705312200000_123456789"
        )
        private String taskId;

        @Schema(
            description = "The URL of the repository being scanned",
            example = "https://github.com/owner/repository"
        )
        private String repositoryUrl;

        @Schema(
            description = "Current status of the scan task",
            example = "STARTED",
            allowableValues = {"STARTED", "IN_PROGRESS", "COMPLETED", "FAILED"}
        )
        private String status;

        @Schema(
            description = "Timestamp when the scan was initiated",
            example = "2024-01-15T10:30:00"
        )
        private LocalDateTime timestamp;

        // Constructors
        public AsyncScanResponse() {}

        public AsyncScanResponse(String taskId, String repositoryUrl, String status, LocalDateTime timestamp) {
            this.taskId = taskId;
            this.repositoryUrl = repositoryUrl;
            this.status = status;
            this.timestamp = timestamp;
        }

        public static AsyncScanResponseBuilder builder() {
            return new AsyncScanResponseBuilder();
        }

        // Getters and Setters
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }

        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        // Builder class
        public static class AsyncScanResponseBuilder {
            private String taskId;
            private String repositoryUrl;
            private String status;
            private LocalDateTime timestamp;

            public AsyncScanResponseBuilder taskId(String taskId) {
                this.taskId = taskId;
                return this;
            }

            public AsyncScanResponseBuilder repositoryUrl(String repositoryUrl) {
                this.repositoryUrl = repositoryUrl;
                return this;
            }

            public AsyncScanResponseBuilder status(String status) {
                this.status = status;
                return this;
            }

            public AsyncScanResponseBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public AsyncScanResponse build() {
                return new AsyncScanResponse(taskId, repositoryUrl, status, timestamp);
            }
        }
    }

    /**
     * Response DTO for health check operations.
     */
    @Schema(
        name = "HealthStatus",
        description = "Health status information for the service"
    )
    public static class HealthStatus {
        
        @Schema(
            description = "Current health status",
            example = "UP",
            allowableValues = {"UP", "DOWN", "DEGRADED"}
        )
        private String status;

        @Schema(
            description = "Timestamp of the health check",
            example = "2024-01-15T10:30:00"
        )
        private LocalDateTime timestamp;

        @Schema(
            description = "Additional health status message",
            example = "Git Scanner is running"
        )
        private String message;

        // Constructors
        public HealthStatus() {}

        public HealthStatus(String status, LocalDateTime timestamp, String message) {
            this.status = status;
            this.timestamp = timestamp;
            this.message = message;
        }

        public static HealthStatusBuilder builder() {
            return new HealthStatusBuilder();
        }

        // Getters and Setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        // Builder class
        public static class HealthStatusBuilder {
            private String status;
            private LocalDateTime timestamp;
            private String message;

            public HealthStatusBuilder status(String status) {
                this.status = status;
                return this;
            }

            public HealthStatusBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public HealthStatusBuilder message(String message) {
                this.message = message;
                return this;
            }

            public HealthStatus build() {
                return new HealthStatus(status, timestamp, message);
            }
        }
    }

    /**
     * Generic API response wrapper.
     */
    @Schema(
        name = "GitScannerApiResponse",
        description = "Generic API response wrapper containing success status, message, data, and timestamp"
    )
    public static class GitScannerApiResponse<T> {
        
        @Schema(
            description = "Indicates whether the operation was successful",
            example = "true"
        )
        private boolean success;

        @Schema(
            description = "Human-readable message describing the result",
            example = "Success"
        )
        private String message;

        @Schema(
            description = "Response data (type varies by endpoint)"
        )
        private T data;

        @Schema(
            description = "Timestamp when the response was generated",
            example = "2024-01-15T10:30:00"
        )
        private LocalDateTime timestamp;

        // Constructors
        public GitScannerApiResponse() {}

        public GitScannerApiResponse(boolean success, String message, T data, LocalDateTime timestamp) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.timestamp = timestamp;
        }

        public static <T> GitScannerApiResponseBuilder<T> builder() {
            return new GitScannerApiResponseBuilder<>();
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public T getData() { return data; }
        public void setData(T data) { this.data = data; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        // Builder class
        public static class GitScannerApiResponseBuilder<T> {
            private boolean success;
            private String message;
            private T data;
            private LocalDateTime timestamp;

            public GitScannerApiResponseBuilder<T> success(boolean success) {
                this.success = success;
                return this;
            }

            public GitScannerApiResponseBuilder<T> message(String message) {
                this.message = message;
                return this;
            }

            public GitScannerApiResponseBuilder<T> data(T data) {
                this.data = data;
                return this;
            }

            public GitScannerApiResponseBuilder<T> timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public GitScannerApiResponse<T> build() {
                return new GitScannerApiResponse<>(success, message, data, timestamp);
            }
        }
    }

    /**
     * Enum for supported Git platform types.
     */
    @Schema(
        name = "ToolType",
        description = "Supported Git platform types"
    )
    public enum ToolType {
        @Schema(description = "GitHub platform")
        GITHUB,
        
        @Schema(description = "GitLab platform")
        GITLAB,
        
        @Schema(description = "Bitbucket platform")
        BITBUCKET,
        
        @Schema(description = "Azure DevOps (Azure Repos) platform")
        AZUREREPO
    }
}