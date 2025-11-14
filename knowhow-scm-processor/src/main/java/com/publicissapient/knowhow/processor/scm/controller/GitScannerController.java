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

package com.publicissapient.knowhow.processor.scm.controller;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.executer.ScmProcessorScanExecutor;
import com.publicissapient.knowhow.processor.scm.service.core.GitScannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * REST controller for Git scanning operations.
 * 
 * Provides endpoints for:
 * - Triggering scmRepository scans
 * - Checking scan status
 * - Retrieving scan results
 */
@RestController
@RequestMapping("/api/scm")
@Tag(name = "Repository Scanning", description = "Operations for scanning Git repositories and collecting metadata")
@Slf4j
public class GitScannerController {

    private static final String SUCCESS_STATUS = "Success";

    private final GitScannerService gitScannerService;

    private final ScmProcessorScanExecutor scmProcessorScanExecutor;

    public GitScannerController(GitScannerService gitScannerService, ScmProcessorScanExecutor scmProcessorScanExecutor) {
        this.gitScannerService = gitScannerService;
        this.scmProcessorScanExecutor = scmProcessorScanExecutor;
    }

    /**
     * Triggers a synchronous scmRepository scan.
     * 
     * @param request the scan request
     * @return scan results
     */
    @PostMapping("/scan")
    @Operation(
        summary = "Scan Git Repository (Synchronous)",
        description = "Triggers a synchronous scan of a Git scmRepository to collect commits, merge requests, and user data. " +
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
    public ResponseEntity<GitScannerApiResponse<ScanResult>> scanRepository(
            @Parameter(description = "Repository scan request details", required = true)
            @Valid @RequestBody ScanRepositoryRequest request) {

        try {
            ScanRequest scanRequest = ScanRequest.builder()
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

            ScanResult result = gitScannerService.scanRepository(scanRequest);
            
            GitScannerApiResponse<ScanResult> response = GitScannerApiResponse.<ScanResult>builder()
                    .success(true)
                    .message(SUCCESS_STATUS)
                    .data(result)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error scanning scmRepository: {}", request.getRepositoryUrl(), e);
            
            GitScannerApiResponse<ScanResult> response = GitScannerApiResponse.<ScanResult>builder()
                    .success(false)
                    .message("Scan failed: " + e.getMessage())
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
                .message(SUCCESS_STATUS)
                .data(healthStatus)
                .timestamp(LocalDateTime.now())
                .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * Request DTO for scmRepository scanning operations.
     */
    @Setter
    @Getter
    @Schema(
        name = "ScanRepositoryRequest",
        description = "Request payload for scanning a Git scmRepository"
    )
    @AllArgsConstructor
    public static class ScanRepositoryRequest {

        // Getters and Setters
        @Schema(
            description = "The URL of the Git scmRepository to scan",
            example = "https://github.com/owner/scmRepository"
        )
        @NotBlank(message = "Repository URL is required")
        private String repositoryUrl;

        @Schema(
            description = "The name of the scmRepository (typically owner/repo format)",
            example = "owner/scmRepository"
        )
        @NotBlank(message = "Repository name is required")
        private String repositoryName;

        @Schema(
            description = "Access token for authenticating with the Git platform",
            example = "ghp_xxxxxxxxxxxxxxxxxxxx"
        )
        @NotBlank(message = "Access token is required")
        private String accessToken;

        @Schema(
            description = "Username for the Git platform account",
            example = "john.doe"
        )
        @NotBlank(message = "Username is required")
        private String username;

        @Schema(
            description = "The branch to scan (typically 'main' or 'master')",
            example = "main"
        )
        @NotBlank(message = "Branch is required")
        private String branch;

        @Schema(
            description = "Whether to enable local cloning for scanning (JGit strategy)",
            example = "true"
        )
        @NotNull(message = "Clone enabled flag is required")
        private Boolean isCloneEnabled;

        @Schema(
            description = "The type of Git platform",
            example = "GITHUB"
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

    }

    /**
     * Process SCM connection metadata by connection ID.
     *
     * @param connectionId the connection ID
     * @return processing result
     */
    @PostMapping(path = "/connection/sync-metadata", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Sync SCM Connection Metadata",
            description = "Processes SCM connection metadata for the specified connection ID. " +
                    "This will fetch and update repository information for the connection.",
            tags = {"Repository Scanning"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Metadata sync completed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GitScannerApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Successful Sync",
                                    value = """
                        {
                          "success": true,
                          "message": "Success",
                          "data": {
                            "connectionId": "507f1f77bcf86cd799439011",
                            "processed": true,
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
                    description = "Invalid connection ID format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GitScannerApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Invalid ID",
                                    value = """
                        {
                          "success": false,
                          "message": "Invalid connection ID format",
                          "data": null,
                          "timestamp": "2024-01-15T10:30:00"
                        }
                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Connection not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GitScannerApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Not Found",
                                    value = """
                        {
                          "success": false,
                          "message": "Connection not found",
                          "data": null,
                          "timestamp": "2024-01-15T10:30:00"
                        }
                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during processing",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GitScannerApiResponse.class)
                    )
            )
    })
	public ResponseEntity<GitScannerApiResponse<ScanResult>> syncConnectionMetadata(
			@Parameter(description = "The connection ID (MongoDB ObjectId format)", required = true, example = "507f1f77bcf86cd799439011") @RequestBody String connectionId) {

		try {
			// Validate ObjectId format
			if (!ObjectId.isValid(connectionId)) {
				GitScannerApiResponse<ScanResult> response = GitScannerApiResponse.<ScanResult>builder().success(false)
						.message("Invalid connection ID format").data(null).timestamp(LocalDateTime.now()).build();

				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}

			ObjectId objectId = new ObjectId(connectionId);
			ScanResult scanResult = scmProcessorScanExecutor.processScmConnectionMetaData(objectId);

			GitScannerApiResponse<ScanResult> response = GitScannerApiResponse.<ScanResult>builder()
					.success(scanResult.isSuccess()).data(scanResult).timestamp(LocalDateTime.now()).build();

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("Error processing SCM connection metadata for connectionId: {}", connectionId, e);

			GitScannerApiResponse<ScanResult> response = GitScannerApiResponse.<ScanResult>builder().success(false)
					.message("Failed to process connection metadata: " + e.getMessage()).data(null)
					.timestamp(LocalDateTime.now()).build();

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}


    /**
     * Response DTO for health check operations.
     */
    @Setter
    @Getter
    @Schema(
        name = "HealthStatus",
        description = "Health status information for the service"
    )
    public static class HealthStatus {

        // Getters and Setters
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
    @Setter
    @Getter
    @Schema(
        name = "GitScannerApiResponse",
        description = "Generic API response wrapper containing success status, message, data, and timestamp"
    )
    public static class GitScannerApiResponse<T> {

        // Getters and Setters
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