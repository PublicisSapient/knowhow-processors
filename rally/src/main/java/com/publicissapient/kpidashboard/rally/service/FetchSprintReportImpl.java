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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.model.JiraIssueMetadata;
import com.publicissapient.kpidashboard.rally.model.RallyToolConfig;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.util.RallyProcessorUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.exceptions.ClientErrorMessageEnum;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.model.jira.BoardDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * @author girpatha
 */
@Slf4j
@Service
public class FetchSprintReportImpl implements FetchSprintReport {

	private static final String CONTENTS = "contents";
	private static final String COMPLETED_ISSUES = "completedIssues";
	private static final String PUNTED_ISSUES = "puntedIssues";
	private static final String COMPLETED_ISSUES_ANOTHER_SPRINT = "issuesCompletedInAnotherSprint";
	private static final String ADDED_ISSUES = "issueKeysAddedDuringSprint";
	private static final String NOT_COMPLETED_ISSUES = "issuesNotCompletedInCurrentSprint";
	private static final String KEY = "key";
	private static final String ENTITY_DATA = "entityData";
	private static final String PRIORITYID = "priorityId";
	private static final String STATUSID = "statusId";
	private static final String TYPEID = "typeId";
	private static final String ID = "id";
	private static final String STATE = "state";
	private static final String NAME = "name";
	private static final String STARTDATE = "startDate";
	private static final String ENDDATE = "endDate";
	private static final String COMPLETEDATE = "completeDate";
	private static final String ACTIVATEDDATE = "activatedDate";
	private static final String GOAL = "goal";
	@Autowired
	private RallyProcessorConfig rallyProcessorConfig;
	@Autowired
	private SprintRepository sprintRepository;
	@Autowired
	private RallyCommonService rallyCommonService;

	@Autowired
	private ProcessorToolConnectionService processorToolConnectionService;

