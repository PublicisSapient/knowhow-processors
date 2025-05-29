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
package com.publicissapient.kpidashboard.rally.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.publicissapient.kpidashboard.rally.helper.RallyHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.publicissapient.kpidashboard.common.exceptions.ClientErrorMessageEnum;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.ErrorDetail;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.Iteration;
import com.publicissapient.kpidashboard.rally.model.IterationResponse;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.QueryResult;
import com.publicissapient.kpidashboard.rally.model.RallyResponse;

import lombok.extern.slf4j.Slf4j;
/**
 * @author girpatha
 */
@Slf4j
@Component
public class RallyCommonService {

	private static final String RALLY_URL = "https://rally1.rallydev.com/slm/webservice/v2.0";
	private static final String API_KEY = "_8BogJQcTuGwVjEemJiAjV0z5SgR2UCSsSnBUu55Y5U";
	private static final String PROJECT_NAME = "Core Team";
	private static final int PAGE_SIZE = 200; // Number of artifacts per page
	private static final String ZSESSIONID = "ZSESSIONID";
	private static final String RALLY_ISSUE_REVISION_ENDPOINT = "/Revisions";

	@Autowired
	private RallyProcessorConfig rallyProcessorConfig;

	@Autowired
	private AesEncryptionService aesEncryptionService;
	@Autowired
	private ProcessorToolConnectionService processorToolConnectionService;
	@Autowired
	private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepository;

	@Autowired
	private RestTemplate restTemplate;

	/**
	 * @param projectConfig
	 *            projectConfig
	 * @param url
	 *            url
	 * @return String
	 * @throws IOException
	 *             IOException
	 */
	public String getDataFromClient(ProjectConfFieldMapping projectConfig, URL url)
			throws IOException {
		Optional<Connection> connectionOptional = projectConfig.getJira().getConnection();
		ObjectId projectConfigId = projectConfig.getBasicProjectConfigId();
		return getDataFromServer(url, connectionOptional, projectConfigId);
	}

	/**
	 * @param url
	 *            url
	 * @param connectionOptional
	 *            connectionOptional
	 * @return String
	 * @throws IOException
	 *             IOException
	 */
	public String getDataFromServer(URL url, Optional<Connection> connectionOptional, ObjectId projectConfigId)
			throws IOException {
		HttpURLConnection request = (HttpURLConnection) url.openConnection();

		String username = null;
		String password = null;

		if (connectionOptional.isPresent()) {
				username = connectionOptional.map(Connection::getUsername).orElse(null);
				password = decryptJiraPassword(connectionOptional.map(Connection::getPassword).orElse(null));
		}
		if (connectionOptional.isPresent() && connectionOptional.get().isBearerToken()) {
			String patOAuthToken = decryptJiraPassword(connectionOptional.get().getPatOAuthToken());
			request.setRequestProperty("Authorization", "Bearer " + patOAuthToken); // NOSONAR
		} else {
			request.setRequestProperty("Authorization", "Basic " + encodeCredentialsToBase64(username, password)); // NOSONAR
		}
		request.connect();
		// process the client error
		processClientError(connectionOptional, request, projectConfigId);
		StringBuilder sb = new StringBuilder();
		try (InputStream in = (InputStream) request.getContent();
				BufferedReader inReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			int cp;
			while ((cp = inReader.read()) != -1) {
				sb.append((char) cp);
			}
			request.disconnect();
		} catch (IOException ie) {
			log.error("Read exception when connecting to server {}", ie);
			String errorMessage = ie.getMessage();
			// Regular expression pattern to extract the status code
			Pattern pattern = Pattern.compile("\\b(\\d{3})\\b");
			Matcher matcher = pattern.matcher(errorMessage);
			isClientException(connectionOptional, matcher);
			request.disconnect();
		}
		return sb.toString();
	}

