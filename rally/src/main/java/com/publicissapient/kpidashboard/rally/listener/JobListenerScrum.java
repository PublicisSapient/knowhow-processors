/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.rally.listener;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.rally.cache.RallyProcessorCacheEvictor;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.service.NotificationHandler;
import com.publicissapient.kpidashboard.rally.service.OngoingExecutionsService;
import com.publicissapient.kpidashboard.rally.service.ProjectHierarchySyncService;
import com.publicissapient.kpidashboard.rally.service.RallyCommonService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.publicissapient.kpidashboard.rally.helper.RallyHelper.convertDateToCustomFormat;
import static com.publicissapient.kpidashboard.rally.util.RallyProcessorUtil.generateLogMessage;

/**
 * @author girpatha
 */
@Component
@Slf4j
@JobScope
public class JobListenerScrum implements JobExecutionListener {

	@Autowired
	private NotificationHandler handler;

	@Value("#{jobParameters['projectId']}")
	private String projectId;

	@Autowired
	private FieldMappingRepository fieldMappingRepository;

	@Autowired
	private RallyProcessorCacheEvictor rallyProcessorCacheEvictor;

	@Autowired
	private OngoingExecutionsService ongoingExecutionsService;


	@Autowired
	private ProjectBasicConfigRepository projectBasicConfigRepo;

	@Autowired
	private RallyCommonService rallyCommonService;


	@Autowired
	private ProjectHierarchySyncService projectHierarchySyncService;

	@Autowired
	private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepo;


	@Override
	public void beforeJob(JobExecution jobExecution) {
		// in future we can use this method to do something before job execution starts
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.batch.core.listener.JobExecutionListenerSupport#afterJob(
	 * org.springframework.batch.core.JobExecution)
	 */
	@Override
	public void afterJob(JobExecution jobExecution) {
		log.info("********in scrum JobExecution listener - finishing job *********");
		// Sync the sprint hierarchy
		projectHierarchySyncService.syncScrumSprintHierarchy(new ObjectId(projectId));
		rallyProcessorCacheEvictor.evictCache(CommonConstant.CACHE_CLEAR_ENDPOINT, CommonConstant.CACHE_ACCOUNT_HIERARCHY);
		rallyProcessorCacheEvictor.evictCache(CommonConstant.CACHE_CLEAR_ENDPOINT,
				CommonConstant.CACHE_ORGANIZATION_HIERARCHY);
		rallyProcessorCacheEvictor.evictCache(CommonConstant.CACHE_CLEAR_ENDPOINT, CommonConstant.CACHE_SPRINT_HIERARCHY);
		rallyProcessorCacheEvictor.evictCache(CommonConstant.CACHE_CLEAR_ENDPOINT, CommonConstant.CACHE_PROJECT_HIERARCHY);
		rallyProcessorCacheEvictor.evictCache(CommonConstant.CACHE_CLEAR_ENDPOINT, CommonConstant.CACHE_PROJECT_TOOL_CONFIG);
		rallyProcessorCacheEvictor.evictCache(CommonConstant.CACHE_CLEAR_ENDPOINT, CommonConstant.JIRA_KPI_CACHE);
		rallyProcessorCacheEvictor.evictCache(CommonConstant.CACHE_CLEAR_PROJECT_SOURCE_ENDPOINT, projectId,
				CommonConstant.JIRA_KPI);
		try {
			if (jobExecution.getStatus() == BatchStatus.FAILED) {
				log.error("job failed : {} for the project : {}", jobExecution.getJobInstance().getJobName(), projectId);
				Throwable stepFaliureException = null;
				for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
					if (stepExecution.getStatus() == BatchStatus.FAILED) {
						stepFaliureException = stepExecution.getFailureExceptions().get(0);
						break;
					}
				}
				setExecutionInfoInTraceLog(false, stepFaliureException);
				final String failureReasonMsg = generateLogMessage(stepFaliureException);
				sendNotification(failureReasonMsg, RallyConstants.ERROR_NOTIFICATION_SUBJECT_KEY,
						RallyConstants.ERROR_MAIL_TEMPLATE_KEY);
			} else {
				setExecutionInfoInTraceLog(true, null);
			}
		} catch (Exception e) {
			log.error("An Exception has occured in scrum jobListener", e);
		} finally {
			log.info("removing project with basicProjectConfigId {}", projectId);
			// Mark the execution as completed
			ongoingExecutionsService.markExecutionAsCompleted(projectId);
			log.info("removing client for basicProjectConfigId {}", projectId);
		}
	}

	private void sendNotification(String notificationMessage, String notificationSubjectKey, String mailTemplateKey)
			throws UnknownHostException {
		FieldMapping fieldMapping = fieldMappingRepository.findByProjectConfigId(projectId);
		ProjectBasicConfig projectBasicConfig = projectBasicConfigRepo.findByStringId(projectId).orElse(null);
		if (fieldMapping == null || (fieldMapping.getNotificationEnabler() && projectBasicConfig != null)) {
			handler.sendEmailToProjectAdminAndSuperAdmin(
					convertDateToCustomFormat(System.currentTimeMillis()) + " on " + rallyCommonService.getApiHost() + " for \"" +
							getProjectName(projectBasicConfig) + "\"",
					notificationMessage, projectId, notificationSubjectKey, mailTemplateKey);
		} else {
			log.info("Notification Switch is Off for the project : {}. So No mail is sent to project admin", projectId);
		}
	}

	private static String getProjectName(ProjectBasicConfig projectBasicConfig) {
		return projectBasicConfig == null ? "" : projectBasicConfig.getProjectName();
	}

	private void setExecutionInfoInTraceLog(boolean status, Throwable stepFailureException) {
		List<ProcessorExecutionTraceLog> procExecTraceLogs = processorExecutionTraceLogRepo
				.findByProcessorNameAndBasicProjectConfigIdIn(RallyConstants.RALLY, Collections.singletonList(projectId));
		if (CollectionUtils.isNotEmpty(procExecTraceLogs)) {
			for (ProcessorExecutionTraceLog processorExecutionTraceLog : procExecTraceLogs) {
				processorExecutionTraceLog.setExecutionEndedAt(System.currentTimeMillis());
				processorExecutionTraceLog.setExecutionSuccess(status);
				if (stepFailureException != null && processorExecutionTraceLog.isProgressStats()) {
					processorExecutionTraceLog.setErrorMessage(generateLogMessage(stepFailureException));
					processorExecutionTraceLog.setFailureLog(stepFailureException.getMessage());
				}
			}
			processorExecutionTraceLogRepo.saveAll(procExecTraceLogs);
		}
	}
	
	/**
	 * Getter for projectHierarchySyncService - added for testing purposes
	 * 
	 * @return the projectHierarchySyncService
	 */
	public ProjectHierarchySyncService getProjectHierarchySyncService() {
		return projectHierarchySyncService;
	}
}
