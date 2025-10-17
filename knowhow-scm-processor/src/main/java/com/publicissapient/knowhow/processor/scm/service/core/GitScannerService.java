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

package com.publicissapient.knowhow.processor.scm.service.core;

import com.publicissapient.knowhow.processor.scm.service.core.command.ScanCommand;
import com.publicissapient.knowhow.processor.scm.service.core.command.ScanCommandExecutor;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Core service for scanning Git repositories and collecting metadata.
 *
 * This service orchestrates the scanning process using the Command pattern to
 * delegate complex operations to specialized command executors.
 *
 * Implements the Facade pattern to provide a simplified interface for the
 * complex scanning operations.
 */
@Service
@Slf4j
public class GitScannerService {

	private final ScanCommandExecutor scanCommandExecutor;

	@Autowired
	public GitScannerService(ScanCommandExecutor scanCommandExecutor) {
		this.scanCommandExecutor = scanCommandExecutor;
		log.info("GitScannerService initialized");
	}

	/**
	 * Scans a repository asynchronously and collects all metadata.
	 *
	 * @param scanRequest
	 *            the scan request containing repository information
	 * @return CompletableFuture with scan results
	 */
	@Async
	public CompletableFuture<ScanResult> scanRepositoryAsync(ScanRequest scanRequest) {
		log.info("Starting async scan for repository: {}", scanRequest.getRepositoryUrl());

		try {
			ScanResult result = scanRepository(scanRequest);
			log.info("Completed async scan for repository: {}", scanRequest.getRepositoryUrl());
			return CompletableFuture.completedFuture(result);
		} catch (Exception e) {
			log.error("Error during async scan for repository {}: {}", scanRequest.getRepositoryUrl(), e.getMessage(),
					e);
			return CompletableFuture.failedFuture(e);
		}
	}

	/**
	 * Scans a repository synchronously and collects all metadata.
	 *
	 * @param scanRequest
	 *            the scan request containing repository information
	 * @return scan results
	 * @throws DataProcessingException
	 *             if scanning fails
	 */
	public ScanResult scanRepository(ScanRequest scanRequest) throws DataProcessingException {
		log.info("Starting scan for repository: {} ({})", scanRequest.getRepositoryName(),
				scanRequest.getRepositoryUrl());

		ScanCommand scanCommand = new ScanCommand(scanRequest);
		return scanCommandExecutor.execute(scanCommand);
	}
}
