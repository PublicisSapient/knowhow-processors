package com.publicissapient.knowhow.processor.scm.executer;

import com.publicissapient.knowhow.processor.scm.constants.ScmConstants;
import com.publicissapient.knowhow.processor.scm.domain.model.ScmProcessor;
import com.publicissapient.knowhow.processor.scm.domain.model.ScmProcessorItem;
import com.publicissapient.knowhow.processor.scm.repository.ScmProcessorItemRepository;
import com.publicissapient.knowhow.processor.scm.service.core.GitScannerService;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.exceptions.ClientErrorMessageEnum;
import com.publicissapient.kpidashboard.common.executor.ProcessorJobExecutor;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.processortool.ProcessorToolConnection;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.generic.ProcessorRepository;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * base class for Git scanning executors.
 *
 * This class provides the common structure and functionality for different
 * types of scanning executors (scheduled, on-demand, etc.).
 *
 * Implement the Template Method pattern where subclasses define specific
 * execution behavior while this class provides the common framework.
 */
@Component
public class ScmProcessorScanExecutor extends ProcessorJobExecutor<ScmProcessor> {
	static final Logger logger = LoggerFactory.getLogger(ScmProcessorScanExecutor.class);

	@Autowired
	private ProcessorToolConnectionService processorToolConnectionService;

	@Autowired
	private ProjectBasicConfigRepository projectConfigRepository;

	@Autowired
	private ProcessorExecutionTraceLogService processorExecutionTraceLogService;

	@Autowired
	private ScmProcessorItemRepository scmProcessorItemRepository;

	@Autowired
	private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepository;

	@Autowired
	private GitScannerService gitScannerService;

	@Autowired
	private ProcessorRepository<ScmProcessor> scmProcessorRepository;

	@Value("${aesEncryptionKey}")
	private String aesEncryptionKey;

	@Value("${customapi.baseurl}")
	private String customApiBaseUrl;

	@Autowired
	AesEncryptionService aesEncryptionService;

	@Value("${scm.cron}")
	private String cron;

	private static final List<String> SCM_TOOL_LIST = Arrays.asList(ProcessorConstants.BITBUCKET,
			ProcessorConstants.GITLAB, ProcessorConstants.GITHUB, ProcessorConstants.AZUREREPO);

	@Autowired
	protected ScmProcessorScanExecutor(TaskScheduler taskScheduler) {
		super(taskScheduler, ProcessorConstants.SCM);
	}

	@Override
	public String getCron() {
		return cron;
	}

	@Override
	public ScmProcessor getProcessor() {
		return ScmProcessor.prototype();
	}

	/**
	 * Gets the processor repository.
	 *
	 * @return the processor repository
	 */
	@Override
	public ProcessorRepository<ScmProcessor> getProcessorRepository() {
		return scmProcessorRepository;
	}

	/**
	 * Creates the processor item.
	 *
	 * @param tool
	 *            the tool
	 * @param processorId
	 *            the processor id
	 * @return the processor item
	 */
	private ScmProcessorItem createProcessorItem(ProcessorToolConnection tool, ObjectId processorId) {
		ScmProcessorItem item = new ScmProcessorItem();
		item.setToolConfigId(tool.getId());
		item.setProcessorId(processorId);
		item.setActive(Boolean.TRUE);
		item.getToolDetailsMap().put(ScmConstants.URL, tool.getUrl());
		item.getToolDetailsMap().put(ScmConstants.TOOL_BRANCH, tool.getBranch());
		item.getToolDetailsMap().put(ScmConstants.SCM, tool.getToolName());
		item.getToolDetailsMap().put(ScmConstants.OWNER, tool.getUsername());
		item.getToolDetailsMap().put(ScmConstants.REPO_NAME, tool.getRepositoryName());
		item.getToolDetailsMap().put(ScmConstants.REPO_BRANCH, tool.getBranch());
		return item;
	}

