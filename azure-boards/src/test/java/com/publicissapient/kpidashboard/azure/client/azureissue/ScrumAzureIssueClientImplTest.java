package com.publicissapient.kpidashboard.azure.client.azureissue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.BeanUtils;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.publicissapient.kpidashboard.azure.adapter.AzureAdapter;
import com.publicissapient.kpidashboard.azure.adapter.impl.async.ProcessorAzureRestClient;
import com.publicissapient.kpidashboard.azure.client.sprint.SprintClient;
import com.publicissapient.kpidashboard.azure.config.AzureProcessorConfig;
import com.publicissapient.kpidashboard.azure.model.AzureProcessor;
import com.publicissapient.kpidashboard.azure.model.AzureToolConfig;
import com.publicissapient.kpidashboard.azure.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.azure.repository.AzureProcessorRepository;
import com.publicissapient.kpidashboard.azure.util.AdditionalFilterHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.application.AccountHierarchy;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.azureboards.AzureBoardsWIModel;
import com.publicissapient.kpidashboard.common.model.azureboards.Fields;
import com.publicissapient.kpidashboard.common.model.azureboards.Value;
import com.publicissapient.kpidashboard.common.model.azureboards.iterations.AzureIterationsModel;
import com.publicissapient.kpidashboard.common.model.azureboards.updates.AzureUpdatesModel;
import com.publicissapient.kpidashboard.common.model.azureboards.wiql.AzureWiqlModel;
import com.publicissapient.kpidashboard.common.model.azureboards.wiql.WorkItem;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.application.AccountHierarchyRepository;
import com.publicissapient.kpidashboard.common.repository.jira.AssigneeDetailsRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.common.service.ProjectHierarchyService;

@ExtendWith(SpringExtension.class)
public class ScrumAzureIssueClientImplTest {

	List<ProjectBasicConfig> scrumProjectList = new ArrayList<>();

	ProjectConfFieldMapping projectConfFieldMapping = ProjectConfFieldMapping.builder().build();
	List<FieldMapping> fieldMappingList = new ArrayList<>();
	List<ProjectConfFieldMapping> projectConfFieldMappingList = new ArrayList<>();
	List<Value> issues = new ArrayList<>();

	List<com.publicissapient.kpidashboard.common.model.azureboards.updates.Value> valueList = new ArrayList<>();
	AccountHierarchy accountHierarchy;

	@InjectMocks
	JiraIssue jiraIssue;
	@Mock
	JiraIssueCustomHistory jiraIssueCustomHistory;
	@Mock
	JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Mock
	AzureProcessor azureProcessor;
	@Mock
	AzureWiqlModel azureWiqlModel;
	@Mock
	AzureIterationsModel azureIterationsModel;
	@Mock
	AzureBoardsWIModel azureBoardsWIModel;
	AzureUpdatesModel azureUpdatesModel;
	Fields field;
	com.publicissapient.kpidashboard.common.model.azureboards.updates.Fields fields;
	@Mock
	private JiraIssueRepository jiraIssueRepository;
	@Mock
	private AzureProcessorRepository azureProcessorRepository;
	@Mock
	private AccountHierarchyRepository accountHierarchyRepo;
	@Mock
	private AzureProcessorConfig azureProcessorConfig;
	@InjectMocks
	private ScrumAzureIssueClientImpl scrumIssueClientImpl;
	@Mock
	private AesEncryptionService aesEncryptionService;
	@Mock
	private ProcessorAzureRestClient processorAzureRestClient;
	@Mock
	private AzureAdapter azureAdapter;
	@Mock
	private SprintClient sprintClient;
	@Mock
	private AdditionalFilterHelper additionalFilterHelper;
	@Mock
	private HierarchyLevelService hierarchyLevelService;
	@Mock
	private ScrumHandleAzureIssueHistory scrumHandleAzureIssueHistory;
	@Mock
	private ProcessorExecutionTraceLogService processorExecutionTraceLogService;
	@Mock
	private AssigneeDetailsRepository assigneeDetailsRepository;
	@Mock
	private ProjectHierarchyService projectHierarchyService;
	@Mock
	private ProcessorToolConnectionService processorToolConnectionService;

	private ProjectBasicConfig projectConfig = new ProjectBasicConfig();
	private ProjectToolConfig projectToolConfig = new ProjectToolConfig();

	// AzureUpdatesModel azureUpdatesModel=new AzureUpdatesModel();