 private boolean shouldFetchReport(SprintDetails sprint, Map<String, SprintDetails> dbSprintDetailMap, boolean isSprintFetch) {
    SprintDetails dbSprintDetails = dbSprintDetailMap.get(sprint.getSprintID());
    if (dbSprintDetails == null) {
        log.info("sprint id {} not found in db.", sprint.getSprintID());
        return true;
    }

    sprint.setId(dbSprintDetails.getId());
    if (!dbSprintDetails.getOriginBoardId().containsAll(sprint.getOriginBoardId())) {
        sprint.getOriginBoardId().addAll(dbSprintDetails.getOriginBoardId());
        return true;
    } else if (sprint.getState().equalsIgnoreCase(SprintDetails.SPRINT_STATE_ACTIVE) ||
               !sprint.getState().equalsIgnoreCase(dbSprintDetails.getState())) {
        sprint.setOriginBoardId(dbSprintDetails.getOriginBoardId());
        return true;
    } else if (!sprint.getState().equalsIgnoreCase(dbSprintDetails.getState()) && isSprintFetch) {
        sprint.setState(dbSprintDetails.getState());
        sprint.setOriginBoardId(dbSprintDetails.getOriginBoardId());
        return true;
    } else {
        log.debug("Sprint not to be saved again : {}, status: {} ", sprint.getOriginalSprintId(), sprint.getState());
        return false;
    }
}

private void processSprint(SprintDetails sprint, ProjectConfFieldMapping projectConfig, ObjectId jiraProcessorId,
                           Map<String, SprintDetails> dbSprintDetailMap, Set<SprintDetails> sprintToSave) throws IOException {
    String boardId = sprint.getOriginBoardId().get(0);
    log.info("processing sprint with sprintId: {}, state: {} and boardId: {} ", sprint.getSprintID(), sprint.getState(), boardId);
    sprint.setProcessorId(jiraProcessorId);
    sprint.setBasicProjectConfigId(projectConfig.getBasicProjectConfigId());

    if (shouldFetchReport(sprint, dbSprintDetailMap, false)) {
        try {
            TimeUnit.MILLISECONDS.sleep(rallyProcessorConfig.getSubsequentApiCallDelayInMilli());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        getSprintReport(sprint, projectConfig, boardId, dbSprintDetailMap.get(sprint.getSprintID()));
        sprintToSave.add(sprint);
    }
}

@Override
public Set<SprintDetails> fetchSprints(ProjectConfFieldMapping projectConfig, Set<SprintDetails> sprintDetailsSet,
                                       boolean isSprintFetch, ObjectId jiraProcessorId) throws IOException {
    Set<SprintDetails> sprintToSave = new HashSet<>();
    if (CollectionUtils.isNotEmpty(sprintDetailsSet)) {
        List<String> sprintIds = sprintDetailsSet.stream().map(SprintDetails::getSprintID).toList();
        List<SprintDetails> dbSprints = sprintRepository.findBySprintIDIn(sprintIds);
        Map<String, SprintDetails> dbSprintDetailMap = dbSprints.stream()
                .collect(Collectors.toMap(SprintDetails::getSprintID, Function.identity()));

        for (SprintDetails sprint : sprintDetailsSet) {
            processSprint(sprint, projectConfig, jiraProcessorId, dbSprintDetailMap, sprintToSave);
        }
    }
    return sprintToSave;
}
    private void getSprintReport(SprintDetails sprint, ProjectConfFieldMapping projectConfig, String boardId,
                                 SprintDetails dbSprintDetails) throws IOException {
        if (sprint.getOriginalSprintId() != null && sprint.getOriginBoardId() != null &&
                sprint.getOriginBoardId().stream().anyMatch(id -> id != null && !id.isEmpty())) {
            // If there's at least one non-null and non-empty string in the list, the
            // condition is true.
            getSprintReport(projectConfig, sprint.getOriginalSprintId(), boardId, sprint, dbSprintDetails);
        }
    }

    private void getSprintReport(ProjectConfFieldMapping projectConfig, String sprintId, String boardId,
                                 SprintDetails sprint, SprintDetails dbSprintDetails) throws IOException {
        try {
            RallyToolConfig rallyToolConfig = projectConfig.getJira();
            if (null != rallyToolConfig) {
                URL url = getSprintReportUrl(projectConfig, sprintId, boardId);
                getReport(rallyCommonService.getDataFromClient(projectConfig, url), sprint, projectConfig,
                        dbSprintDetails, boardId);
            }
            log.info(String.format("Fetched Sprint Report for Sprint Id : %s , Board Id : %s", sprintId, boardId));
        } catch (RestClientException rce) {
            log.error("Client exception when loading sprint report for sprint :{} ", sprintId, rce);
            throw rce;
        } catch (MalformedURLException mfe) {
            log.error("Malformed url for loading sprint report for sprint :{} ", sprintId, mfe);
            throw mfe;
        }
    }

    private void getReport(String sprintReportObj, SprintDetails sprint, ProjectConfFieldMapping projectConfig,
                           SprintDetails dbSprintDetails, String boardId) {
        if (StringUtils.isNotBlank(sprintReportObj)) {
            JSONArray completedIssuesJson = new JSONArray();
            JSONArray notCompletedIssuesJson = new JSONArray();
            JSONArray puntedIssuesJson = new JSONArray();
            JSONArray completedIssuesAnotherSprintJson = new JSONArray();
            org.json.simple.JSONObject addedIssuesJson = new org.json.simple.JSONObject();
            org.json.simple.JSONObject entityDataJson = new org.json.simple.JSONObject();

            boolean otherBoardExist = findIfOtherBoardExist(sprint);
            Set<SprintIssue> completedIssues = initializeIssues(
                    null == dbSprintDetails ? new HashSet<>() : dbSprintDetails.getCompletedIssues(), boardId, otherBoardExist);
            Set<SprintIssue> notCompletedIssues = initializeIssues(
                    null == dbSprintDetails ? new HashSet<>() : dbSprintDetails.getNotCompletedIssues(), boardId,
                    otherBoardExist);
            Set<SprintIssue> puntedIssues = initializeIssues(
                    null == dbSprintDetails ? new HashSet<>() : dbSprintDetails.getPuntedIssues(), boardId, otherBoardExist);
            Set<SprintIssue> completedIssuesAnotherSprint = initializeIssues(
                    null == dbSprintDetails ? new HashSet<>() : dbSprintDetails.getCompletedIssuesAnotherSprint(), boardId,
                    otherBoardExist);
            Set<SprintIssue> totalIssues = initializeIssues(
                    null == dbSprintDetails ? new HashSet<>() : dbSprintDetails.getTotalIssues(), boardId, otherBoardExist);
            Set<String> addedIssues = initializeAddedIssues(
                    null == dbSprintDetails ? new HashSet<>() : dbSprintDetails.getAddedIssues(), totalIssues, puntedIssues,
                    otherBoardExist);
            try {
                org.json.simple.JSONObject obj = (org.json.simple.JSONObject) new JSONParser().parse(sprintReportObj);
                if (null != obj) {
                    org.json.simple.JSONObject contentObj = (org.json.simple.JSONObject) obj.get(CONTENTS);
                    completedIssuesJson = (JSONArray) contentObj.get(COMPLETED_ISSUES);
                    notCompletedIssuesJson = (JSONArray) contentObj.get(NOT_COMPLETED_ISSUES);
                    puntedIssuesJson = (JSONArray) contentObj.get(PUNTED_ISSUES);
                    completedIssuesAnotherSprintJson = (JSONArray) contentObj.get(COMPLETED_ISSUES_ANOTHER_SPRINT);
                    addedIssuesJson = (org.json.simple.JSONObject) contentObj.get(ADDED_ISSUES);
                    entityDataJson = (org.json.simple.JSONObject) contentObj.get(ENTITY_DATA);
                }

                populateMetaData(entityDataJson, projectConfig);

                setIssues(completedIssuesJson, completedIssues, totalIssues, projectConfig, boardId);

                setIssues(notCompletedIssuesJson, notCompletedIssues, totalIssues, projectConfig, boardId);

                setPuntedCompletedAnotherSprint(puntedIssuesJson, puntedIssues, projectConfig, boardId);

                setPuntedCompletedAnotherSprint(completedIssuesAnotherSprintJson, completedIssuesAnotherSprint, projectConfig,
                        boardId);

                addedIssues = setAddedIssues(addedIssuesJson, addedIssues);

                if (null != sprint) {
                    sprint.setCompletedIssues(completedIssues);
                    sprint.setNotCompletedIssues(notCompletedIssues);
                    sprint.setCompletedIssuesAnotherSprint(completedIssuesAnotherSprint);
                    sprint.setPuntedIssues(puntedIssues);
                    sprint.setAddedIssues(addedIssues);
                    sprint.setTotalIssues(totalIssues);
                }

            } catch (org.json.simple.parser.ParseException pe) {
                log.error("Parser exception when parsing statuses", pe);
            }
        }
    }

    private Set<String> setAddedIssues(org.json.simple.JSONObject addedIssuesJson, Set<String> addedIssues) {
        Set<String> keys = addedIssuesJson.keySet();
        if (CollectionUtils.isNotEmpty(keys)) {
            addedIssues.addAll(keys.stream().collect(Collectors.toSet()));
        }
        return addedIssues;
    }

    private void setPuntedCompletedAnotherSprint(JSONArray puntedIssuesJson, Set<SprintIssue> puntedIssues,
                                                 ProjectConfFieldMapping projectConfig, String boardId) {
        puntedIssuesJson.forEach(puntedObj -> {
            org.json.simple.JSONObject punObj = (org.json.simple.JSONObject) puntedObj;
            if (null != punObj) {
                SprintIssue issue = getSprintIssue(punObj, projectConfig, boardId);
                puntedIssues.remove(issue);
                puntedIssues.add(issue);
            }
        });
    }

    private boolean findIfOtherBoardExist(SprintDetails sprint) {
        boolean exist = false;
        if (null != sprint && sprint.getOriginBoardId().size() > 1) {
            exist = true;
        }
        return exist;
    }

    private Set<SprintIssue> initializeIssues(Set<SprintIssue> sprintIssues, String boardId, boolean otherBoardExist) {
        if (otherBoardExist) {
            return CollectionUtils.emptyIfNull(sprintIssues).stream()
                    .filter(issue -> null != issue.getOriginBoardId() && !issue.getOriginBoardId().equalsIgnoreCase(boardId))
                    .collect(Collectors.toSet());
        } else {
            return new HashSet<>();
        }
    }

    private Set<String> initializeAddedIssues(Set<String> addedIssue, Set<SprintIssue> totalIssues,
                                              Set<SprintIssue> puntedIssues, boolean otherBoardExist) {
        if (otherBoardExist) {
            if (null == addedIssue) {
                addedIssue = new HashSet<>();
            }
            Set<String> keySet = CollectionUtils.emptyIfNull(totalIssues).stream().map(issue -> issue.getNumber())
                    .collect(Collectors.toSet());
            keySet.addAll(CollectionUtils.emptyIfNull(puntedIssues).stream().map(issue -> issue.getNumber())
                    .collect(Collectors.toSet()));
            addedIssue.retainAll(keySet);
            return addedIssue;
        } else {
            return new HashSet<>();
        }
    }

    private void populateMetaData(org.json.simple.JSONObject entityDataJson, ProjectConfFieldMapping projectConfig) {
        JiraIssueMetadata jiraIssueMetadata = new JiraIssueMetadata();
        if (Objects.nonNull(entityDataJson)) {
            jiraIssueMetadata
                    .setIssueTypeMap(getMetaDataMap((org.json.simple.JSONObject) entityDataJson.get("types"), "typeName"));
            jiraIssueMetadata
                    .setStatusMap(getMetaDataMap((org.json.simple.JSONObject) entityDataJson.get("statuses"), "statusName"));
            jiraIssueMetadata.setPriorityMap(
                    getMetaDataMap((org.json.simple.JSONObject) entityDataJson.get("priorities"), "priorityName"));
            projectConfig.setJiraIssueMetadata(jiraIssueMetadata);
        }
    }

    private Map<String, String> getMetaDataMap(org.json.simple.JSONObject object, String fieldName) {
        Map<String, String> map = new HashMap<>();
        if (null != object) {
            object.keySet().forEach(key -> {
                org.json.simple.JSONObject innerObj = (org.json.simple.JSONObject) object.get(key);
                Object fieldObject = innerObj.get(fieldName);
                if (null != fieldObject) {
                    map.put(key.toString(), fieldObject.toString());
                }
            });
        }
        return map;
    }

    private void setIssues(JSONArray issuesJson, Set<SprintIssue> issues, Set<SprintIssue> totalIssues,
                           ProjectConfFieldMapping projectConfig, String boardId) {
        issuesJson.forEach(jsonObj -> {
            org.json.simple.JSONObject obj = (org.json.simple.JSONObject) jsonObj;
            if (null != obj) {
                SprintIssue issue = getSprintIssue(obj, projectConfig, boardId);
                issues.remove(issue);
                issues.add(issue);
                totalIssues.remove(issue);
                totalIssues.add(issue);
            }
        });
    }

    private SprintIssue getSprintIssue(org.json.simple.JSONObject obj, ProjectConfFieldMapping projectConfig,
                                       String boardId) {
        SprintIssue issue = new SprintIssue();
        issue.setNumber(obj.get(KEY).toString());
        issue.setOriginBoardId(boardId);
        Optional<Connection> connectionOptional = projectConfig.getJira().getConnection();
        boolean isCloudEnv = connectionOptional.map(Connection::isCloudEnv).orElse(false);
        if (isCloudEnv) {
            issue.setPriority(getOptionalString(obj, "priorityName"));
            issue.setStatus(getOptionalString(obj, "statusName"));
            issue.setTypeName(getOptionalString(obj, "typeName"));
        } else {
            issue.setPriority(getName(projectConfig, PRIORITYID, obj));
            issue.setStatus(getName(projectConfig, STATUSID, obj));
            issue.setTypeName(getName(projectConfig, TYPEID, obj));
        }
        setEstimateStatistics(issue, obj, projectConfig);
        setTimeTrackingStatistics(issue, obj);
        return issue;
    }

    private void setTimeTrackingStatistics(SprintIssue issue, org.json.simple.JSONObject obj) {
        Object timeEstimateFieldId = getStatisticsFieldId((org.json.simple.JSONObject) obj.get("trackingStatistic"),
                "statFieldId");
        if (null != timeEstimateFieldId) {
            Object timeTrackingObject = getStatistics((org.json.simple.JSONObject) obj.get("trackingStatistic"),
                    "statFieldValue", "value");
            issue.setRemainingEstimate(timeTrackingObject == null ? null : Double.valueOf(timeTrackingObject.toString()));
        }
    }

    private void setEstimateStatistics(SprintIssue issue, org.json.simple.JSONObject obj,
                                       ProjectConfFieldMapping projectConfig) {
        Object currentEstimateFieldId = getStatisticsFieldId(
                (org.json.simple.JSONObject) obj.get("currentEstimateStatistic"), "statFieldId");
        if (null != currentEstimateFieldId) {
            Object estimateObject = getStatistics((org.json.simple.JSONObject) obj.get("currentEstimateStatistic"),
                    "statFieldValue", "value");
            String storyPointCustomField = StringUtils
                    .defaultIfBlank(projectConfig.getFieldMapping().getJiraStoryPointsCustomField(), "");
            if (storyPointCustomField.equalsIgnoreCase(currentEstimateFieldId.toString())) {
                issue.setStoryPoints(estimateObject == null ? null : Double.valueOf(estimateObject.toString()));
            } else {
                issue.setOriginalEstimate(estimateObject == null ? null : Double.valueOf(estimateObject.toString()));
            }
        }
    }

    private Object getStatistics(org.json.simple.JSONObject object, String objectName, String fieldName) {
        Object resultObj = null;
        if (null != object) {
            org.json.simple.JSONObject innerObj = (org.json.simple.JSONObject) object.get(objectName);
            if (null != innerObj) {
                resultObj = innerObj.get(fieldName);
            }
        }
        return resultObj;
    }

    private Object getStatisticsFieldId(org.json.simple.JSONObject object, String fieldName) {
        Object resultObj = null;
        if (null != object) {
            resultObj = object.get(fieldName);
        }
        return resultObj;
    }

    private String getName(ProjectConfFieldMapping projectConfig, String entityDataKey,
                           org.json.simple.JSONObject jsonObject) {
        String name = null;
        Object obj = jsonObject.get(entityDataKey);
        if (null != obj) {
            JiraIssueMetadata metadata = projectConfig.getJiraIssueMetadata();
            switch (entityDataKey) {
                case PRIORITYID:
                    name = metadata.getPriorityMap().getOrDefault(obj.toString(), null);
                    break;
                case STATUSID:
                    name = metadata.getStatusMap().getOrDefault(obj.toString(), null);
                    break;
                case TYPEID:
                    name = metadata.getIssueTypeMap().getOrDefault(obj.toString(), null);
                    break;
                default:
                    break;
            }
        }
        return name;
    }

    private String getOptionalString(final org.json.simple.JSONObject jsonObject, final String attributeName) {
        final Object res = jsonObject.get(attributeName);
        if (res == null) {
            return null;
        }
        return res.toString();
    }

    private URL getSprintReportUrl(ProjectConfFieldMapping projectConfig, String sprintId, String boardId)
            throws MalformedURLException {

        Optional<Connection> connectionOptional = projectConfig.getJira().getConnection();
        boolean isCloudEnv = connectionOptional.map(Connection::isCloudEnv).orElse(false);
        String serverURL = rallyProcessorConfig.getJiraServerSprintReportApi();
        if (isCloudEnv) {
            serverURL = rallyProcessorConfig.getJiraCloudSprintReportApi();
        }
        serverURL = serverURL.replace("{rapidViewId}", boardId).replace("{sprintId}", sprintId);
        String baseUrl = connectionOptional.map(Connection::getBaseUrl).orElse("");
        return new URL(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + serverURL);
    }

    @Override
    public List<SprintDetails> createSprintDetailBasedOnBoard(ProjectConfFieldMapping projectConfig, BoardDetails boardDetails, ObjectId processorId) throws IOException {
        List<SprintDetails> sprintDetailsBasedOnBoard = new ArrayList<>();
        List<SprintDetails> sprintDetailsList = getSprints(projectConfig, boardDetails.getBoardId());
        if (CollectionUtils.isNotEmpty(sprintDetailsList)) {
            Set<SprintDetails> sprintDetailSet = limitSprint(sprintDetailsList);
            sprintDetailsBasedOnBoard.addAll(fetchSprints(projectConfig, sprintDetailSet, false, processorId));
        }
        return sprintDetailsBasedOnBoard;
    }

    private Set<SprintDetails> limitSprint(List<SprintDetails> sprintDetailsList) {
        Set<SprintDetails> sd = sprintDetailsList.stream()
                .filter(sprintDetails -> sprintDetails.getState().equalsIgnoreCase(SprintDetails.SPRINT_STATE_CLOSED))
                .sorted((sprint1, sprint2) -> sprint2.getStartDate().compareTo(sprint1.getStartDate()))
                .limit(rallyProcessorConfig.getSprintReportCountToBeFetched()).collect(Collectors.toSet());
        sd.addAll(sprintDetailsList.stream()
                .filter(sprintDetails -> !sprintDetails.getState().equalsIgnoreCase(SprintDetails.SPRINT_STATE_CLOSED))
                .collect(Collectors.toSet()));
        return sd;
    }

    @Override
    public List<SprintDetails> getSprints(ProjectConfFieldMapping projectConfig, String boardId) throws IOException {
        List<SprintDetails> sprintDetailsList = new ArrayList<>();
        try {
            processorToolConnectionService.validateJiraAzureConnFlag(projectConfig.getProjectToolConfig());
            RallyToolConfig rallyToolConfig = projectConfig.getJira();
            if (null != rallyToolConfig) {
                boolean isLast = false;
                int startIndex = 0;
                do {
                    URL url = getSprintUrl(projectConfig, boardId, startIndex);
                    String jsonResponse = rallyCommonService.getDataFromClient(projectConfig, url);
                    isLast = populateSprintDetailsList(jsonResponse, sprintDetailsList, projectConfig, boardId);
                    startIndex = sprintDetailsList.size();
                    TimeUnit.MILLISECONDS.sleep(rallyProcessorConfig.getSubsequentApiCallDelayInMilli());
                } while (!isLast);
            }
        } catch (RestClientException rce) {
            if (rce.getStatusCode().isPresent() && rce.getStatusCode().get() >= 400 && rce.getStatusCode().get() < 500) {
                String errMsg = ClientErrorMessageEnum.fromValue(rce.getStatusCode().get()).getReasonPhrase();
                processorToolConnectionService.updateBreakingConnection(projectConfig.getProjectToolConfig().getConnectionId(),
                        errMsg);
            }
            log.error("Client exception when fetching sprints for board", rce);
            throw rce;
        } catch (MalformedURLException mfe) {
            log.error("Malformed url for loading sprint sprints for board", mfe);
            throw mfe;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return sprintDetailsList;
    }

    private boolean populateSprintDetailsList(String sprintReportObj, List<SprintDetails> sprintDetailsSet,
                                              ProjectConfFieldMapping projectConfig, String boardId) {
        boolean isLast = true;
        if (StringUtils.isNotBlank(sprintReportObj)) {
            JSONArray valuesJson = new JSONArray();
            try {
                JSONObject obj = (JSONObject) new JSONParser().parse(sprintReportObj);
                if (null != obj) {
                    valuesJson = (JSONArray) obj.get("values");
                }
                setSprintDetails(valuesJson, sprintDetailsSet, projectConfig, boardId);
                isLast = Boolean.parseBoolean(Objects.requireNonNull(obj).get("isLast").toString());
            } catch (ParseException pe) {
                log.error("Parser exception when parsing statuses", pe);
            }
        }
        return isLast;
    }

    private void setSprintDetails(JSONArray valuesJson, List<SprintDetails> sprintDetailsSet,
                                  ProjectConfFieldMapping projectConfig, String boardId) {
        valuesJson.forEach(values -> {
            JSONObject sprintJson = (JSONObject) values;
            if (sprintJson != null) {
                SprintDetails sprintDetails = createSprintDetails(sprintJson, projectConfig, boardId);
                sprintDetailsSet.add(sprintDetails);
            }
        });
    }

    private SprintDetails createSprintDetails(JSONObject sprintJson, ProjectConfFieldMapping projectConfig, String boardId) {
        SprintDetails sprintDetails = new SprintDetails();
        sprintDetails.setSprintName(sprintJson.get(NAME).toString());
        sprintDetails.setOriginBoardId(List.of(boardId));
        sprintDetails.setOriginalSprintId(sprintJson.get(ID).toString());
        sprintDetails.setState(sprintJson.get(STATE).toString().toUpperCase());
        String sprintId = sprintDetails.getOriginalSprintId() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR +
                projectConfig.getProjectBasicConfig().getProjectNodeId();
        sprintDetails.setSprintID(sprintId);
        sprintDetails.setStartDate(parseDate(sprintJson.get(STARTDATE)));
        sprintDetails.setEndDate(parseDate(sprintJson.get(ENDDATE)));
        sprintDetails.setCompleteDate(parseDate(sprintJson.get(COMPLETEDATE)));
        sprintDetails.setActivatedDate(parseDate(sprintJson.get(ACTIVATEDDATE)));
        sprintDetails.setGoal(sprintJson.get(GOAL) == null ? null : sprintJson.get(GOAL).toString());
        return sprintDetails;
    }

    private String parseDate(Object dateObj) {
        return dateObj == null ? null : RallyProcessorUtil.getFormattedDateForSprintDetails(dateObj.toString());
    }

    private URL getSprintUrl(ProjectConfFieldMapping projectConfig, String boardId, int startIndex)
            throws MalformedURLException {

        Optional<Connection> connectionOptional = projectConfig.getJira().getConnection();
        String serverURL = rallyProcessorConfig.getJiraSprintByBoardUrlApi();
        serverURL = serverURL.replace("{startAtIndex}", String.valueOf(startIndex)).replace("{boardId}", boardId);
        String baseUrl = connectionOptional.map(Connection::getBaseUrl).orElse("");
        return new URL(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + serverURL);
    }
}
