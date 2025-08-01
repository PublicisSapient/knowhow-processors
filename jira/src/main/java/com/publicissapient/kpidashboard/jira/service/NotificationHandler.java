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
package com.publicissapient.kpidashboard.jira.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyValue;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.rbac.ProjectsAccess;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import com.publicissapient.kpidashboard.common.service.NotificationService;
import com.publicissapient.kpidashboard.jira.config.JiraProcessorConfig;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NotificationHandler {

	public static final String ROLE_PROJECT_ADMIN = "ROLE_PROJECT_ADMIN";
	public static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";
	private static final String NOTIFICATION_MSG = "Notification_Msg";
	private static final String NOTIFICATION_ERROR = "Notification_Error";
	@Autowired
	private JiraProcessorConfig jiraProcessorConfig;

	@Autowired
	private ProjectBasicConfigRepository projectBasicConfigRepository;
	@Autowired
	private UserInfoRepository userInfoRepository;
	@Autowired
	private NotificationService notificationService;

	/**
	 * send mail project admin/superadmin who had enabled notification preferences
	 *
	 * @param value
	 *          value
	 * @param allFailureExceptions
	 *          allFailureExceptions
	 * @param projectBasicConfigId
	 *          projectBasicConfigId
	 */
	public void sendEmailToProjectAdminAndSuperAdmin(String value, String allFailureExceptions,
			String projectBasicConfigId, String notificationSubjectKey, String mailTemplateKey) {
		List<String> emailAddresses = getEmailAddressBasedProjectIdAndRole(projectBasicConfigId);
		if (CollectionUtils.isNotEmpty(jiraProcessorConfig.getDomainNames())) {
			emailAddresses = emailAddresses.stream().filter(emailAddress -> {
				String domain = StringUtils.substringAfter(emailAddress, "@").trim();
				return jiraProcessorConfig.getDomainNames().contains(domain);
			}).collect(Collectors.toList());
		}
		Map<String, String> notificationSubjects = jiraProcessorConfig.getNotificationSubject();
		if (CollectionUtils.isNotEmpty(emailAddresses) && MapUtils.isNotEmpty(notificationSubjects)) {

			Map<String, String> customData = new HashMap<>();
			customData.put(NOTIFICATION_MSG, value);
			customData.put(NOTIFICATION_ERROR, allFailureExceptions);
			String subject = notificationSubjects.get(notificationSubjectKey);
			log.info("Notification message sent with key : {}", mailTemplateKey);
			String templateKey = jiraProcessorConfig.getMailTemplate().getOrDefault(mailTemplateKey, "");
			notificationService.sendNotificationEvent(emailAddresses, customData, subject, jiraProcessorConfig.isNotificationSwitch(),
					templateKey);
		} else {
			log.error("Notification Event not sent : No email address found associated with Project-Admin role");
		}
	}

	/**
	 * find User List will all project admin who have access of that particular
	 * project and that hierarchy and superadmin user and which users had enabled
	 * notification alert
	 *
	 * @param projectConfigId
	 * @return
	 */
	private List<String> getEmailAddressBasedProjectIdAndRole(String projectConfigId) {
		Set<String> emailAddresses = new HashSet<>();
		List<UserInfo> usersList = userInfoRepository
				.findByAuthoritiesIn(Arrays.asList(ROLE_PROJECT_ADMIN, ROLE_SUPERADMIN));
		List<UserInfo> notificationEnableUsersList = usersList.stream()
				.filter(userInfo -> userInfo.getNotificationEmail() != null &&
						userInfo.getNotificationEmail().get(CommonConstant.ERROR_ALERT_NOTIFICATION))
				.collect(Collectors.toList());
		Map<String, String> projectMap = getHierarchyMap(projectConfigId);
		if (CollectionUtils.isNotEmpty(notificationEnableUsersList)) {
			notificationEnableUsersList.forEach(userInfo -> {
				// Case handel for SUPERADMIN
				if (CollectionUtils.isEmpty(userInfo.getProjectsAccess())) {
					emailAddresses.add(userInfo.getEmailAddress());
				}
				// case handel for ProjectAdmin
				Optional<ProjectsAccess> projectAccess = userInfo.getProjectsAccess().stream()
						.filter(access -> access.getRole().equalsIgnoreCase(ROLE_PROJECT_ADMIN)).findAny();
				if (projectAccess.isPresent()) {
					projectAccess.get().getAccessNodes().stream().forEach(accessNode -> {
						if (accessNode.getAccessItems().stream()
								.anyMatch(item -> item.getItemId().equalsIgnoreCase(projectMap.get(accessNode.getAccessLevel())))) {
							emailAddresses.add(userInfo.getEmailAddress());
						}
					});
				}
			});
		}

		return emailAddresses.stream().filter(StringUtils::isNotEmpty).collect(Collectors.toList());
	}

	private Map<String, String> getHierarchyMap(String projectConfigId) {
		Map<String, String> map = new HashMap<>();
		Optional<ProjectBasicConfig> basicConfig = projectBasicConfigRepository.findById(new ObjectId(projectConfigId));
		if (basicConfig.isPresent()) {
			ProjectBasicConfig projectBasicConfig = basicConfig.get();
			CollectionUtils.emptyIfNull(projectBasicConfig.getHierarchy()).stream()
					.sorted(
							Comparator.comparing((HierarchyValue hierarchyValue) -> hierarchyValue.getHierarchyLevel().getLevel()))
					.forEach(hierarchyValue -> map.put(hierarchyValue.getHierarchyLevel().getHierarchyLevelId(),
							hierarchyValue.getOrgHierarchyNodeId()));
			map.put(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, projectBasicConfig.getProjectNodeId());
		}

		return map;
	}
}
