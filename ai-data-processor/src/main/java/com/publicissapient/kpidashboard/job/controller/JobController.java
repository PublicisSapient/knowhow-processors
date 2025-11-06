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

package com.publicissapient.kpidashboard.job.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.job.dto.JobExecutionResponseRecord;
import com.publicissapient.kpidashboard.job.dto.JobResponseRecord;
import com.publicissapient.kpidashboard.job.orchestrator.JobOrchestrator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Management", description = "APIs for managing and controlling job execution, including running jobs and managing their enabled/disabled state")
public class JobController {

	private final JobOrchestrator jobOrchestrator;

	@Operation(summary = "Execute a job", description = "Triggers the execution of a specified job asynchronously. The job will be queued for execution and a response with execution details will be returned immediately.", operationId = "runJob")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Job execution request accepted and queued successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobExecutionResponseRecord.class), examples = @ExampleObject(name = "Successful job execution", value = """
					    {
					    "jobName": "data-sync-job",
					    "executionId": "exec-12345",
					    "jobId": "exec-job-id-12345",
					    "isRunning": true
					    }
					"""))),
			@ApiResponse(responseCode = "400", description = "Invalid job name or job configuration error", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Invalid job name", value = """
					{
					    "message": "Job name 'invalid-job' is not valid or does not exist"
					}
					"""))),
			@ApiResponse(responseCode = "404", description = "Job not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Job not found", value = """
					"message": "Job 'non-existent-job' not found"
					}
					"""))),
			@ApiResponse(responseCode = "409", description = "Job is already running or disabled", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Job already running", value = """
					{
					    "message": "Job 'data-sync-job' is already running"
					}
					"""))),
			@ApiResponse(responseCode = "500", description = "Internal server error during job execution", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Internal server error", value = """
					{
					    "message": "Failed to queue job for execution"
					}
					"""))) })
	@PostMapping("/{jobName}/run")
	public ResponseEntity<JobExecutionResponseRecord> runJob(
			@Parameter(name = "jobName", description = "The unique name/identifier of the job to execute. Must be a valid, existing job name.", required = true, example = "data-sync-job", schema = @Schema(type = "string", pattern = "^[a-zA-Z0-9-_]+$")) @PathVariable String jobName) {
		JobExecutionResponseRecord jobExecutionResponseRecord = jobOrchestrator.runJob(jobName);
		return ResponseEntity.accepted().body(jobExecutionResponseRecord);
	}

	@Operation(summary = "Enable a job", description = "Enables a previously disabled job, allowing it to be executed. An enabled job can be triggered manually or by scheduled triggers.", operationId = "enableJob")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Job enabled successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobResponseRecord.class), examples = @ExampleObject(name = "Job enabled successfully", value = """
					{
					    "jobName": "data-sync-job",
					    "status": "ENABLED",
					    "message": "Job enabled successfully",
					    "lastModified": "2024-01-15T10:30:00Z"
					}
					"""))),
			@ApiResponse(responseCode = "400", description = "Invalid job name or job is already enabled", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Job already enabled", value = """
					{
					    "message": "Job 'data-sync-job' is already enabled"
					}
					"""))),
			@ApiResponse(responseCode = "404", description = "Job not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Job not found", value = """
					{
					    "message": "Job 'non-existent-job' not found"
					}
					"""))),
			@ApiResponse(responseCode = "500", description = "Internal server error during job enable operation", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Internal server error", value = """
					{
					    "message": "Failed to enable job due to system error"
					}
					"""))) })
	@PutMapping("/{jobName}/enable")
	public ResponseEntity<JobResponseRecord> enableJob(
			@Parameter(name = "jobName", description = "The unique name/identifier of the job to disable. Must be a valid, existing job name.", required = true, example = "data-sync-job", schema = @Schema(type = "string", pattern = "^[a-zA-Z0-9-_]+$")) @PathVariable String jobName) {
		return ResponseEntity.ok(jobOrchestrator.enableJob(jobName));
	}

	@Operation(summary = "Disable a job", description = "Disables an active job, preventing it from being executed until re-enabled. Currently running instances will complete, but no new executions will be allowed.", operationId = "disableJob")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Job disabled successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobResponseRecord.class), examples = @ExampleObject(name = "Job disabled successfully", value = """
					{
					    "jobName": "data-sync-job",
					    "status": "DISABLED",
					    "message": "Job disabled successfully",
					    "lastModified": "2024-01-15T10:30:00Z"
					}
					"""))),
			@ApiResponse(responseCode = "400", description = "Invalid job name or job is already disabled", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Job already disabled", value = """
					{
					    "message": "Job 'data-sync-job' is already disabled"
					}
					"""))),
			@ApiResponse(responseCode = "404", description = "Job not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Job not found", value = """
					{
					    "message": "Job 'non-existent-job' not found"
					}
					"""))),
			@ApiResponse(responseCode = "409", description = "Job cannot be disabled while running", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Job currently running", value = """
					{
					    "message": "Cannot disable job 'data-sync-job' while it is currently running"
					}
					"""))),
			@ApiResponse(responseCode = "500", description = "Internal server error during job disable operation", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Internal server error", value = """
					{
					    "message": "Failed to disable job due to system error",
					}
					"""))) })
	@PutMapping("/{jobName}/disable")
	public ResponseEntity<JobResponseRecord> disableJob(
			@Parameter(name = "jobName", description = "The unique name/identifier of the job to disable. Must be a valid, existing job name.", required = true, example = "data-sync-job", schema = @Schema(type = "string", pattern = "^[a-zA-Z0-9-_]+$")) @PathVariable String jobName) {
		return ResponseEntity.ok(jobOrchestrator.disableJob(jobName));
	}
}
