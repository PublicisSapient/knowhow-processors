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

package com.publicissapient.knowhow.processor.scm.executer;

import com.publicissapient.knowhow.processor.scm.constants.ScmConstants;
import com.publicissapient.knowhow.processor.scm.domain.model.ScmProcessor;
import com.publicissapient.knowhow.processor.scm.domain.model.ScmProcessorItem;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.repository.ScmProcessorItemRepository;
import com.publicissapient.knowhow.processor.scm.service.core.GitScannerService;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.processortool.ProcessorToolConnection;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.generic.ProcessorRepository;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ScmProcessorScanExecutorTest {

	@Mock
	private ProcessorToolConnectionService processorToolConnectionService;

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
	private AesEncryptionService aesEncryptionService;

	@Mock
	private TaskScheduler taskScheduler;

	@Mock
	private RestTemplate restTemplate;

	private ScmProcessorScanExecutor scmProcessorScanExecutor;

	private ObjectId processorId;
	private ObjectId projectId;
	private ObjectId toolId;

	@BeforeEach
	void setUp() {
		processorId = new ObjectId();
		projectId = new ObjectId();
		toolId = new ObjectId();

		scmProcessorScanExecutor = new ScmProcessorScanExecutor(taskScheduler);

		// CHANGE: Manually inject all the mocked dependencies using ReflectionTestUtils
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "processorToolConnectionService",
				processorToolConnectionService);
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "projectConfigRepository", projectConfigRepository);
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "processorExecutionTraceLogService",
				processorExecutionTraceLogService);
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "scmProcessorItemRepository",
				scmProcessorItemRepository);
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "processorExecutionTraceLogRepository",
				processorExecutionTraceLogRepository);
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "gitScannerService", gitScannerService);
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "scmProcessorRepository", scmProcessorRepository);
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "aesEncryptionService", aesEncryptionService);

		// Set field values using reflection
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "cron", "0 0 * * * *");
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "aesEncryptionKey", "testKey");
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "customApiBaseUrl", "http://localhost:8080");
	}

	@Test
	public void testGetCron_ReturnsConfiguredCronValue() {
		// Act
		String result = scmProcessorScanExecutor.getCron();

		// Assert
		assertEquals("0 0 * * * *", result);
	}

	@Test
	public void testGetProcessor_ReturnsScmProcessorPrototype() {
		// Act
		ScmProcessor result = scmProcessorScanExecutor.getProcessor();

		// Assert
		assertNotNull(result);
		assertEquals(ProcessorConstants.SCM, result.getProcessorName());
	}

	@Test
	public void testGetProcessorRepository_ReturnsScmProcessorRepository() {
		// Act
		ProcessorRepository<ScmProcessor> result = scmProcessorScanExecutor.getProcessorRepository();

		// Assert
		assertEquals(scmProcessorRepository, result);
	}

	@Test
	public void testExecute_WithProjects_ProcessesSuccessfully() {
		// Arrange
		ScmProcessor processor = createScmProcessor();
		ProjectBasicConfig project = createProjectBasicConfig();
		ProcessorToolConnection tool = createProcessorToolConnection();
		ScmProcessorItem processorItem = createScmProcessorItem();
		ScanResult scanResult = ScanResult.builder().repositoryName("repoName").repositoryUrl("repoName.git")
				.success(true).startTime(LocalDateTime.now()).endTime(LocalDateTime.now()).durationMs(1000L)
				.commitsFound(10).mergeRequestsFound(5).usersFound(3).build();
		when(projectConfigRepository.findActiveProjects(false)).thenReturn(List.of(project));
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(anyString(), any(ObjectId.class)))
				.thenReturn(List.of(tool));
		when(scmProcessorItemRepository.findByProcessorIdAndToolConfigId(any(ObjectId.class), any(ObjectId.class)))
				.thenReturn(List.of(processorItem));
		when(gitScannerService.scanRepository(any(ScanRequest.class))).thenReturn(scanResult);
		when(aesEncryptionService.decrypt(anyString(), anyString())).thenReturn("decryptedToken");

		// Act
		boolean result = scmProcessorScanExecutor.execute(processor);

		// Assert
		assertTrue(result);
		verify(processorExecutionTraceLogService, times(1)).save(any(ProcessorExecutionTraceLog.class));
		verify(scmProcessorItemRepository, times(4)).save(any(ScmProcessorItem.class));
	}

	@Test
	public void testExecute_NoProjects_ReturnsTrue() {
		// Arrange
		ScmProcessor processor = createScmProcessor();
		when(projectConfigRepository.findActiveProjects(false)).thenReturn(Collections.emptyList());

		// Act
		boolean result = scmProcessorScanExecutor.execute(processor);

		// Assert
		assertTrue(result);
		verify(processorExecutionTraceLogService, never()).save(any());
	}

	@Test
	public void testExecute_ProjectWithNoToolConnections_SkipsProject() {
		// Arrange
		ScmProcessor processor = createScmProcessor();
		ProjectBasicConfig project = createProjectBasicConfig();

		when(projectConfigRepository.findActiveProjects(false)).thenReturn(List.of(project));
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(anyString(), any(ObjectId.class)))
				.thenReturn(Collections.emptyList());

		// Act
		boolean result = scmProcessorScanExecutor.execute(processor);

		// Assert
		assertTrue(result);
		verify(processorExecutionTraceLogService, never()).save(any());
	}

	@Test
	public void testExecute_MultipleToolsPerProject_ProcessesAllTools() {
		// Arrange
		ScmProcessor processor = createScmProcessor();
		ProjectBasicConfig project = createProjectBasicConfig();
		ProcessorToolConnection tool1 = createProcessorToolConnection();
		ProcessorToolConnection tool2 = createProcessorToolConnection();
		tool2.setId(new ObjectId());
		ScmProcessorItem processorItem = createScmProcessorItem();
		ScanResult scanResult = ScanResult.builder().repositoryName("repoName").repositoryUrl("repoName.git")
				.success(true).startTime(LocalDateTime.now()).endTime(LocalDateTime.now()).durationMs(1000L)
				.commitsFound(10).mergeRequestsFound(5).usersFound(3).build();

		when(projectConfigRepository.findActiveProjects(false)).thenReturn(List.of(project));
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(anyString(), any(ObjectId.class)))
				.thenReturn(Arrays.asList(tool1, tool2));
		when(scmProcessorItemRepository.findByProcessorIdAndToolConfigId(any(ObjectId.class), any(ObjectId.class)))
				.thenReturn(List.of(processorItem));
		when(gitScannerService.scanRepository(any(ScanRequest.class))).thenReturn(scanResult);
		when(aesEncryptionService.decrypt(anyString(), anyString())).thenReturn("decryptedToken");

		// Act
		boolean result = scmProcessorScanExecutor.execute(processor);

		// Assert
		assertTrue(result);
		verify(gitScannerService, times(8)).scanRepository(any(ScanRequest.class));
	}

	@Test
	public void testExecute_ToolProcessingFails_ContinuesWithOtherTools() {
		// Arrange
		ScmProcessor processor = createScmProcessor();
		ProjectBasicConfig project = createProjectBasicConfig();
		ProcessorToolConnection tool = createProcessorToolConnection();

		when(projectConfigRepository.findActiveProjects(false)).thenReturn(List.of(project));
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(anyString(), any(ObjectId.class)))
				.thenReturn(List.of(tool));
		doThrow(new RuntimeException("Processing failed")).when(processorToolConnectionService)
				.validateConnectionFlag(any());

		// Act
		boolean result = scmProcessorScanExecutor.execute(processor);

		// Assert
		assertTrue(result);
		verify(processorExecutionTraceLogService, times(1)).save(any(ProcessorExecutionTraceLog.class));
	}

	@Test
	public void testExecute_ClientException_UpdatesBreakingConnection() {
		// Arrange
		ScmProcessor processor = createScmProcessor();
		ProjectBasicConfig project = createProjectBasicConfig();
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setConnectionId(new ObjectId());

		HttpClientErrorException clientException = new HttpClientErrorException(HttpStatus.UNAUTHORIZED);

		// CHANGE: Set processorLabel to limit to one tool type
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "processorLabel", ProcessorConstants.GITHUB);

		when(projectConfigRepository.findActiveProjects(false)).thenReturn(List.of(project));
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(eq(ProcessorConstants.GITHUB),
				any(ObjectId.class))).thenReturn(List.of(tool));
		doThrow(new RuntimeException(clientException)).when(processorToolConnectionService)
				.validateConnectionFlag(any());

		// Act
		boolean result = scmProcessorScanExecutor.execute(processor);

		// Assert
		assertTrue(result);
		verify(processorToolConnectionService, times(1)).updateBreakingConnection(eq(tool.getConnectionId()),
				anyString());
	}

	@Test
	public void testExecuteSprint_AlwaysReturnsFalse() {
		// Act
		boolean result = scmProcessorScanExecutor.executeSprint("sprint123");

		// Assert
		assertFalse(result);
	}

	@Test
	public void testProcessToolConnection_SuccessfulScan_ReturnsTrue() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		ScmProcessor processor = createScmProcessor();
		ProjectBasicConfig project = createProjectBasicConfig();
		ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
		ScmProcessorItem processorItem = createScmProcessorItem();
		ScanResult scanResult = ScanResult.builder().repositoryName("repoName").repositoryUrl("repoName.git")
				.success(true).startTime(LocalDateTime.now()).endTime(LocalDateTime.now()).durationMs(1000L)
				.commitsFound(10).mergeRequestsFound(5).usersFound(3).build();

		when(scmProcessorItemRepository.findByProcessorIdAndToolConfigId(any(ObjectId.class), any(ObjectId.class)))
				.thenReturn(List.of(processorItem));
		when(gitScannerService.scanRepository(any(ScanRequest.class))).thenReturn(scanResult);
		when(aesEncryptionService.decrypt(anyString(), anyString())).thenReturn("decryptedToken");

		// Use reflection to test private method
		ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "processToolConnection", tool, processor, project,
				traceLog);

		// Assert
		verify(scmProcessorItemRepository).save(any(ScmProcessorItem.class));
	}

	@Test
	public void testCreateScanRequest_GitHubTool_CreatesProperRequest() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setToolName(ProcessorConstants.GITHUB);
		tool.setUsername("testuser");
		tool.setRepositoryName("testrepo");
		tool.setGitFullUrl(null);

		ScmProcessorItem processorItem = createScmProcessorItem();
		ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
		traceLog.setExecutionSuccess(true);
		ProjectBasicConfig project = createProjectBasicConfig();

		when(aesEncryptionService.decrypt(anyString(), anyString())).thenReturn("decryptedToken");

		// Act
		ScanRequest result = (ScanRequest) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"createScanRequest", tool, processorItem, traceLog, project);

		// Assert
		assertNotNull(result);
		assertEquals("testuser/testrepo", result.getRepositoryName());
		assertEquals("github", result.getToolType());
	}

	@Test
	public void testCreateScanRequest_BitbucketTool_CreatesProperRequest() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setToolName(ProcessorConstants.BITBUCKET);
		tool.setBitbucketProjKey("PROJ");
		tool.setRepoSlug("testrepo");
		tool.setGitFullUrl(null);

		ScmProcessorItem processorItem = createScmProcessorItem();
		ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
		traceLog.setExecutionSuccess(false);
		ProjectBasicConfig project = createProjectBasicConfig();

		when(aesEncryptionService.decrypt(anyString(), anyString())).thenReturn("decryptedToken");

		// Act
		ScanRequest result = (ScanRequest) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"createScanRequest", tool, processorItem, traceLog, project);

		// Assert
		assertNotNull(result);
		assertEquals("PROJ/testrepo", result.getRepositoryName());
		assertEquals("bitbucket", result.getToolType());
		assertEquals(0L, result.getLastScanFrom());
	}

	@Test
	public void testCreateScanRequest_GitLabWithFullUrl_UsesFullUrl() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setToolName(ProcessorConstants.GITLAB);
		tool.setGitFullUrl("https://gitlab.com/group/project.git");
		tool.setRepositoryName("project");

		ScmProcessorItem processorItem = createScmProcessorItem();
		ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
		ProjectBasicConfig project = createProjectBasicConfig();
		project.setDeveloperKpiEnabled(true);

		when(aesEncryptionService.decrypt(anyString(), anyString())).thenReturn("decryptedToken");

		// Act
		ScanRequest result = (ScanRequest) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"createScanRequest", tool, processorItem, traceLog, project);

		// Assert
		assertNotNull(result);
		assertEquals("https://gitlab.com/group/project.git", result.getRepositoryUrl());
		assertTrue(result.isCloneEnabled());
	}

	@Test
	public void testGetDecryptedToken_WithAccessToken_DecryptsToken() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setAccessToken("encryptedAccessToken");
		tool.setPassword(null);
		tool.setPat(null);

		when(aesEncryptionService.decrypt("encryptedAccessToken", "testKey")).thenReturn("decryptedToken");

		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "getDecryptedToken", tool);

		// Assert
		assertEquals("decryptedToken", result);
	}

	@Test
	public void testGetDecryptedToken_WithPassword_DecryptsPassword() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setAccessToken(null);
		tool.setPassword("encryptedPassword");
		tool.setPat(null);

		when(aesEncryptionService.decrypt("encryptedPassword", "testKey")).thenReturn("decryptedPassword");

		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "getDecryptedToken", tool);

		// Assert
		assertEquals("decryptedPassword", result);
	}

	@Test
	public void testGetDecryptedToken_WithPat_DecryptsPat() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setAccessToken(null);
		tool.setPassword(null);
		tool.setPat("encryptedPat");

		when(aesEncryptionService.decrypt("encryptedPat", "testKey")).thenReturn("decryptedPat");

		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "getDecryptedToken", tool);

		// Assert
		assertEquals("decryptedPat", result);
	}

	@Test
	public void testGetDecryptedToken_NoToken_ReturnsNull() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setAccessToken(null);
		tool.setPassword(null);
		tool.setPat(null);

		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "getDecryptedToken", tool);

		// Assert
		assertNull(result);
	}

	@Test
	public void testGetDecryptedToken_EmptyTokens_ReturnsNull() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setAccessToken("");
		tool.setPassword("");
		tool.setPat("");

		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "getDecryptedToken", tool);

		// Assert
		assertNull(result);
	}

	@Test
	public void testCacheRestClient_Success_LogsSuccess() {
		// Arrange
		ResponseEntity<String> responseEntity = new ResponseEntity<>("Success", HttpStatus.OK);

		// Mock RestTemplate creation
		ScmProcessorScanExecutor spyExecutor = spy(scmProcessorScanExecutor);
		RestTemplate mockRestTemplate = mock(RestTemplate.class);

		when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
				.thenReturn(responseEntity);

		// Use reflection to inject mock RestTemplate
		doReturn(mockRestTemplate).when(spyExecutor).getRestTemplate();

		// Act
		ReflectionTestUtils.invokeMethod(spyExecutor, "cacheRestClient");

		// Assert
		verify(mockRestTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
	}

	@Test
	public void testCacheRestClient_RestClientException_HandlesException() {
		// Arrange
		ScmProcessorScanExecutor spyExecutor = spy(scmProcessorScanExecutor);
		RestTemplate mockRestTemplate = mock(RestTemplate.class);

		when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
				.thenThrow(new RestClientException("Connection failed"));

		doReturn(mockRestTemplate).when(spyExecutor).getRestTemplate();

		// Act & Assert - Should not throw exception
		assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(spyExecutor, "cacheRestClient"));
	}

	@Test
	public void testGetSelectedProjects_WithSelectedIds_ReturnsFilteredProjects() {
		// Arrange
		ProjectBasicConfig project1 = createProjectBasicConfig();
		ProjectBasicConfig project2 = createProjectBasicConfig();
		project2.setId(new ObjectId());

		List<ProjectBasicConfig> allProjects = Arrays.asList(project1, project2);
		List<String> selectedIds = List.of(project1.getId().toHexString());

		when(projectConfigRepository.findActiveProjects(false)).thenReturn(allProjects);
		scmProcessorScanExecutor.setProjectsBasicConfigIds(selectedIds);

		// Act
		List<ProjectBasicConfig> result = ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"getSelectedProjects");

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(project1.getId(), result.get(0).getId());
	}

	@Test
	public void testCreateProcessorItem_CreatesItemWithCorrectDetails() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setUrl("https://github.com");
		tool.setBranch("main");
		tool.setToolName(ProcessorConstants.GITHUB);
		tool.setUsername("testuser");
		tool.setRepositoryName("testrepo");

		// Act
		ScmProcessorItem result = (ScmProcessorItem) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"createProcessorItem", tool, processorId);

		// Assert
		assertNotNull(result);
		assertEquals(toolId, result.getToolConfigId());
		assertEquals(processorId, result.getProcessorId());
		assertTrue(result.isActive());
		assertEquals("https://github.com", result.getToolDetailsMap().get(ScmConstants.URL));
		assertEquals("main", result.getToolDetailsMap().get(ScmConstants.TOOL_BRANCH));
		assertEquals(ProcessorConstants.GITHUB, result.getToolDetailsMap().get(ScmConstants.SCM));
		assertEquals("testuser", result.getToolDetailsMap().get(ScmConstants.OWNER));
		assertEquals("testrepo", result.getToolDetailsMap().get(ScmConstants.REPO_NAME));
	}

	@Test
	public void testGetScmProcessorItem_ExistingItem_ReturnsExisting() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		ScmProcessorItem existingItem = createScmProcessorItem();

		when(scmProcessorItemRepository.findByProcessorIdAndToolConfigId(processorId, toolId))
				.thenReturn(List.of(existingItem));

		// Act
		ScmProcessorItem result = (ScmProcessorItem) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"getScmProcessorItem", tool, processorId);

		// Assert
		assertEquals(existingItem, result);
		verify(scmProcessorItemRepository, never()).save(any());
	}

	@Test
	public void testGetScmProcessorItem_NoExistingItem_CreatesNew() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		ScmProcessorItem newItem = createScmProcessorItem();

		when(scmProcessorItemRepository.findByProcessorIdAndToolConfigId(processorId, toolId))
				.thenReturn(Collections.emptyList());
		when(scmProcessorItemRepository.save(any(ScmProcessorItem.class))).thenReturn(newItem);

		// Act
		ScmProcessorItem result = (ScmProcessorItem) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"getScmProcessorItem", tool, processorId);

		// Assert
		assertEquals(newItem, result);
		verify(scmProcessorItemRepository).save(any(ScmProcessorItem.class));
	}

	// @Test
	// public void testCreateTraceLog_WithExistingLog_UpdatesFields() {
	// // Arrange
	// ProcessorExecutionTraceLog existingLog = new ProcessorExecutionTraceLog();
	// existingLog.setLastEnableAssigneeToggleState(true);
	// existingLog.setExecutionEndedAt(1000L);
	// existingLog.setExecutionSuccess(true);
	//
	// when(processorExecutionTraceLogRepository.findByProcessorNameAndBasicProjectConfigId(
	// anyString(),
	// eq(projectId.toHexString()))).thenReturn(Optional.of(existingLog));
	//
	// // Act
	// ProcessorExecutionTraceLog result = (ProcessorExecutionTraceLog)
	// ReflectionTestUtils.invokeMethod(
	// scmProcessorScanExecutor, "createTraceLog", projectId.toHexString(),
	// ProcessorConstants.GITHUB);
	//
	// // Assert
	// assertNotNull(result);
	// assertEquals(ProcessorConstants.GITHUB, result.getProcessorName());
	// assertEquals(projectId.toHexString(), result.getBasicProjectConfigId());
	// assertTrue(result.isLastEnableAssigneeToggleState());
	// assertEquals(1000L, result.getExecutionEndedAt());
	// assertTrue(result.isExecutionSuccess());
	// }

	// Helper methods to create test objects
	private ScmProcessor createScmProcessor() {
		ScmProcessor processor = new ScmProcessor();
		processor.setId(processorId);
		processor.setProcessorName(ProcessorConstants.SCM);
		return processor;
	}

	private ProjectBasicConfig createProjectBasicConfig() {
		ProjectBasicConfig config = new ProjectBasicConfig();
		config.setId(projectId);
		config.setProjectName("Test Project");
		config.setSaveAssigneeDetails(true);
		config.setDeveloperKpiEnabled(false);
		return config;
	}

	private ProcessorToolConnection createProcessorToolConnection() {
		ProcessorToolConnection connection = new ProcessorToolConnection();
		connection.setId(toolId);
		connection.setToolName(ProcessorConstants.GITHUB);
		connection.setUrl("https://github.com/test/repo");
		connection.setBranch("main");
		connection.setUsername("testuser");
		connection.setRepositoryName("testrepo");
		connection.setAccessToken("encryptedToken");
		return connection;
	}

	private ScmProcessorItem createScmProcessorItem() {
		ScmProcessorItem item = new ScmProcessorItem();
		item.setId(new ObjectId());
		item.setToolConfigId(toolId);
		item.setProcessorId(processorId);
		item.setActive(true);
		item.setUpdatedTime(System.currentTimeMillis());
		return item;
	}

	@Test
	public void testGetToolConnections_WithProcessorLabel_ReturnsFilteredConnections() {
		// Arrange
		ProjectBasicConfig project = createProjectBasicConfig();
		ProcessorToolConnection tool = createProcessorToolConnection();

		// Set processor label using reflection
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "processorLabel", ProcessorConstants.GITHUB);

		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(ProcessorConstants.GITHUB, projectId))
				.thenReturn(List.of(tool));

		// Act
		List<ProcessorToolConnection> result = ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"getToolConnections", project);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(tool, result.get(0));
	}

	@Test
	public void testGetToolConnections_WithoutProcessorLabel_ReturnsAllScmTools() {
		// Arrange
		ProjectBasicConfig project = createProjectBasicConfig();
		ProcessorToolConnection githubTool = createProcessorToolConnection();
		ProcessorToolConnection bitbucketTool = createProcessorToolConnection();
		bitbucketTool.setToolName(ProcessorConstants.BITBUCKET);

		// Set processor label to null
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "processorLabel", null);

		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(ProcessorConstants.GITHUB, projectId))
				.thenReturn(List.of(githubTool));
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(ProcessorConstants.BITBUCKET, projectId))
				.thenReturn(List.of(bitbucketTool));
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(ProcessorConstants.GITLAB, projectId))
				.thenReturn(Collections.emptyList());
		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(ProcessorConstants.AZUREREPO, projectId))
				.thenReturn(Collections.emptyList());

		// Act
		List<ProcessorToolConnection> result = ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor,
				"getToolConnections", project);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
	}

	@Test
	public void testHandleToolProcessingException_WithHttpClientErrorException_UpdatesBreakingConnection() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setConnectionId(new ObjectId());

		HttpClientErrorException clientException = new HttpClientErrorException(HttpStatus.FORBIDDEN);
		Exception wrappedException = new RuntimeException(clientException);

		// Act
		ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "handleToolProcessingException", tool,
				wrappedException);

		// Assert
		verify(processorToolConnectionService).updateBreakingConnection(eq(tool.getConnectionId()), anyString());
	}

	@Test
	public void testHandleToolProcessingException_WithNonClientException_DoesNotUpdateConnection() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		Exception exception = new RuntimeException("Generic error");

		// Act
		ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "handleToolProcessingException", tool, exception);

		// Assert
		verify(processorToolConnectionService, never()).updateBreakingConnection(any(), anyString());
	}

	@Test
	public void testFinalizeTraceLog_UpdatesTraceLogAndClearsCache() {
		// Arrange
		ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
		ProjectBasicConfig project = createProjectBasicConfig();
		project.setSaveAssigneeDetails(true);

		// Mock RestTemplate for cache clearing
		ScmProcessorScanExecutor spyExecutor = spy(scmProcessorScanExecutor);
		doNothing().when(spyExecutor).clearToolItemCache(anyString());

		// Act
		ReflectionTestUtils.invokeMethod(spyExecutor, "finalizeTraceLog", traceLog, true, project);

		// Assert
		assertTrue(traceLog.isExecutionSuccess());
		assertTrue(traceLog.isLastEnableAssigneeToggleState());
		verify(processorExecutionTraceLogService).save(traceLog);
	}

	//@Test
	public void testSetupExecutionContext_SetsMDCValues() {
		// Act
		ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "setupExecutionContext");

		// Assert
		assertNotNull(MDC.get("GitHubProcessorJobExecutorUid"));
		assertNotNull(MDC.get("GitHubProcessorJobExecutorStartTime"));
	}

	@Test
	public void testClearSelectedBasicProjectConfigIds_SetsProjectIdsToNull() {
		// Arrange
		scmProcessorScanExecutor.setProjectsBasicConfigIds(Arrays.asList("id1", "id2"));

		// Act
		ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "clearSelectedBasicProjectConfigIds");

		// Assert
		assertNull(scmProcessorScanExecutor.getProjectsBasicConfigIds());
	}

	@Test
	public void testIsClientException_With4xxError_UpdatesBreakingConnection() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setConnectionId(new ObjectId());
		HttpClientErrorException clientException = new HttpClientErrorException(HttpStatus.NOT_FOUND);

		// Act
		ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "isClientException", tool, clientException);

		// Assert
		verify(processorToolConnectionService).updateBreakingConnection(eq(tool.getConnectionId()), anyString());
	}

	@Test
	public void testIsClientException_With5xxError_DoesNotUpdateConnection() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		HttpClientErrorException serverException = new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

		// Act
		ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "isClientException", tool, serverException);

		// Assert
		verify(processorToolConnectionService, never()).updateBreakingConnection(any(), anyString());
	}

	@Test
	public void testGetRepositoryName_AzureRepoTool_ReturnsRepositoryName() {
		// Arrange
		ProcessorToolConnection tool = createProcessorToolConnection();
		tool.setToolName(ProcessorConstants.AZUREREPO);
		tool.setRepositoryName("azurerepo");
		tool.setGitFullUrl("https://dev.azure.com/org/project/_git/repo");

		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "getRepositoryName", tool);

		// Assert
		assertEquals("azurerepo", result);
	}

	@Test
	public void testProcessProject_WithException_LogsErrorAndContinues() {
		// Arrange
		ScmProcessor processor = createScmProcessor();
		ProjectBasicConfig project = createProjectBasicConfig();
		ProcessorToolConnection tool = createProcessorToolConnection();

		// CHANGE: Set processorLabel to limit to one tool type to avoid multiple calls
		ReflectionTestUtils.setField(scmProcessorScanExecutor, "processorLabel", ProcessorConstants.GITHUB);

		when(processorToolConnectionService.findByToolAndBasicProjectConfigId(eq(ProcessorConstants.GITHUB),
				any(ObjectId.class))).thenReturn(List.of(tool));

		// CHANGE: Mock the repository to return empty Optional instead of throwing
		// exception
		// This simulates no existing trace log found
		when(processorExecutionTraceLogRepository.findByProcessorNameAndBasicProjectConfigId(any(), anyString()))
				.thenReturn(Optional.empty());

		// CHANGE: Mock validateConnectionFlag to throw the exception instead
		// This is where the exception should be thrown to test error handling
		doThrow(new RuntimeException("Database error")).when(processorToolConnectionService)
				.validateConnectionFlag(any());

		// Act & Assert - Should not throw exception
		assertDoesNotThrow(
				() -> ReflectionTestUtils.invokeMethod(scmProcessorScanExecutor, "processProject", project, processor));

		// CHANGE: Verify that the trace log was saved despite the exception
		verify(processorExecutionTraceLogService, times(1)).save(any(ProcessorExecutionTraceLog.class));
	}

	@Test
	public void testCacheRestClient_FailedResponse_LogsError() {
		// Arrange
		ResponseEntity<String> responseEntity = new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR);

		ScmProcessorScanExecutor spyExecutor = spy(scmProcessorScanExecutor);
		RestTemplate mockRestTemplate = mock(RestTemplate.class);

		when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
				.thenReturn(responseEntity);

		doReturn(mockRestTemplate).when(spyExecutor).getRestTemplate();
		doNothing().when(spyExecutor).clearToolItemCache(anyString());

		// Act
		ReflectionTestUtils.invokeMethod(spyExecutor, "cacheRestClient");

		// Assert
		verify(mockRestTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
	}

	@Test
	public void testCacheRestClient_NullResponse_LogsError() {
		// Arrange
		ScmProcessorScanExecutor spyExecutor = spy(scmProcessorScanExecutor);
		RestTemplate mockRestTemplate = mock(RestTemplate.class);

		when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
				.thenReturn(null);

		doReturn(mockRestTemplate).when(spyExecutor).getRestTemplate();
		doNothing().when(spyExecutor).clearToolItemCache(anyString());

		// Act
		ReflectionTestUtils.invokeMethod(spyExecutor, "cacheRestClient");

		// Assert
		verify(mockRestTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
	}

	// Helper method to support RestTemplate mocking
	private RestTemplate getRestTemplate() {
		return new RestTemplate();
	}
}