	@Override
	public boolean execute(ScmProcessor processor) {
		setupExecutionContext();

		List<ProjectBasicConfig> projectConfigList = getSelectedProjects();
		MDC.put("TotalSelectedProjectsForProcessing", String.valueOf(projectConfigList.size()));
		clearSelectedBasicProjectConfigIds();

		projectConfigList.forEach(project -> processProject(project, processor));

		return true;
	}

	private void setupExecutionContext() {
		String uid = UUID.randomUUID().toString();
		MDC.put("GitHubProcessorJobExecutorUid", uid);

		long gitHubProcessorStartTime = System.currentTimeMillis();
		MDC.put("GitHubProcessorJobExecutorStartTime", String.valueOf(gitHubProcessorStartTime));
	}

	private void processProject(ProjectBasicConfig proBasicConfig, ScmProcessor processor) {
		List<ProcessorToolConnection> toolConnections = getToolConnections(proBasicConfig);

		if (CollectionUtils.isEmpty(toolConnections)) {
			logger.debug("No tool connections found for project: {}", proBasicConfig.getId());
			return;
		}

		ProcessorExecutionTraceLog traceLog = createTraceLog(proBasicConfig.getId().toHexString(),
				toolConnections.get(0).getToolName());

		boolean overallExecutionStatus = true;

		for (ProcessorToolConnection tool : toolConnections) {
			boolean toolExecutionStatus = processToolConnection(tool, processor, proBasicConfig, traceLog);
			overallExecutionStatus = overallExecutionStatus && toolExecutionStatus;
		}

		finalizeTraceLog(traceLog, overallExecutionStatus, proBasicConfig);
	}

	private List<ProcessorToolConnection> getToolConnections(ProjectBasicConfig proBasicConfig) {
		if (getProcessorLabel() == null || getProcessorLabel().isEmpty()) {
			List<ProcessorToolConnection> allConnections = new ArrayList<>();
			SCM_TOOL_LIST.forEach(scmTool -> allConnections.addAll(
					processorToolConnectionService.findByToolAndBasicProjectConfigId(scmTool, proBasicConfig.getId())));
			return allConnections;
		} else {
			return processorToolConnectionService.findByToolAndBasicProjectConfigId(getProcessorLabel(),
					proBasicConfig.getId());
		}
	}

	private boolean processToolConnection(ProcessorToolConnection tool, ScmProcessor processor,
			ProjectBasicConfig proBasicConfig, ProcessorExecutionTraceLog traceLog) {
		try {
			processorToolConnectionService.validateConnectionFlag(tool);
			traceLog.setExecutionStartedAt(System.currentTimeMillis());

            ScmProcessorItem scmProcessorItem = getScmProcessorItem(tool, processor.getId());
			GitScannerService.ScanRequest scanRequest = createScanRequest(tool, scmProcessorItem, traceLog, proBasicConfig);

			GitScannerService.ScanResult scanResult = gitScannerService.scanRepository(scanRequest);

            if(scanResult.isSuccess()) {
            	scmProcessorItem.setUpdatedTime(System.currentTimeMillis());
            	scmProcessorItemRepository.save(scmProcessorItem);
            }

			logger.debug("Successfully processed tool: {} for project: {}", tool.getToolName(), proBasicConfig.getId());
			return scanResult.isSuccess();

		} catch (Exception exception) {
			handleToolProcessingException(tool, exception);
			return false;
		}
	}

	private GitScannerService.ScanRequest createScanRequest(ProcessorToolConnection tool, ScmProcessorItem scmProcessorItem, ProcessorExecutionTraceLog processorExecutionTraceLog,
			ProjectBasicConfig proBasicConfig) {

		String repositoryName = getRepositoryName(tool);
		String token = getDecryptedToken(tool);
        long lastScanFrom = 0L;
        if(processorExecutionTraceLog.isExecutionSuccess()) {
            lastScanFrom = scmProcessorItem.getUpdatedTime();
        }


		return GitScannerService.ScanRequest.builder().repositoryName(repositoryName)
				.repositoryUrl(tool.getGitFullUrl() != null ? tool.getGitFullUrl() : tool.getUrl())
				.toolConfigId(scmProcessorItem.getId()).branchName(tool.getBranch())
				.cloneEnabled(proBasicConfig.isDeveloperKpiEnabled()).toolType(tool.getToolName().toLowerCase())
				.username(tool.getUsername()).token(token).lastScanFrom(lastScanFrom).build();
	}