	@BeforeEach
	public void setUp() throws Exception {
		prepareProjectData();
		prepareProjectConfig();
		prepareFieldMapping();
		setProjectConfigFieldMap();
		jiraIssue = JiraIssue.builder().build();
		azureUpdatesModel = new AzureUpdatesModel();
		List<com.publicissapient.kpidashboard.common.model.azureboards.updates.Value> valueList = new ArrayList<>();
		com.publicissapient.kpidashboard.common.model.azureboards.updates.Value value1 = new com.publicissapient.kpidashboard.common.model.azureboards.updates.Value();
		value1.setId(2);
		value1.setRev(2);
		valueList.add(value1);
		fields = new com.publicissapient.kpidashboard.common.model.azureboards.updates.Fields();
		value1.setFields(fields);
		azureUpdatesModel.setValue(valueList);
	}

	@Test
	public void testProcessesAzureIssues() throws URISyntaxException, JSONException {

		when(jiraIssueRepository.findTopByBasicProjectConfigId(any())).thenReturn(null);
		when(jiraIssueRepository.saveAll(any())).thenReturn(null);
		when(jiraIssueCustomHistoryRepository.saveAll(any())).thenReturn(null);
		when(azureProcessorRepository.findByProcessorName(ProcessorConstants.AZURE))
				.thenReturn(azureProcessor);
		when(azureProcessor.getId()).thenReturn(new ObjectId("5e16c126e4b098db673cc372"));
		when(azureAdapter.getPageSize()).thenReturn(30);
		when(jiraIssueCustomHistoryRepository.findByStoryIDAndBasicProjectConfigId(
						anyString(), anyString()))
				.thenReturn(jiraIssueCustomHistory);
		when(azureAdapter.getIterationsModel(any())).thenReturn(azureIterationsModel);
		when(azureAdapter.getWiqlModel(any(), any(), any(), anyBoolean())).thenReturn(azureWiqlModel);
		when(azureProcessorConfig.getMinsToReduce()).thenReturn(30);
		when(azureProcessorConfig.getStartDate()).thenReturn("2019-01-07T00:00:00.0000000");
		when(assigneeDetailsRepository.findByBasicProjectConfigIdAndSource(any(), any()))
				.thenReturn(null);
		createIssue();

		WorkItem work = new WorkItem();
		work.setId(1);
		work.setUrl("https://testDomain.com/jira/");
		List<WorkItem> workItems = new ArrayList<>();
		workItems.add(work);
		Value value = new Value();
		value.setId(3);
		value.setFields(field);
		Set<SprintDetails> sprintDetailsSet = new LinkedHashSet<>();
		when(azureWiqlModel.getWorkItems()).thenReturn(workItems);
		when(processorAzureRestClient.getUpdatesResponse(any(), any())).thenReturn(azureUpdatesModel);
		when(accountHierarchyRepo.findByLabelNameAndBasicProjectConfigId(
						"Project", scrumProjectList.get(0).getId()))
				.thenReturn(Arrays.asList(accountHierarchy));

		when(azureAdapter.getUpdates(any(), anyString())).thenReturn(azureUpdatesModel);
		projectConfFieldMapping.setProjectKey("prkey");
		projectConfFieldMapping.setProjectName("prName");
		projectConfFieldMapping.setProjectBasicConfig(projectConfig);
		projectConfFieldMapping.setProjectToolConfig(projectToolConfig);

		scrumIssueClientImpl.processesAzureIssues(projectConfFieldMapping, "TestKey", azureAdapter);
		scrumIssueClientImpl.purgeAzureIssues(issues, projectConfFieldMapping);
		scrumIssueClientImpl.saveAzureIssueDetails(issues, projectConfFieldMapping, sprintDetailsSet);
	}

	private void prepareProjectData() {
		// Online Project Config data
		projectConfig.setId(new ObjectId("5b674d58f47cae8935b1b26f"));
		projectConfig.setProjectName("TestProject");
		projectConfig.setSaveAssigneeDetails(false);
		projectConfig.setIsKanban(false);
		scrumProjectList.add(projectConfig);
	}

	private void prepareProjectConfig() {
		AzureToolConfig config = new AzureToolConfig();
		Connection conn = new Connection();
		conn.setOffline(Boolean.TRUE);
		conn.setBaseUrl("https://test.com/testUser/testProject");
		config.setBasicProjectConfigId("5b674d58f47cae8935b1b26f");
		config.setConnection(conn);
		projectConfFieldMapping.setAzure(config);
	}