	/**
	 * Method to process client error and update the connection broken flag
	 *
	 * @param connectionOptional
	 *            connectionOptional
	 * @param request
	 *            request
	 * @throws IOException
	 *             throw IO Error
	 */
	private void processClientError(Optional<Connection> connectionOptional, HttpURLConnection request,
			ObjectId basicProjectConfigId) throws IOException {
		int responseCode = request.getResponseCode();
		if (responseCode >= 400 && responseCode < 500) {
			// Read error message from the server
			String errorMessage = readErrorStream(request.getErrorStream());
			if (responseCode == 404) {
				ErrorDetail errorDetail = new ErrorDetail(responseCode, request.getURL().toString(), errorMessage,
						determineImpactBasedOnUrl(request.getURL().toString()));
				Optional<ProcessorExecutionTraceLog> existingTraceLog = processorExecutionTraceLogRepository
						.findByProcessorNameAndBasicProjectConfigIdAndProgressStatsTrue(RallyConstants.RALLY,
								basicProjectConfigId.toString());
				existingTraceLog.ifPresent(traceLog -> {
					List<ErrorDetail> errorDetailList = Optional.ofNullable(traceLog.getErrorDetailList())
							.orElseGet(ArrayList::new);
					errorDetailList.add(errorDetail);
					traceLog.setErrorDetailList(errorDetailList);
					processorExecutionTraceLogRepository.save(traceLog);
				});
			}
			// flagging the connection flag w.r.t error code.
			connectionOptional.ifPresent(connection -> {
				String errMsg = ClientErrorMessageEnum.fromValue(responseCode).getReasonPhrase();
				processorToolConnectionService.updateBreakingConnection(connection.getId(), errMsg);
			});
		}
	}