	private String getRepositoryName(ProcessorToolConnection tool) {
		String repositoryName = tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug();

		if (tool.getGitFullUrl() == null || tool.getGitFullUrl().isEmpty()) {
			if (ProcessorConstants.GITHUB.equalsIgnoreCase(tool.getToolName())) {
				repositoryName = tool.getUsername() + "/" + repositoryName;
			} else if (ProcessorConstants.BITBUCKET.equalsIgnoreCase(tool.getToolName())) {
				repositoryName = tool.getBitbucketProjKey() + "/" + repositoryName;
			}
		}
		return repositoryName;
	}

	private String getDecryptedToken(ProcessorToolConnection tool) {
		String encryptedToken = Optional.ofNullable(tool.getAccessToken()).filter(token -> !token.isEmpty())
				.orElse(Optional.ofNullable(tool.getPassword()).filter(password -> !password.isEmpty())
						.orElse(Optional.ofNullable(tool.getPat()).filter(pat -> !pat.isEmpty()).orElse(null)));

		if (encryptedToken == null) {
			logger.warn("No access token or password found for tool: {}", tool.getToolName());
			return null;
		}

		return aesEncryptionService.decrypt(encryptedToken, aesEncryptionKey);
	}

	private void handleToolProcessingException(ProcessorToolConnection tool, Exception exception) {
		Throwable cause = exception.getCause();
		isClientException(tool, cause);
		logger.error("Error in processing tool: {} with URL: {}", tool.getToolName(), tool.getUrl(), exception);
	}

	private void finalizeTraceLog(ProcessorExecutionTraceLog traceLog, boolean executionStatus,
			ProjectBasicConfig proBasicConfig) {
		traceLog.setExecutionSuccess(executionStatus);
		traceLog.setLastEnableAssigneeToggleState(proBasicConfig.isSaveAssigneeDetails());
		traceLog.setExecutionEndedAt(System.currentTimeMillis());
		processorExecutionTraceLogService.save(traceLog);
		cacheRestClient(CommonConstant.CACHE_CLEAR_ENDPOINT, CommonConstant.BITBUCKET_KPI_CACHE);
	}

	@Override
	public boolean executeSprint(String sprintId) {
		return false;
	}

	/**
	 * Return List of selected ProjectBasicConfig id if null then return all
	 * ProjectBasicConfig ids
	 *
	 * @return List of projects
	 */
	private List<ProjectBasicConfig> getSelectedProjects() {
		List<ProjectBasicConfig> allProjects = projectConfigRepository.findActiveProjects(false);
		MDC.put("TotalConfiguredProject", String.valueOf(CollectionUtils.emptyIfNull(allProjects).size()));

		List<String> selectedProjectsBasicIds = getProjectsBasicConfigIds();
		if (CollectionUtils.isEmpty(selectedProjectsBasicIds)) {
			return allProjects;
		}
		return CollectionUtils.emptyIfNull(allProjects).stream().filter(
				projectBasicConfig -> selectedProjectsBasicIds.contains(projectBasicConfig.getId().toHexString()))
				.toList();
	}

	private void isClientException(ProcessorToolConnection tool, Throwable cause) {
		if (cause instanceof HttpClientErrorException
				&& ((HttpClientErrorException) cause).getStatusCode().is4xxClientError()) {
			String errMsg = ClientErrorMessageEnum.fromValue(((HttpClientErrorException) cause).getStatusCode().value())
					.getReasonPhrase();
			processorToolConnectionService.updateBreakingConnection(tool.getConnectionId(), errMsg);
		}

	}

