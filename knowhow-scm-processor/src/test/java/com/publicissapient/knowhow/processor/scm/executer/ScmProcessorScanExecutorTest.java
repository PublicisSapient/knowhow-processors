package com.publicissapient.knowhow.processor.scm.executer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.knowhow.processor.scm.domain.model.ScmProcessor;
import com.publicissapient.knowhow.processor.scm.domain.model.ScmProcessorItem;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.repository.ScmProcessorItemRepository;
import com.publicissapient.knowhow.processor.scm.service.core.GitScannerService;
import com.publicissapient.knowhow.processor.scm.service.core.fetcher.RepositoryFetcher;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.model.processortool.ProcessorToolConnection;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.connection.ConnectionRepository;
import com.publicissapient.kpidashboard.common.repository.generic.ProcessorRepository;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;

@RunWith(MockitoJUnitRunner.class)
public class ScmProcessorScanExecutorTest {

	@Mock
	private ConnectionRepository connectionRepository;

	@Mock
	private RepositoryFetcher repositoryFetcher;

	@Mock
	private ProcessorToolConnectionService processorToolConnectionService;

	@Mock
	private AesEncryptionService aesEncryptionService;

	@Mock
	private TaskScheduler taskScheduler;

	@Mock
	private ProjectBasicConfigRepository projectConfigRepository;

	@Mock
	private ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Mock
	private ScmProcessorItemRepository scmProcessorItemRepository;

	@Mock
	private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepository;

	@Mock
	private GitScannerService gitScannerService;

	@Mock
	private ProcessorRepository<ScmProcessor> scmProcessorRepository;

	@Mock
	private RestTemplate restTemplate;

	private ScmProcessorScanExecutor executor;

	private ObjectId connectionId;
	private Connection connection;

	@Before
	public void setUp() {
		executor = new ScmProcessorScanExecutor(taskScheduler);
		ReflectionTestUtils.setField(executor, "connectionRepository", connectionRepository);
		ReflectionTestUtils.setField(executor, "repositoryFetcher", repositoryFetcher);
		ReflectionTestUtils.setField(executor, "processorToolConnectionService", processorToolConnectionService);
		ReflectionTestUtils.setField(executor, "aesEncryptionService", aesEncryptionService);
		ReflectionTestUtils.setField(executor, "projectConfigRepository", projectConfigRepository);
		ReflectionTestUtils.setField(executor, "processorExecutionTraceLogService", processorExecutionTraceLogService);
		ReflectionTestUtils.setField(executor, "scmProcessorItemRepository", scmProcessorItemRepository);
		ReflectionTestUtils.setField(executor, "processorExecutionTraceLogRepository",
				processorExecutionTraceLogRepository);
		ReflectionTestUtils.setField(executor, "gitScannerService", gitScannerService);
		ReflectionTestUtils.setField(executor, "scmProcessorRepository", scmProcessorRepository);
		ReflectionTestUtils.setField(executor, "aesEncryptionKey", "testKey");
		ReflectionTestUtils.setField(executor, "customApiBaseUrl", "http://localhost:8080");
		ReflectionTestUtils.setField(executor, "cron", "0 0 * * * *");

		connectionId = new ObjectId();
		connection = new Connection();
		connection.setId(connectionId);
		connection.setType("GitHub");
		connection.setBaseUrl("https://github.com");
		connection.setUsername("testuser");
		connection.setBrokenConnection(false);
	}

	@Test
	public void testProcessScmConnectionMetaData_WithConnectionId_Success() {
		connection.setAccessToken("encryptedToken");
		ScanResult expectedResult = ScanResult.builder().success(true).build();

		when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
		when(aesEncryptionService.decrypt(any(), any())).thenReturn("decryptedToken");
		when(repositoryFetcher.fetchRepositories(any())).thenReturn(expectedResult);

		ScanResult result = executor.processScmConnectionMetaData(connectionId);

		assertEquals(true, result.isSuccess());
	}

	@Test
	public void testProcessScmConnectionMetaData_WithConnectionId_NotFound() {
		when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

		ScanResult result = executor.processScmConnectionMetaData(connectionId);

		assertFalse(result.isSuccess());
	}

	@Test
	public void testProcessScmConnectionMetaData_WithConnection_BrokenConnection() {
		connection.setBrokenConnection(true);

		ScanResult result = ReflectionTestUtils.invokeMethod(executor, "processScmConnectionMetaData", connection);

		assertFalse(result.isSuccess());
	}

	@Test
	public void testProcessProject_NoToolConnections() {
		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(new ObjectId());

		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(any(), any()))
				.thenReturn(Collections.emptyList());

