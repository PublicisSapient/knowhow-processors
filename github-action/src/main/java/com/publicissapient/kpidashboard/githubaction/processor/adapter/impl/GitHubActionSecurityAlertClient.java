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

package com.publicissapient.kpidashboard.githubaction.processor.adapter.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.kpidashboard.common.model.application.SecurityAlert;
import com.publicissapient.kpidashboard.common.model.processortool.ProcessorToolConnection;
import com.publicissapient.kpidashboard.common.repository.application.SecurityAlertRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.githubaction.config.GitHubActionConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Fetches GitHub Advanced Security alerts (Dependabot SCA + Code Scanning SAST) and persists them
 * as SecurityAlert documents for the Security Vulnerability Lead Time KPI.
 */
@Component
@Slf4j
public class GitHubActionSecurityAlertClient {

	@Autowired private AesEncryptionService aesEncryptionService;
	@Autowired private GitHubActionConfig gitHubActionConfig;
	@Autowired private RestTemplate restTemplate;
	@Autowired private SecurityAlertRepository securityAlertRepository;

	public void fetchAndPersistAlerts(
			ProcessorToolConnection toolConnection, ObjectId basicProjectConfigId, ObjectId processorId) {

		String apiBase = StringUtils.removeEnd(toolConnection.getUrl(), "/");
		String owner = toolConnection.getUsername();
		String repo = toolConnection.getRepositoryName();

		if (StringUtils.isAnyEmpty(apiBase, owner, repo)) {
			log.warn("GitHubAction connection missing apiBase/owner/repo — skipping GHAS fetch");
			return;
		}

		String repoLabel = "https://github.com/" + owner + "/" + repo;
		String repoApiBase = apiBase + "/repos/" + owner + "/" + repo;
		String decryptedToken = decryptApiToken(toolConnection.getAccessToken());

		fetchAlerts(
				repoApiBase,
				"dependabot/alerts?state=fixed",
				"DEPENDABOT",
				decryptedToken,
				basicProjectConfigId,
				processorId,
				repoLabel);
		fetchAlerts(
				repoApiBase,
				"code-scanning/alerts?state=fixed",
				"CODE_SCANNING",
				decryptedToken,
				basicProjectConfigId,
				processorId,
				repoLabel);
		fetchAlerts(
				repoApiBase,
				"code-scanning/alerts?state=dismissed",
				"CODE_SCANNING",
				decryptedToken,
				basicProjectConfigId,
				processorId,
				repoLabel);
	}

	private void fetchAlerts(
			String repoApiBase,
			String endpoint,
			String source,
			String decryptedToken,
			ObjectId basicProjectConfigId,
			ObjectId processorId,
			String repoUrl) {

		String baseUrl = repoApiBase + "/" + endpoint + "&per_page=100&page=";
		int page = 1;
		List<SecurityAlert> toSave = new ArrayList<>();

		while (true) {
			String url = baseUrl + page;
			ResponseEntity<String> response;
			try {
				response = getResponse(decryptedToken, url);
			} catch (HttpClientErrorException e) {
				if (e.getStatusCode().is4xxClientError()) {
					log.warn(
							"GHAS not enabled for repo {} — skipping security alert fetch ({})",
							repoUrl,
							e.getStatusCode());
					return;
				}
				log.error("HTTP error fetching GHAS alerts from {}: {}", url, e.getMessage());
				return;
			} catch (Exception e) {
				log.error("Error fetching GHAS alerts from {}: {}", url, e.getMessage());
				return;
			}

			if (response == null || response.getBody() == null) break;

			JSONArray alerts = parseJsonArray(response.getBody());
			if (alerts == null || alerts.isEmpty()) break;

			for (Object obj : alerts) {
				JSONObject alert = (JSONObject) obj;
				SecurityAlert sec = parseAlert(alert, source, basicProjectConfigId, processorId, repoUrl);
				if (sec == null) continue;
				if (!securityAlertRepository.existsByBasicProjectConfigIdAndAlertIdAndSource(
						basicProjectConfigId, sec.getAlertId(), source)) {
					toSave.add(sec);
				}
			}
			page++;
		}

		if (!toSave.isEmpty()) {
			securityAlertRepository.saveAll(toSave);
			log.info("Saved {} {} alerts for repo {}", toSave.size(), source, repoUrl);
		}
	}

	private SecurityAlert parseAlert(
			JSONObject alert,
			String source,
			ObjectId basicProjectConfigId,
			ObjectId processorId,
			String repoUrl) {

		Object numberObj = alert.get("number");
		if (numberObj == null) return null;
		String alertId = numberObj.toString();

		String createdAtStr = getString(alert, "created_at");
		String fixedAtStr = getString(alert, "fixed_at");
		if (fixedAtStr == null || fixedAtStr.isEmpty()) return null;

		long detectedAt;
		long fixedAt;
		try {
			detectedAt = Instant.parse(createdAtStr).toEpochMilli();
			fixedAt = Instant.parse(fixedAtStr).toEpochMilli();
		} catch (Exception e) {
			log.warn("Could not parse timestamps for alert {}: {}", alertId, e.getMessage());
			return null;
		}

		String severity = extractSeverity(alert, source);
		if (severity == null) severity = "unknown";

		return SecurityAlert.builder()
				.basicProjectConfigId(basicProjectConfigId)
				.processorId(processorId)
				.alertId(alertId)
				.source(source)
				.severity(severity.toLowerCase())
				.detectedAt(detectedAt)
				.fixedAt(fixedAt)
				.repoUrl(repoUrl)
				.build();
	}

	private String extractSeverity(JSONObject alert, String source) {
		if ("DEPENDABOT".equals(source)) {
			JSONObject advisory = (JSONObject) alert.get("security_advisory");
			if (advisory != null) return getString(advisory, "severity");
		} else {
			JSONObject rule = (JSONObject) alert.get("rule");
			if (rule != null) {
				String secLevel = getString(rule, "security_severity_level");
				if (StringUtils.isNotEmpty(secLevel)) return secLevel;
				return getString(rule, "severity");
			}
		}
		return null;
	}

	private JSONArray parseJsonArray(String body) {
		try {
			JSONParser parser = new JSONParser();
			Object parsed = parser.parse(body);
			if (parsed instanceof JSONArray) return (JSONArray) parsed;
		} catch (ParseException e) {
			log.error("Failed to parse GHAS response JSON: {}", e.getMessage());
		}
		return null;
	}

	private String decryptApiToken(String apiToken) {
		return StringUtils.isNotEmpty(apiToken)
				? aesEncryptionService.decrypt(apiToken, gitHubActionConfig.getAesEncryptionKey())
				: "";
	}

	private ResponseEntity<String> getResponse(String apiToken, String url) {
		HttpHeaders headers = new HttpHeaders();
		if (StringUtils.isNotEmpty(apiToken)) {
			headers.set("Authorization", "token " + apiToken);
		}
		headers.set("Accept", "application/vnd.github+json");
		HttpEntity<HttpHeaders> entity = new HttpEntity<>(headers);
		return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
	}

	private String getString(JSONObject obj, String key) {
		Object val = obj.get(key);
		return val != null ? val.toString() : null;
	}
}