	/**
	 * Creates a new `ProcessorExecutionTraceLog` for the given project
	 * configuration ID. This method initializes a new `ProcessorExecutionTraceLog`
	 * object and sets its processor name and basic project configuration ID. If an
	 * existing trace log is found for the same processor name and project
	 * configuration ID, the method updates the `lastEnableAssigneeToggleState`
	 * field of the new trace log based on the existing one.
	 *
	 * @param basicProjectConfigId
	 *            The ID of the basic project configuration for which the trace log
	 *            is being created.
	 * @return A new or updated `ProcessorExecutionTraceLog` object.
	 */
	private ProcessorExecutionTraceLog createTraceLog(String basicProjectConfigId, String toolName) {
		ProcessorExecutionTraceLog processorExecutionTraceLog = new ProcessorExecutionTraceLog();
		processorExecutionTraceLog.setProcessorName(toolName);
		processorExecutionTraceLog.setBasicProjectConfigId(basicProjectConfigId);
		Optional<ProcessorExecutionTraceLog> existingTraceLogOptional = processorExecutionTraceLogRepository
				.findByProcessorNameAndBasicProjectConfigId(getProcessorLabel(), basicProjectConfigId);
		existingTraceLogOptional.ifPresent(existingProcessorExecutionTraceLog -> {
			processorExecutionTraceLog.setLastEnableAssigneeToggleState(
					existingProcessorExecutionTraceLog.isLastEnableAssigneeToggleState());
			processorExecutionTraceLog.setExecutionEndedAt(existingProcessorExecutionTraceLog.getExecutionEndedAt());
            processorExecutionTraceLog.setExecutionSuccess(existingProcessorExecutionTraceLog.isExecutionSuccess());
		});
		return processorExecutionTraceLog;
	}

	/**
	 * Retrieves or creates a `ScmProcessorItem` for the given tool and processor
	 * ID. This method first attempts to find an existing `ScmProcessorItem` that
	 * matches the provided processor ID and tool configuration ID. If no such item
	 * exists, it creates a new `ScmProcessorItem` using the provided tool and
	 * processor ID, and saves it to the repository.
	 *
	 * @param tool
	 *            The `ProcessorToolConnection` object containing tool configuration
	 *            details.
	 * @param processorId
	 *            The `ObjectId` representing the processor ID.
	 * @return The `ScmProcessorItem` associated with the given tool and processor
	 *         ID.
	 */
	private ScmProcessorItem getScmProcessorItem(ProcessorToolConnection tool, ObjectId processorId) {
		List<ScmProcessorItem> scmProcessorItems = scmProcessorItemRepository
				.findByProcessorIdAndToolConfigId(processorId, tool.getId());
		ScmProcessorItem scmProcessorItem;
		if (CollectionUtils.isNotEmpty(scmProcessorItems)) {
			scmProcessorItem = scmProcessorItems.get(0);
		} else {
			scmProcessorItem = scmProcessorItemRepository.save(createProcessorItem(tool, processorId));
		}
		return scmProcessorItem;
	}

	private void clearSelectedBasicProjectConfigIds() {
		setProjectsBasicConfigIds(null);
	}

	/**
	 * Cleans the cache in the Custom API
	 *
	 * @param cacheEndPoint
	 *            the cache endpoint
	 * @param cacheName
	 *            the cache name
	 */
	private void cacheRestClient(String cacheEndPoint, String cacheName) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(customApiBaseUrl);
		uriBuilder.path("/");
		uriBuilder.path(cacheEndPoint);
		uriBuilder.path("/");
		uriBuilder.path(cacheName);

		HttpEntity<?> entity = new HttpEntity<>(headers);

		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = null;
		try {
			response = restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.GET, entity, String.class);
		} catch (RestClientException e) {
			logger.error("[BITBUCKET-CUSTOMAPI-CACHE-EVICT]. Error while consuming rest service {}", e);
		}

		if (null != response && response.getStatusCode().is2xxSuccessful()) {
			logger.info("[BITBUCKET-CUSTOMAPI-CACHE-EVICT]. Successfully evicted cache: {} ", cacheName);
		} else {
			logger.error("[BITBUCKET-CUSTOMAPI-CACHE-EVICT]. Error while evicting cache: {}", cacheName);
		}

		clearToolItemCache(customApiBaseUrl);
	}

}