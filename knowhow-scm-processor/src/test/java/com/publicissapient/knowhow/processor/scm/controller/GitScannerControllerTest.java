package com.publicissapient.knowhow.processor.scm.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.publicissapient.knowhow.processor.scm.controller.GitScannerController.GitScannerApiResponse;
import com.publicissapient.knowhow.processor.scm.controller.GitScannerController.HealthStatus;
import com.publicissapient.knowhow.processor.scm.controller.GitScannerController.ScanRepositoryRequest;
import com.publicissapient.knowhow.processor.scm.controller.GitScannerController.ToolType;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.executer.ScmProcessorScanExecutor;
import com.publicissapient.knowhow.processor.scm.service.core.GitScannerService;

@RunWith(MockitoJUnitRunner.class)
public class GitScannerControllerTest {

	@Mock
	private GitScannerService gitScannerService;

	@Mock
	private ScmProcessorScanExecutor scmProcessorScanExecutor;

	private GitScannerController controller;

	@Before
	public void setUp() {
		controller = new GitScannerController(gitScannerService, scmProcessorScanExecutor);
	}

	@Test
	public void testScanRepository_Success() throws Exception {
		ScanRepositoryRequest request = createScanRequest();
		ScanResult scanResult = createScanResult(true);

		when(gitScannerService.scanRepository(any(ScanRequest.class))).thenReturn(scanResult);

		ResponseEntity<GitScannerApiResponse<ScanResult>> response = controller.scanRepository(request);

		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().isSuccess());
		assertEquals("Success", response.getBody().getMessage());
		assertNotNull(response.getBody().getData());
	}

	@Test
	public void testScanRepository_Failure() throws Exception {
		ScanRepositoryRequest request = createScanRequest();

		when(gitScannerService.scanRepository(any(ScanRequest.class)))
				.thenThrow(new RuntimeException("Scan failed"));

		ResponseEntity<GitScannerApiResponse<ScanResult>> response = controller.scanRepository(request);

		assertNotNull(response);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertFalse(response.getBody().isSuccess());
		assertTrue(response.getBody().getMessage().contains("Scan failed"));
	}

	@Test
	public void testHealth() {
		ResponseEntity<GitScannerApiResponse<HealthStatus>> response = controller.health();

		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().isSuccess());
		assertEquals("UP", response.getBody().getData().getStatus());
	}

	@Test
	public void testSyncConnectionMetadata_Success() throws Exception {
		String connectionId = new ObjectId().toString();
		ScanResult scanResult = createScanResult(true);

		when(scmProcessorScanExecutor.processScmConnectionMetaData(any(ObjectId.class))).thenReturn(scanResult);

		ResponseEntity<GitScannerApiResponse<ScanResult>> response = controller.syncConnectionMetadata(connectionId);

		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().isSuccess());
	}

	@Test
	public void testSyncConnectionMetadata_InvalidId() {
		String invalidId = "invalid-id";

		ResponseEntity<GitScannerApiResponse<ScanResult>> response = controller.syncConnectionMetadata(invalidId);

		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertFalse(response.getBody().isSuccess());
		assertEquals("Invalid connection ID format", response.getBody().getMessage());
	}

	@Test
	public void testSyncConnectionMetadata_Exception() {
		String connectionId = new ObjectId().toString();

		when(scmProcessorScanExecutor.processScmConnectionMetaData(any(ObjectId.class)))
				.thenThrow(new RuntimeException("Processing failed"));

		ResponseEntity<GitScannerApiResponse<ScanResult>> response = controller.syncConnectionMetadata(connectionId);

		assertNotNull(response);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertFalse(response.getBody().isSuccess());
	}

	private ScanRepositoryRequest createScanRequest() {
		return new ScanRepositoryRequest("https://github.com/test/repo", "test-repo", "token123", "testuser", "main",
				true, ToolType.GITHUB, null, null);
	}

	private ScanResult createScanResult(boolean success) {
		return ScanResult.builder().success(success).repositoryUrl("https://github.com/test/repo")
				.repositoryName("test-repo").startTime(Instant.now().toEpochMilli())
				.endTime(Instant.now().toEpochMilli()).build();
	}

	@Test
	public void testSyncConnectionMetadata_EmptyId() {
		String emptyId = "";

		ResponseEntity<GitScannerApiResponse<ScanResult>> response = controller.syncConnectionMetadata(emptyId);

		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void testSyncConnectionMetadata_FailedScanResult() throws Exception {
		String connectionId = new ObjectId().toString();
		ScanResult scanResult = createScanResult(false);

		when(scmProcessorScanExecutor.processScmConnectionMetaData(any(ObjectId.class))).thenReturn(scanResult);

		ResponseEntity<GitScannerApiResponse<ScanResult>> response = controller.syncConnectionMetadata(connectionId);

		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertFalse(response.getBody().isSuccess());
	}
}
