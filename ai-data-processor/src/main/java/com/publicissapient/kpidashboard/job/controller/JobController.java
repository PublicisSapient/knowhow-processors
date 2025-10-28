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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

	private final JobOrchestrator jobOrchestrator;

	@PostMapping("/{jobName}/run")
	public ResponseEntity<JobExecutionResponseRecord> runJob(@PathVariable String jobName) {
		JobExecutionResponseRecord jobExecutionResponseRecord = jobOrchestrator.runJob(jobName);
		return ResponseEntity.accepted().body(jobExecutionResponseRecord);
	}

	@PutMapping("/{jobName}/enable")
	public ResponseEntity<JobResponseRecord> enableJob(@PathVariable String jobName) {
		return ResponseEntity.ok(jobOrchestrator.enableJob(jobName));
	}

	@PutMapping("/{jobName}/disable")
	public ResponseEntity<JobResponseRecord> disableJob(@PathVariable String jobName) {
		return ResponseEntity.ok(jobOrchestrator.disableJob(jobName));
	}
}