	private void prepareFieldMapping() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setBasicProjectConfigId(new ObjectId("5b674d58f47cae8935b1b26f"));
		fieldMapping.setSprintName("customfield_12700");
		List<String> jiraType = new ArrayList<>();
		jiraType.add("Defect");
		jiraType.add("story");
		fieldMapping.setJiradefecttype(jiraType);
		jiraType = new ArrayList<>(Arrays.asList(new String[]{"Story", "Defect", "Pre Story", "Feature", "Enabler Story"}));
		String[] jiraIssueType = new String[]{"Story", "Defect", "Pre Story", "Feature", "Enabler Story"};
		fieldMapping.setJiraIssueTypeNames(jiraIssueType);
		fieldMapping.setRootCause("customfield_19121");
		fieldMapping.setRootCauseIdentifier("Labels");
		jiraType = new ArrayList<>();
		jiraType.add("story");
		jiraType.add("defect");
		fieldMapping.setJiraDefectInjectionIssueTypeKPI14(jiraType);
		fieldMapping.setJiraTechDebtIssueType(jiraType);
		fieldMapping.setJiraIssueTypeKPI35(jiraType);
		fieldMapping.setJiraDefectRemovalStatusKPI34(jiraType);
		fieldMapping.setJiraTestAutomationIssueType(jiraType);
		fieldMapping.setJiraDefectCountlIssueTypeKPI36(jiraType);
		fieldMapping.setJiraDefectCountlIssueTypeKPI28(jiraType);
		fieldMapping.setJiraIssueTypeKPI3(jiraType);
		fieldMapping.setJiraBugRaisedByCustomField("customfield_12121");
		fieldMapping.setJiradefecttype(jiraType);
		fieldMapping.setJiraIssueEpicType(jiraType);

		fieldMapping.setJiraTechDebtIdentification(CommonConstant.CUSTOM_FIELD);
		fieldMapping.setJiraTechDebtCustomField("customfield_14141");