		ReflectionTestUtils.invokeMethod(executor, "processProject", projectConfig, null);
	}

	@Test
	public void testExecuteSprint() {
		assertFalse(executor.executeSprint("sprint123"));
	}

	@Test
	public void testGetCron() {
		assertEquals("0 0 * * * *", executor.getCron());
	}

	@Test
	public void testGetProcessor() {
		ScmProcessor processor = executor.getProcessor();
		assertNotNull(processor);
	}

	@Test
	public void testGetProcessorRepository() {
		assertEquals(scmProcessorRepository, executor.getProcessorRepository());
	}

	@Test
	public void testProcessProject_WithToolConnections() {
		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(new ObjectId());
		projectConfig.setSaveAssigneeDetails(true);

		ProcessorToolConnection tool = new ProcessorToolConnection();
		tool.setId(new ObjectId());
		tool.setToolName("GitHub");
		tool.setUrl("https://github.com");
		tool.setBranch("main");
		tool.setUsername("testuser");
		tool.setRepositoryName("testrepo");
		tool.setConnectionId(new ObjectId());
		tool.setAccessToken("token");

		ScmProcessor processor = ScmProcessor.prototype();
		processor.setId(new ObjectId());

		ScmProcessorItem processorItem = new ScmProcessorItem();
		processorItem.setId(new ObjectId());

		ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
		traceLog.setExecutionSuccess(false);

		ScanResult scanResult = ScanResult.builder().success(true).build();

		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(any(), any()))
				.thenReturn(Arrays.asList(tool));
		when(processorExecutionTraceLogRepository.findByProcessorNameAndBasicProjectConfigId(any(), any()))
				.thenReturn(Optional.of(traceLog));
		when(scmProcessorItemRepository.findByProcessorIdAndToolConfigId(any(), any()))
				.thenReturn(Arrays.asList(processorItem));
		when(aesEncryptionService.decrypt(any(), any())).thenReturn("decryptedToken");
		when(gitScannerService.scanRepository(any())).thenReturn(scanResult);
		when(scmProcessorItemRepository.save(any())).thenReturn(processorItem);

		ReflectionTestUtils.invokeMethod(executor, "processProject", projectConfig, processor);
	}

	@Test
	public void testProcessProject_WithException() {
		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(new ObjectId());

		ProcessorToolConnection tool = new ProcessorToolConnection();
		tool.setId(new ObjectId());
		tool.setToolName("GitHub");
		tool.setConnectionId(new ObjectId());

		ScmProcessor processor = ScmProcessor.prototype();
		processor.setId(new ObjectId());

		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(any(), any()))
				.thenReturn(Arrays.asList(tool));
		when(processorExecutionTraceLogRepository.findByProcessorNameAndBasicProjectConfigId(any(), any()))
				.thenReturn(Optional.empty());
		when(scmProcessorItemRepository.findByProcessorIdAndToolConfigId(any(), any()))
				.thenThrow(new RuntimeException("Test exception"));

		ReflectionTestUtils.invokeMethod(executor, "processProject", projectConfig, processor);
	}

	@Test
	public void testExecute() {
		ScmProcessor processor = ScmProcessor.prototype();
		processor.setId(new ObjectId());

		ProjectBasicConfig project = new ProjectBasicConfig();
		project.setId(new ObjectId());

		when(projectConfigRepository.findActiveProjects(false)).thenReturn(Arrays.asList(project));
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(any(), any()))
				.thenReturn(Collections.emptyList());

		boolean result = executor.execute(processor);

		assertTrue(result);
	}

	@Test
	public void testExecute_WithMultipleProjects() {
		ScmProcessor processor = ScmProcessor.prototype();
		processor.setId(new ObjectId());

		ProjectBasicConfig project1 = new ProjectBasicConfig();
		project1.setId(new ObjectId());
		ProjectBasicConfig project2 = new ProjectBasicConfig();
		project2.setId(new ObjectId());

		Connection conn = new Connection();
		conn.setId(new ObjectId());
		conn.setBrokenConnection(false);
		conn.setAccessToken("token");

		when(projectConfigRepository.findActiveProjects(false)).thenReturn(Arrays.asList(project1, project2));
		when(connectionRepository.findByTypeIn(any())).thenReturn(Arrays.asList(conn));
		when(aesEncryptionService.decrypt(any(), any())).thenReturn("decrypted");
		when(repositoryFetcher.fetchRepositories(any())).thenReturn(ScanResult.builder().success(true).build());
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(any(), any()))
				.thenReturn(Collections.emptyList());

		boolean result = executor.execute(processor);

		assertTrue(result);
	}
}
