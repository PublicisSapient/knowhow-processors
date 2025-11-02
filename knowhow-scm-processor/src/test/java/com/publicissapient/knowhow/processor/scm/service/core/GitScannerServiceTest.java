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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GitScannerServiceTest {

	@Mock
	private ScanCommandExecutor scanCommandExecutor;

	private GitScannerService gitScannerService;

	private ScanRequest scanRequest;
	private ScanResult scanResult;

	@BeforeEach
	void setUp() {
		gitScannerService = new GitScannerService(scanCommandExecutor);

		// Initialize test data
		scanRequest = ScanRequest.builder().repositoryUrl("https://github.com/test/repo.git").repositoryName("repo")
				.build();

		scanResult = ScanResult.builder().build();
		// Add any necessary fields to scanResult based on your implementation
	}

	@Test
	void testConstructor_Success() {
		// Arrange & Act
		GitScannerService service = new GitScannerService(scanCommandExecutor);

		// Assert
		assertNotNull(service);
		// Constructor logs initialization message - verified through manual inspection
	}

	@Test
	void testScanRepositoryAsync_Success() throws ExecutionException, InterruptedException {
		// Arrange
		when(scanCommandExecutor.execute(any(ScanCommand.class))).thenReturn(scanResult);

		// Act
		CompletableFuture<ScanResult> futureResult = gitScannerService.scanRepositoryAsync(scanRequest);

		// Assert
		assertNotNull(futureResult);
		ScanResult actualResult = futureResult.get();
		assertEquals(scanResult, actualResult);

		// Verify command executor was called
		ArgumentCaptor<ScanCommand> commandCaptor = ArgumentCaptor.forClass(ScanCommand.class);
		verify(scanCommandExecutor, times(1)).execute(commandCaptor.capture());

		ScanCommand capturedCommand = commandCaptor.getValue();
		assertNotNull(capturedCommand);
	}

	@Test
	void testScanRepositoryAsync_ExceptionThrown() {
		// Arrange
		DataProcessingException expectedException = new DataProcessingException("Scan failed");
		when(scanCommandExecutor.execute(any(ScanCommand.class))).thenThrow(expectedException);

		// Act
		CompletableFuture<ScanResult> futureResult = gitScannerService.scanRepositoryAsync(scanRequest);

		// Assert
		assertNotNull(futureResult);
		assertTrue(futureResult.isCompletedExceptionally());

		ExecutionException executionException = assertThrows(ExecutionException.class, futureResult::get);
		assertEquals(expectedException, executionException.getCause());

		// Verify command executor was called
		verify(scanCommandExecutor, times(1)).execute(any(ScanCommand.class));
	}

	@Test
	void testScanRepository_Success() throws DataProcessingException {
		// Arrange
		when(scanCommandExecutor.execute(any(ScanCommand.class))).thenReturn(scanResult);

		// Act
		ScanResult actualResult = gitScannerService.scanRepository(scanRequest);

		// Assert
		assertNotNull(actualResult);
		assertEquals(scanResult, actualResult);

		// Verify command executor was called with correct command
		ArgumentCaptor<ScanCommand> commandCaptor = ArgumentCaptor.forClass(ScanCommand.class);
		verify(scanCommandExecutor, times(1)).execute(commandCaptor.capture());

		ScanCommand capturedCommand = commandCaptor.getValue();
		assertNotNull(capturedCommand);
	}

	@Test
	void testScanRepository_NullRequest() {
		// Act & Assert
		assertThrows(NullPointerException.class, () -> gitScannerService.scanRepository(null));
	}

	@Test
	void testScanRepositoryAsync_NullRequest() {
		// Act & Assert
		assertThrows(NullPointerException.class, () -> {
			CompletableFuture<ScanResult> futureResult = gitScannerService.scanRepositoryAsync(null);
			futureResult.get();
		});
	}

	@Test
	void testScanRepository_DataProcessingException() throws DataProcessingException {
		// Arrange
		DataProcessingException expectedException = new DataProcessingException("Processing failed");
		when(scanCommandExecutor.execute(any(ScanCommand.class))).thenThrow(expectedException);

		// Act & Assert
		DataProcessingException actualException = assertThrows(DataProcessingException.class,
				() -> gitScannerService.scanRepository(scanRequest));

		assertEquals(expectedException, actualException);
		assertEquals("Processing failed", actualException.getMessage());

		// Verify command executor was called
		verify(scanCommandExecutor, times(1)).execute(any(ScanCommand.class));
	}
}