		jiraType = new ArrayList<>();
		jiraType.add("TECH_DEBT");
		fieldMapping.setJiraTechDebtValue(jiraType);
		fieldMapping.setJiraDefectRejectionStatusKPI37("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI14("Dropped");
		fieldMapping.setJiraDefectRejectionStatusAVR("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI28("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI35("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI82("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI135("Dropped");
		fieldMapping.setJiraDefectRejectionStatusQAKPI111("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI34("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI133("Dropped");
		fieldMapping.setJiraDefectRejectionStatusRCAKPI36("Dropped");
		fieldMapping.setJiraBugRaisedByIdentification("CustomField");

		jiraType = new ArrayList<>();
		jiraType.add("Ready for Sign-off");
		fieldMapping.setJiraDodKPI171(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Closed");
		fieldMapping.setJiraDefectRemovalStatusKPI34(jiraType);

		fieldMapping.setJiraStoryPointsCustomField("customfield_56789");

		jiraType = new ArrayList<>();
		jiraType.add("40");

		jiraType = new ArrayList<>();
		jiraType.add("Client Testing (UAT)");
		fieldMapping.setJiraBugRaisedByValue(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Story");
		jiraType.add("Feature");
		fieldMapping.setJiraSprintVelocityIssueTypeKPI138(jiraType);

		jiraType = new ArrayList<>(Arrays.asList(new String[]{"Story", "Defect", "Pre Story", "Feature"}));
		fieldMapping.setJiraSprintCapacityIssueTypeKpi46(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Closed");
		fieldMapping.setJiraIssueDeliverdStatusAVR(jiraType);
		fieldMapping.setJiraIssueDeliverdStatusKPI138(jiraType);
		fieldMapping.setJiraIssueDeliverdStatusKPI126(jiraType);
		fieldMapping.setJiraIssueDeliverdStatusKPI82(jiraType);

		fieldMapping.setJiraDorKPI171(Arrays.asList("In Progress"));
		fieldMapping.setJiraLiveStatus("Closed");
		fieldMapping.setRootCauseValue(Arrays.asList("Coding", "None"));
		fieldMapping.setRootCauseValues(Arrays.asList("Coding", "None"));
		fieldMapping.setRootCause("Coding");

		jiraType = new ArrayList<>(Arrays.asList(new String[]{"Story", "Pre Story"}));
		fieldMapping.setJiraStoryIdentification(jiraType);

		fieldMapping.setJiraDefectCreatedStatusKPI14("Open");

		jiraType = new ArrayList<>();
		jiraType.add("Ready for Sign-off");
		fieldMapping.setJiraDodKPI171(jiraType);
		fieldMapping.setStoryFirstStatus("In Analysis");
		jiraType = new ArrayList<>();
		jiraType.add("In Analysis");
		jiraType.add("In Development");
		fieldMapping.setJiraStatusForDevelopmentAVR(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Ready for Testing");
		fieldMapping.setJiraStatusForQaKPI148(jiraType);

		List<String> jiraSegData = new ArrayList<>();
		jiraSegData.add("Tech Story");
		jiraSegData.add("Task");

		jiraSegData = new ArrayList<>();
		jiraSegData.add("Tech Story");
		fieldMappingList.add(fieldMapping);

		// FieldMapping on 2nd project

		fieldMapping = new FieldMapping();
		fieldMapping.setBasicProjectConfigId(new ObjectId("5b719d06a500d00814bfb2b9"));
		jiraType = new ArrayList<>();
		jiraType.add("Defect");
		fieldMapping.setJiradefecttype(jiraType);

		jiraIssueType = new String[]{"Support Request", "Incident", "Project Request", "Member Account Request",
				"DOJO Consulting Request", "Test Case"};
		fieldMapping.setJiraIssueTypeNames(jiraIssueType);
		fieldMapping.setStoryFirstStatus("Open");

		fieldMapping.setRootCause("customfield_19121");
		fieldMapping.setRootCauseIdentifier("Labels");
		fieldMapping.setJiraDefectRejectionStatusKPI37("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI14("Dropped");
		fieldMapping.setJiraDefectRejectionStatusAVR("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI28("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI35("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI82("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI135("Dropped");
		fieldMapping.setJiraDefectRejectionStatusQAKPI111("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI34("Dropped");
		fieldMapping.setJiraDefectRejectionStatusKPI133("Dropped");
		fieldMapping.setJiraDefectRejectionStatusRCAKPI36("Dropped");
		fieldMapping.setJiraBugRaisedByIdentification("CustomField");

		jiraType = new ArrayList<>();
		jiraType.add("Ready for Sign-off");
		fieldMapping.setJiraDodKPI171(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Closed");
		fieldMapping.setJiraDefectRemovalStatusKPI34(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("40");

		fieldMapping.setJiraStoryPointsCustomField("customfield_56789");
		fieldMapping.setJiraTechDebtIdentification("CustomField");

		jiraType = new ArrayList<>(Arrays.asList(new String[]{"Support Request", "Incident", "Project Request",
				"Member Account Request", "DOJO Consulting Request", "Test Case"}));
		fieldMapping.setTicketCountIssueType(jiraType);
		fieldMapping.setJiraTicketVelocityIssueTypeKPI49(jiraType);
		fieldMapping.setKanbanJiraTechDebtIssueType(jiraType);
		fieldMapping.setKanbanCycleTimeIssueType(jiraType);
		fieldMapping.setKanbanCycleTimeIssueTypeKPI53(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Resolved");
		fieldMapping.setTicketDeliveredStatusKPI49(jiraType);
		fieldMapping.setJiraTicketResolvedStatus(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Reopen");
		fieldMapping.setTicketReopenStatus(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Closed");
		fieldMapping.setJiraTicketClosedStatus(jiraType);

		jiraType = new ArrayList<>();
		jiraType.add("Assigned");
		fieldMapping.setJiraTicketTriagedStatus(jiraType);

		fieldMapping.setJiraLiveStatus("Closed");
		fieldMapping.setRootCauseValue(Arrays.asList("Coding", "None"));

		fieldMapping.setEpicName("customfield_14502");
		jiraType = new ArrayList<>();
		jiraType.add("Ready for Sign-off");
		fieldMapping.setJiraDodKPI171(jiraType);

		jiraSegData = new ArrayList<>();
		jiraSegData.add("Tech Story");
		jiraSegData.add("Task");

		jiraSegData = new ArrayList<>();
		jiraSegData.add("In Analysis");
		jiraSegData.add("In Development");
		fieldMapping.setJiraStatusForDevelopmentAVR(jiraSegData);

		jiraSegData = new ArrayList<>();
		jiraSegData.add("Ready for Testing");
		fieldMapping.setJiraStatusForQaKPI148(jiraSegData);

		jiraSegData = new ArrayList<>();
		jiraSegData.add("segregationLabel");
		fieldMappingList.add(fieldMapping);
	}

	private void setProjectConfigFieldMap() throws IllegalAccessException, InvocationTargetException {

		BeanUtils.copyProperties(scrumProjectList.get(0), projectConfFieldMapping);
		projectConfFieldMapping.setBasicProjectConfigId(scrumProjectList.get(0).getId());
		projectConfFieldMapping.setFieldMapping(fieldMappingList.get(0));
		projectConfFieldMappingList.add(projectConfFieldMapping);
	}

	private void createIssue() throws URISyntaxException, JSONException {

		Map<String, String> map = new HashMap<>();
		map.put("customfield_12121", "Client Testing (UAT)");
		map.put("self", "https://jiradomain.com/jira/rest/api/2/customFieldOption/20810");
		map.put("value", "Component");
		map.put("id", "20810");
		JSONObject value = new JSONObject(map);
		Fields fields = new Fields();
		fields.setSystemWorkItemType("defect");
		fields.setSystemTitle("systemTitle");
		fields.setMicrosoftVSTSCommonPriority(1);
		fields.setSystemTags("Coding");
		Value issue = new Value();
		issue.setId(1);
		issue.setUrl("https://testDomain.com/jira/rest/api/2/");
		issue.setFields(fields);
		Value issue1 = new Value();
		issue1.setId(2);
		issue1.setUrl("https://testDomain.com/jira/rest/api/2/");
		issues.add(issue);
		issues.add(issue1);
		issue1.setFields(fields);
	}

    @Test
    public void testSetLateRefinement188() throws Exception {
        // Arrange
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setJiraRefinementCriteriaKPI188(CommonConstant.CUSTOM_FIELD);
        fieldMapping.setJiraRefinementByCustomFieldKPI188("customfield_14141");
        fieldMapping.setJiraRefinementMinLengthKPI188("2");
        fieldMapping.setJiraRefinementKeywordsKPI188(Arrays.asList("keyword1", "keyword2"));

        JiraIssue jiraIssue = new JiraIssue();

        Map<String, Object> fieldsMap = new HashMap<>();
        fieldsMap.put("customfield_14141", "keyword1 keyword3");

        // Use reflection to access the private method
        java.lang.reflect.Method method = ScrumAzureIssueClientImpl.class.getDeclaredMethod(
                "setLateRefinement188", FieldMapping.class, JiraIssue.class, Map.class);
        method.setAccessible(true);

        // Act
        method.invoke(scrumIssueClientImpl, fieldMapping, jiraIssue, fieldsMap);

        // Assert
        assertNotNull(jiraIssue.getUnRefinedValue188());
        assertTrue(jiraIssue.getUnRefinedValue188().contains("keyword3"));
    }

    @Test
    public void testSetLateRefinement188_NoValue() throws Exception {
        // Arrange
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setJiraRefinementCriteriaKPI188(CommonConstant.CUSTOM_FIELD);
        fieldMapping.setJiraRefinementByCustomFieldKPI188("customfield_14141");

        JiraIssue jiraIssue = new JiraIssue();

        Map<String, Object> fieldsMap = new HashMap<>();
        fieldsMap.put("customfield_14141", "");

        // Act
        java.lang.reflect.Method method = ScrumAzureIssueClientImpl.class.getDeclaredMethod(
                "setLateRefinement188", FieldMapping.class, JiraIssue.class, Map.class);
        method.setAccessible(true);

        // Act
        method.invoke(scrumIssueClientImpl, fieldMapping, jiraIssue, fieldsMap);


        // Assert
        assertNotNull(jiraIssue.getUnRefinedValue188());
        assertTrue(jiraIssue.getUnRefinedValue188().contains("No Value"));
    }

    @Test
    public void testSetLateRefinement188_NoMatchingKeywords() throws Exception{
        // Arrange
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setJiraRefinementCriteriaKPI188(CommonConstant.CUSTOM_FIELD);
        fieldMapping.setJiraRefinementByCustomFieldKPI188("customfield_14141");
        fieldMapping.setJiraRefinementMinLengthKPI188("2");
        fieldMapping.setJiraRefinementKeywordsKPI188(Arrays.asList("keyword1", "keyword2"));

        JiraIssue jiraIssue = new JiraIssue();

        Map<String, Object> fieldsMap = new HashMap<>();
        fieldsMap.put("customfield_14141", "keyword3 keyword4");

        // Act
        java.lang.reflect.Method method = ScrumAzureIssueClientImpl.class.getDeclaredMethod(
                "setLateRefinement188", FieldMapping.class, JiraIssue.class, Map.class);
        method.setAccessible(true);

        // Act
        method.invoke(scrumIssueClientImpl, fieldMapping, jiraIssue, fieldsMap);


        // Assert
        assertNotNull(jiraIssue.getUnRefinedValue188());
        assertTrue(jiraIssue.getUnRefinedValue188().contains("keyword3"));
        assertTrue(jiraIssue.getUnRefinedValue188().contains("keyword4"));
    }
}