	private String readErrorStream(InputStream errorStream) throws IOException {
		StringBuilder response = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
		}
		return response.toString();
	}

	private String determineImpactBasedOnUrl(String url) {
		if (url.contains("sprint")) {
			return "Sprint KPI's";
		} else if (url.contains("versions")) {
			return "Release KPI's";
		} else if (url.contains("epic")) {
			return "Epic KPI's";
		}
		return ""; // Default or unknown impact
	}

	/**
	 * @param connectionOptional
	 *            connectionOptional
	 * @param matcher
	 *            matcher
	 */
	private void isClientException(Optional<Connection> connectionOptional, Matcher matcher) {
		if (matcher.find()) {
			String statusCodeString = matcher.group(1);
			int statusCode = Integer.parseInt(statusCodeString);
			if (statusCode >= 400 && statusCode < 500 && connectionOptional.isPresent()) {
				String errMsg = ClientErrorMessageEnum.fromValue(statusCode).getReasonPhrase();
				processorToolConnectionService.updateBreakingConnection(connectionOptional.get().getId(), errMsg);
			}
		}
	}

	/**
	 * @param encryptedPassword
	 *            encryptedPassword
	 * @return String
	 */
	public String decryptJiraPassword(String encryptedPassword) {
		return aesEncryptionService.decrypt(encryptedPassword, rallyProcessorConfig.getAesEncryptionKey());
	}

	/**
	 * @param username
	 *            username
	 * @param password
	 *            password
	 * @return String
	 */
	public String encodeCredentialsToBase64(String username, String password) {
		String cred = username + ":" + password;
		return Base64.getEncoder().encodeToString(cred.getBytes());
	}

	/**
	 * @param projectConfig
	 *            projectConfig
	 * @param pageNumber
	 *            pageNumber
	 * @param deltaDate
	 *            deltaDate
	 * @return List of Issue
	 */
	public List<HierarchicalRequirement> fetchIssuesBasedOnJql(ProjectConfFieldMapping projectConfig, int pageNumber,
			String deltaDate) {
		String queryDate = DateUtil
				.dateTimeFormatter(DateUtil.stringToLocalDateTime(deltaDate, RallyConstants.QUERYDATEFORMAT)
						.minusDays(rallyProcessorConfig.getDaysToReduce()), RallyConstants.QUERYDATEFORMAT);
		RallyResponse rallyResponse = getRqlIssues(projectConfig, queryDate, pageNumber);
		return RallyHelper.getIssuesFromResult(rallyResponse);
	}
	
	/**
	 * @param projectConfig
	 *            projectConfig
	 * @param deltaDate
	 *            deltaDate
	 * @param pageStart
	 *            pageStart
	 * @return SearchResult
	 */
	public RallyResponse getRqlIssues(ProjectConfFieldMapping projectConfig, String deltaDate, int pageStart) {
		RallyResponse rallyResponse = null;
		try {
			List<HierarchicalRequirement> allArtifacts = getHierarchicalRequirements(pageStart);
			// Create a RallyResponse object and populate it with the combined results
			QueryResult queryResult = new QueryResult();
			queryResult.setResults(allArtifacts);
			queryResult.setTotalResultCount(allArtifacts.size());
			queryResult.setStartIndex(pageStart);
			queryResult.setPageSize(PAGE_SIZE);

			rallyResponse = new RallyResponse();
			rallyResponse.setQueryResult(queryResult);
			saveSearchDetailsInContext(rallyResponse, pageStart, null, StepSynchronizationManager.getContext());
		} catch (RestClientException e) {
			if (e.getStatusCode().isPresent() && e.getStatusCode().get() >= 400 && e.getStatusCode().get() < 500) {
				String errMsg = ClientErrorMessageEnum.fromValue(e.getStatusCode().get()).getReasonPhrase();
				processorToolConnectionService
						.updateBreakingConnection(projectConfig.getProjectToolConfig().getConnectionId(), errMsg);
			}
			throw e;
		}
		return rallyResponse;
	}
	
	/**
	 * Method to save the search details in context.
	 *
	 * @param rallyResponse
	 *            rallyResponse
	 * @param pageStart
	 *            pageStart
	 * @param boardId
	 *            boardId
	 * @param stepContext
	 *            stepContext
	 */
	public void saveSearchDetailsInContext(RallyResponse rallyResponse, int pageStart, String boardId,
			StepContext stepContext) {
		if (stepContext == null) {
			log.error("StepContext is null");
			return;
		}
		JobExecution jobExecution = stepContext.getStepExecution().getJobExecution();
		int total = rallyResponse.getQueryResult().getTotalResultCount();
		int processed = Math.min(pageStart + rallyProcessorConfig.getPageSize() - 1, total);

		// Saving Progress details in context
		jobExecution.getExecutionContext().putInt(RallyConstants.TOTAL_ISSUES, total);
		jobExecution.getExecutionContext().putInt(RallyConstants.PROCESSED_ISSUES, processed);
		jobExecution.getExecutionContext().putInt(RallyConstants.PAGE_START, pageStart);
		jobExecution.getExecutionContext().putString(RallyConstants.BOARD_ID, boardId);
	}

	/**
	 * * Gets api host
	 *
	 * @return apiHost
	 * @throws UnknownHostException
	 *             UnknownHostException
	 */
	public String getApiHost() throws UnknownHostException {

		StringBuilder urlPath = new StringBuilder();
		if (StringUtils.isNotEmpty(rallyProcessorConfig.getUiHost())) {
			urlPath.append("https").append(':').append(File.separator + File.separator)
					.append(rallyProcessorConfig.getUiHost().trim());
		} else {
			throw new UnknownHostException("Api host not found in properties.");
		}

		return urlPath.toString();
	}

	private Iteration fetchIterationDetails(String iterationUrl, HttpEntity<String> entity) {
		try {
			ResponseEntity<IterationResponse> response = restTemplate.exchange(iterationUrl, HttpMethod.GET, entity,
					IterationResponse.class);

			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				IterationResponse responseBody = response.getBody();
				if (responseBody.getIteration() != null) {
					Iteration iteration = responseBody.getIteration();
					log.info("Fetched Iteration: {}", iteration.getName());
					return iteration;
				}
			}
			log.warn("Iteration details not found in response for URL: {}", iterationUrl);
		} catch (RestClientException e) {
			log.error("Failed to fetch iteration details from URL: {}. Error: {}", iterationUrl, e.getMessage(), e);
		}
		// Return an empty Iteration object instead of null
		return new Iteration();
	}

	public void setRallyIssueHistory(HierarchicalRequirement hierarchicalRequirement, HttpEntity<String> entity) {
		String historyUrl = hierarchicalRequirement.getRevisionHistory().get("_ref") + RALLY_ISSUE_REVISION_ENDPOINT;
		ResponseEntity<RallyResponse> response = restTemplate.exchange(historyUrl, HttpMethod.GET, entity,
				RallyResponse.class);
		List<Pair<String, String>> historyDescription = response.getBody().getQueryResult().getResults().stream()
				.map(revision -> Pair.of(revision.getCreationDate(), revision.getDescription()))
				.filter(pair -> pair.getRight() != null).toList();
		
		Map<String, Object> revisionHistory = new HashMap<>();
		revisionHistory.put(RallyConstants.HIERARCHY_REVISION_HISTORY, historyDescription);
		hierarchicalRequirement.setAdditionalProperties(revisionHistory);
	}
	
	/**
	 * Fetches hierarchical requirements, defects, and tasks from Rally
	 *
	 * @param pageStart starting index for pagination
	 * @return List of HierarchicalRequirement objects including defects
	 */
	public List<HierarchicalRequirement> getHierarchicalRequirements(int pageStart) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(ZSESSIONID, API_KEY);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		// List of artifact types to query
		List<String> artifactTypes = Arrays.asList("hierarchicalrequirement", "defect", "task");

		// Fetch fields for each artifact type, including Defects for hierarchical requirements
		String fetchFields = "FormattedID,Name,Owner,PlanEstimate,ScheduleState,Iteration,CreationDate,LastUpdateDate,RevisionHistory";
		String hierarchicalRequirementFetchFields = fetchFields + ",Defects";
		List<HierarchicalRequirement> allArtifacts = new ArrayList<>();
		Map<String, Iteration> iterationMap = new HashMap<>();
		// Map to store defects by their reference for quick lookup
		Map<String, HierarchicalRequirement> defectMap = new HashMap<>();

		// First, query defects to build a map for quick lookup
		int start = pageStart;
		boolean hasMoreResults = true;
		while (hasMoreResults) {
			String url = String.format("%s/%s?query=(Project.Name = \"%s\")&fetch=%s&start=%d&pagesize=%d",
					RALLY_URL, "defect", PROJECT_NAME, fetchFields + ",Requirement", start, PAGE_SIZE);
			ResponseEntity<RallyResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity,
					RallyResponse.class);
			if (response.getStatusCode() == HttpStatus.OK) {
				RallyResponse responseBody = response.getBody();
				if (responseBody != null && responseBody.getQueryResult() != null) {
					List<HierarchicalRequirement> defects = responseBody.getQueryResult().getResults();
					if (defects != null && !defects.isEmpty()) {
						for (HierarchicalRequirement defect : defects) {
							// Process iteration for defect
							if (defect.getIteration() != null && defect.getIteration().getRef() != null) {
								if(iterationMap.containsKey(defect.getIteration().getRef())) {
									defect.setIteration(iterationMap.get(defect.getIteration().getRef()));
								} else {
									Iteration iteration = fetchIterationDetails(defect.getIteration().getRef(), entity);
									defect.setIteration(iteration);
									iterationMap.put(defect.getIteration().getRef(), iteration);
								}
							}
							// Process revision history for defect
							if (defect.getRevisionHistory() != null) {
								setRallyIssueHistory(defect, entity);
							}
							// Add to defect map for quick lookup
							defectMap.put(defect.getRef(), defect);
							// Also add to allArtifacts as they are part of the result
							allArtifacts.add(defect);
						}
						start += PAGE_SIZE; // Move to the next page
					} else {
						hasMoreResults = false;
					}
				} else {
					hasMoreResults = false; // No response body
				}
			} else {
				log.error("Failed to fetch defects: {}", response.getStatusCode());
				hasMoreResults = false; // Stop on error
			}
		}

		// Now query hierarchical requirements and tasks
		for (String artifactType : Arrays.asList("hierarchicalrequirement", "task")) {
			start = pageStart; // Reset start index for pagination
			hasMoreResults = true;
			// Use enhanced fetch fields for hierarchical requirements to get defects
			String currentFetchFields = "hierarchicalrequirement".equals(artifactType) ? hierarchicalRequirementFetchFields : fetchFields;

			while (hasMoreResults) {
				String url = String.format("%s/%s?query=(Project.Name = \"%s\")&fetch=%s&start=%d&pagesize=%d",
						RALLY_URL, artifactType, PROJECT_NAME, currentFetchFields, start, PAGE_SIZE);
				ResponseEntity<RallyResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity,
						RallyResponse.class);
				if (response.getStatusCode() == HttpStatus.OK) {
					RallyResponse responseBody = response.getBody();
					if (responseBody != null && responseBody.getQueryResult() != null) {
						List<HierarchicalRequirement> artifacts = responseBody.getQueryResult().getResults();
						if (artifacts != null && !artifacts.isEmpty()) {
							for (HierarchicalRequirement artifact : artifacts) {
								// Fetch full iteration details if it exists
								if (artifact.getIteration() != null && artifact.getIteration().getRef() != null) {
									if(iterationMap.containsKey(artifact.getIteration().getRef())) {
										artifact.setIteration(iterationMap.get(artifact.getIteration().getRef()));
									} else {
										Iteration iteration = fetchIterationDetails(artifact.getIteration().getRef(),
												entity);
										artifact.setIteration(iteration);
										iterationMap.put(artifact.getIteration().getRef(), iteration);
									}
								}
								if (artifact.getRevisionHistory() != null) {
									setRallyIssueHistory(artifact, entity);
								}

								// If this is a hierarchical requirement and it has defects, process them
								if ("hierarchicalrequirement".equals(artifactType) && artifact.getDefects() != null) {
									// The API returns defect references, we need to replace them with actual defect objects
									List<HierarchicalRequirement> processedDefects = new ArrayList<>();
									for (HierarchicalRequirement defectRef : artifact.getDefects()) {
										HierarchicalRequirement fullDefect = defectMap.get(defectRef.getRef());
										if (fullDefect != null) {
											processedDefects.add(fullDefect);
										} else {
											// If not in our map, fetch it directly
											fullDefect = fetchDefectDetails(defectRef.getRef(), entity);
											if (fullDefect != null) {
												processedDefects.add(fullDefect);
												defectMap.put(fullDefect.getRef(), fullDefect);
											}
										}
									}
									artifact.setDefects(processedDefects);
								}

								allArtifacts.add(artifact);
							}
							start += PAGE_SIZE; // Move to the next page
						} else {
							hasMoreResults = false;
						}
					} else {
						hasMoreResults = false; // No response body
					}
				} else {
					log.error("Failed to fetch data for {}: {}", artifactType, response.getStatusCode());
					hasMoreResults = false; // Stop on error
				}
			}
		}
		return allArtifacts;
	}

	/**
	 * Fetches detailed information about a defect from Rally
	 *
	 * @param defectRef Reference URL to the defect
	 * @param entity HTTP entity with authentication headers
	 * @return HierarchicalRequirement object with defect details
	 */
	private HierarchicalRequirement fetchDefectDetails(String defectRef, HttpEntity<String> entity) {
		try {
			String fetchFields = "FormattedID,Name,Owner,PlanEstimate,ScheduleState,Iteration,CreationDate,LastUpdateDate,RevisionHistory,Requirement";
			// Extract the defect ID from the reference URL if needed
			String defectId = defectRef;
			if (defectRef.contains("/")) {
				defectId = defectRef.substring(defectRef.lastIndexOf("/") + 1);
			}
			
			String url = String.format("%s/defect/%s?fetch=%s", RALLY_URL, defectId, fetchFields);
			ResponseEntity<RallyResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, RallyResponse.class);

			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				RallyResponse responseBody = response.getBody();
				if (responseBody != null && responseBody.getQueryResult() != null && 
						responseBody.getQueryResult().getResults() != null && 
						!responseBody.getQueryResult().getResults().isEmpty()) {
					
					HierarchicalRequirement defect = responseBody.getQueryResult().getResults().get(0);
					// Process iteration for defect if needed
					if (defect.getIteration() != null && defect.getIteration().getRef() != null) {
						Iteration iteration = fetchIterationDetails(defect.getIteration().getRef(), entity);
						defect.setIteration(iteration);
					}
					// Process revision history for defect if needed
					if (defect.getRevisionHistory() != null) {
						setRallyIssueHistory(defect, entity);
					}
					return defect;
				}
			}
			log.warn("Defect details not found in response for URL: {}", url);
		} catch (Exception e) {
			log.error("Failed to fetch defect details from URL: {}, Error: {}", defectRef, e.getMessage(), e);
		}
		return null;
	}
}
