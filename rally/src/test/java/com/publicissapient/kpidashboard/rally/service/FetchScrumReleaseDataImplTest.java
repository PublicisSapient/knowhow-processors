package com.publicissapient.kpidashboard.rally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectHierarchy;
import com.publicissapient.kpidashboard.common.model.application.ProjectRelease;
import com.publicissapient.kpidashboard.common.model.application.ProjectVersion;
import com.publicissapient.kpidashboard.common.repository.application.ProjectReleaseRepo;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;
import com.publicissapient.kpidashboard.common.service.ProjectHierarchyService;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.RallyReleaseResponse;
import com.publicissapient.kpidashboard.rally.model.Release;
import com.publicissapient.kpidashboard.rally.model.ReleaseWrapper;
import com.publicissapient.kpidashboard.rally.util.RallyRestClient;

@ExtendWith(MockitoExtension.class)
public class FetchScrumReleaseDataImplTest {

    @Mock
    private ProjectReleaseRepo projectReleaseRepo;

    @Mock
    private HierarchyLevelService hierarchyLevelService;

    @Mock
    private ProjectHierarchyService projectHierarchyService;

    @Mock
    private ProjectHierarchySyncService projectHierarchySyncService;

    @Mock
    private RallyRestClient rallyRestClient;

    @InjectMocks
    private FetchScrumReleaseDataImpl fetchScrumReleaseData;

    private ProjectConfFieldMapping projectConfig;
    private ProjectBasicConfig projectBasicConfig;
    private ObjectId projectConfigId;

    @BeforeEach
    public void setup() {
        projectConfigId = new ObjectId();
        
        projectBasicConfig = new ProjectBasicConfig();
        projectBasicConfig.setId(projectConfigId);
        projectBasicConfig.setProjectName("Test Project");
        projectBasicConfig.setProjectNodeId("project123");
        
        projectConfig = new ProjectConfFieldMapping();
        projectConfig.setProjectBasicConfig(projectBasicConfig);
    }

    @Test
    public void testProcessReleaseInfo() throws IOException, ParseException {
        // Execute
        ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "processReleaseInfo", projectConfig);
        
        // Verify that saveProjectRelease is called
        // This is an indirect test since processReleaseInfo just calls saveProjectRelease
        verify(rallyRestClient).getBaseUrl();
    }

    @Test
    public void testGetRallyVersionsWithValidResponse() throws JsonProcessingException {
        // Setup
        String baseUrl = "https://rally1.rallydev.com/slm/webservice/v2.0";
        
        when(rallyRestClient.getBaseUrl()).thenReturn(baseUrl);
        
        // Create mock response
        RallyReleaseResponse.QueryResult queryResult = new RallyReleaseResponse.QueryResult();
        List<RallyReleaseResponse.Release> results = new ArrayList<>();
        
        RallyReleaseResponse.Release rallyRelease = new RallyReleaseResponse.Release();
        rallyRelease.setId(123L);
        rallyRelease.setName("Release 1.0");
        rallyRelease.setDescription("Test Release");
        rallyRelease.setState("Released");
        rallyRelease.setReleaseDate(new DateTime());
        rallyRelease.setReleaseStartDate(new DateTime().minusDays(30));
        rallyRelease.setRef(baseUrl + "/release/release123");
        
        results.add(rallyRelease);
        queryResult.setResults(results);
        
        RallyReleaseResponse responseBody = new RallyReleaseResponse();
        responseBody.setQueryResult(queryResult);
        
        ResponseEntity<RallyReleaseResponse> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class)))
            .thenReturn(responseEntity);
        
        // Mock the processRelease method
        Release release = new Release();
        release.setRef(baseUrl + "/release/release123");
        release.setObjectID(123L);
        release.setName("Release 1.0");
        release.setTheme("Test Release");
        release.setState("Released");
        
        ReleaseWrapper releaseWrapper = new ReleaseWrapper();
        releaseWrapper.setRelease(release);
        
        ResponseEntity<ReleaseWrapper> releaseResponseEntity = new ResponseEntity<>(releaseWrapper, HttpStatus.OK);
        
        when(rallyRestClient.get(eq(baseUrl + "/release/release123"), eq(projectConfig), eq(ReleaseWrapper.class)))
            .thenReturn(releaseResponseEntity);
        
        // Execute
        List<ProjectVersion> result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "getRallyVersions", projectConfig);
        
        // Verify
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Release 1.0", result.get(0).getName());
    }

    @Test
    public void testGetRallyVersionsWithNullResponse() throws JsonProcessingException {
        // Setup
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class)))
            .thenReturn(null);
        
        // Execute
        List<ProjectVersion> result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "getRallyVersions", projectConfig);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRallyVersionsWithNullBody() throws JsonProcessingException {
        // Setup
        ResponseEntity<RallyReleaseResponse> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class)))
            .thenReturn(responseEntity);
        
        // Execute
        List<ProjectVersion> result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "getRallyVersions", projectConfig);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRallyVersionsWithNullQueryResult() throws JsonProcessingException {
        // Setup
        RallyReleaseResponse responseBody = new RallyReleaseResponse();
        responseBody.setQueryResult(null);
        
        ResponseEntity<RallyReleaseResponse> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class)))
            .thenReturn(responseEntity);
        
        // Execute
        List<ProjectVersion> result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "getRallyVersions", projectConfig);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRallyVersionsWithEmptyResults() throws JsonProcessingException {
        // Setup
        RallyReleaseResponse.QueryResult queryResult = new RallyReleaseResponse.QueryResult();
        queryResult.setResults(Collections.emptyList());
        
        RallyReleaseResponse responseBody = new RallyReleaseResponse();
        responseBody.setQueryResult(queryResult);
        
        ResponseEntity<RallyReleaseResponse> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class)))
            .thenReturn(responseEntity);
        
        // Execute
        List<ProjectVersion> result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "getRallyVersions", projectConfig);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testProcessReleaseWithValidResponse() throws JsonProcessingException {
        // Setup
        String baseUrl = "https://rally1.rallydev.com/slm/webservice/v2.0";
        
        Release release = new Release();
        release.setRef(baseUrl + "/release/release123");
        release.setObjectID(123L);
        release.setName("Release 1.0");
        release.setTheme("Test Release");
        release.setState("Released");
        
        ReleaseWrapper releaseWrapper = new ReleaseWrapper();
        releaseWrapper.setRelease(release);
        
        ResponseEntity<ReleaseWrapper> responseEntity = new ResponseEntity<>(releaseWrapper, HttpStatus.OK);
        
        when(rallyRestClient.get(eq(baseUrl + "/release/release123"), eq(projectConfig), eq(ReleaseWrapper.class)))
            .thenReturn(responseEntity);
        
        // Execute
        ProjectVersion result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "processRelease", release, projectConfig);
        
        // Verify
        assertNotNull(result);
        assertEquals("Release 1.0", result.getName());
    }

    @Test
    public void testProcessReleaseWithNullResponse() throws JsonProcessingException {
        // Setup
        Release release = new Release();
        release.setRef("https://rally1.rallydev.com/slm/webservice/v2.0/release/release123");
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(ReleaseWrapper.class)))
            .thenReturn(null);
        
        // Execute
        ProjectVersion result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "processRelease", release, projectConfig);
        
        // Verify
        assertNull(result);
    }

    @Test
    public void testProcessReleaseWithNullBody() throws JsonProcessingException {
        // Setup
        Release release = new Release();
        release.setRef("https://rally1.rallydev.com/slm/webservice/v2.0/release/release123");
        
        ResponseEntity<ReleaseWrapper> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(ReleaseWrapper.class)))
            .thenReturn(responseEntity);
        
        // Execute
        ProjectVersion result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "processRelease", release, projectConfig);
        
        // Verify
        assertNull(result);
    }

    @Test
    public void testProcessReleaseWithNullReleaseData() throws JsonProcessingException {
        // Setup
        Release release = new Release();
        release.setRef("https://rally1.rallydev.com/slm/webservice/v2.0/release/release123");
        
        ReleaseWrapper releaseWrapper = new ReleaseWrapper();
        releaseWrapper.setRelease(null);
        
        ResponseEntity<ReleaseWrapper> responseEntity = new ResponseEntity<>(releaseWrapper, HttpStatus.OK);
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(ReleaseWrapper.class)))
            .thenReturn(responseEntity);
        
        // Execute
        ProjectVersion result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "processRelease", release, projectConfig);
        
        // Verify
        assertNull(result);
    }

    @Test
    public void testMapToProjectVersionWithNullDates() throws JsonProcessingException {
        // Setup
        Release release = new Release();
        release.setObjectID(123L);
        release.setName("Release 1.0");
        release.setTheme("Test Release");
        release.setState("Released");
        
        // Execute
        ProjectVersion result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "mapToProjectVersion", release);
        
        // Verify
        assertNotNull(result);
        assertEquals("Release 1.0", result.getName());
        assertEquals(123L, result.getId());
    }

    @Test
    public void testSaveScrumAccountHierarchy() {
        // Setup
        ProjectRelease projectRelease = new ProjectRelease();
        projectRelease.setConfigId(projectConfigId);
        
        ProjectVersion projectVersion = new ProjectVersion();
        projectVersion.setId(123L);
        projectVersion.setName("Release 1.0");
        projectVersion.setReleased(true);
        
        List<ProjectVersion> versions = new ArrayList<>();
        versions.add(projectVersion);
        projectRelease.setListProjectVersion(versions);
        
        List<HierarchyLevel> hierarchyLevels = new ArrayList<>();
        HierarchyLevel hierarchyLevel = new HierarchyLevel();
        hierarchyLevel.setId(new ObjectId());
        hierarchyLevel.setHierarchyLevelId(CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);
        hierarchyLevels.add(hierarchyLevel);
        
        when(hierarchyLevelService.getFullHierarchyLevels(anyBoolean()))
            .thenReturn(hierarchyLevels);
        
        // Execute
        ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "saveScrumAccountHierarchy", projectBasicConfig, projectRelease);
        
        // Verify
        verify(projectHierarchyService).getProjectHierarchyMapByConfigIdAndHierarchyLevelId(
            eq(projectConfigId.toString()), eq(CommonConstant.HIERARCHY_LEVEL_ID_RELEASE));
    }

    @Test
    public void testCreateScrumHierarchyForRelease() {
        // Setup
        ProjectRelease projectRelease = new ProjectRelease();
        projectRelease.setConfigId(projectConfigId);
        
        ProjectVersion projectVersion = new ProjectVersion();
        projectVersion.setId(123L);
        projectVersion.setName("Release 1.0");
        projectVersion.setReleased(true);
        
        List<ProjectVersion> versions = new ArrayList<>();
        versions.add(projectVersion);
        projectRelease.setListProjectVersion(versions);
        
        List<HierarchyLevel> hierarchyLevels = new ArrayList<>();
        HierarchyLevel hierarchyLevel = new HierarchyLevel();
        hierarchyLevel.setId(new ObjectId());
        hierarchyLevel.setHierarchyLevelId(CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);
        hierarchyLevels.add(hierarchyLevel);
        
        when(hierarchyLevelService.getFullHierarchyLevels(anyBoolean()))
            .thenReturn(hierarchyLevels);
        
        // Execute
        List<ProjectHierarchy> result = ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "createScrumHierarchyForRelease", projectRelease, projectBasicConfig);
        
        // Verify
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    public void testSetToSaveAccountHierarchy() {
        // Setup
        Map<String, ProjectHierarchy> existingHierarchy = new HashMap<>();
        
        List<ProjectHierarchy> accountHierarchy = new ArrayList<>();
        ProjectHierarchy hierarchy = new ProjectHierarchy();
        hierarchy.setNodeId("node123");
        hierarchy.setParentId("parent123");
        hierarchy.setNodeName("Node 1");
        accountHierarchy.add(hierarchy);
        
        Set<ProjectHierarchy> setToSave = new HashSet<>();
        
        // Execute
        ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "setToSaveAccountHierarchy",
            setToSave, accountHierarchy, existingHierarchy);
        
        // Verify
        assertNotNull(setToSave);
        assertEquals(1, setToSave.size());
    }

    @Test
    public void testSaveProjectReleaseWithEmptyVersions() throws IOException, ParseException {
        // Setup
        String baseUrl = "https://rally1.rallydev.com/slm/webservice/v2.0";
        when(rallyRestClient.getBaseUrl()).thenReturn(baseUrl);
        
        ResponseEntity<RallyReleaseResponse> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class)))
            .thenReturn(responseEntity);
        
        // Execute
        ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "saveProjectRelease", projectConfig);
        
        // Verify
        verify(rallyRestClient).get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class));
        // projectReleaseRepo.save should not be called
        verify(projectReleaseRepo, never()).save(any(ProjectRelease.class));
    }

    @Test
    public void testSaveProjectReleaseWithNullProjectNodeId() throws IOException, ParseException {
        // Setup
        String baseUrl = "https://rally1.rallydev.com/slm/webservice/v2.0";
        when(rallyRestClient.getBaseUrl()).thenReturn(baseUrl);
        
        // Create mock response
        RallyReleaseResponse.QueryResult queryResult = new RallyReleaseResponse.QueryResult();
        List<RallyReleaseResponse.Release> results = new ArrayList<>();
        
        RallyReleaseResponse.Release rallyRelease = new RallyReleaseResponse.Release();
        rallyRelease.setId(123L);
        rallyRelease.setName("Release 1.0");
        rallyRelease.setState("Released");
        rallyRelease.setRef("https://rally1.rallydev.com/slm/webservice/v2.0/release/release123");
        
        results.add(rallyRelease);
        queryResult.setResults(results);
        
        RallyReleaseResponse responseBody = new RallyReleaseResponse();
        responseBody.setQueryResult(queryResult);
        
        ResponseEntity<RallyReleaseResponse> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(rallyRestClient.get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class)))
            .thenReturn(responseEntity);
        
        // Mock release details
        Release release = new Release();
        release.setRef("https://rally1.rallydev.com/slm/webservice/v2.0/release/release123");
        release.setObjectID(123L);
        release.setName("Release 1.0");
        release.setState("Released");
        
        ReleaseWrapper releaseWrapper = new ReleaseWrapper();
        releaseWrapper.setRelease(release);
        
        ResponseEntity<ReleaseWrapper> releaseResponseEntity = new ResponseEntity<>(releaseWrapper, HttpStatus.OK);
        
        when(rallyRestClient.get(contains("/release/"), eq(projectConfig), eq(ReleaseWrapper.class)))
            .thenReturn(releaseResponseEntity);
        
        // Set projectNodeId to null
        projectBasicConfig.setProjectNodeId(null);
        
        // Execute
        ReflectionTestUtils.invokeMethod(fetchScrumReleaseData, "saveProjectRelease", projectConfig);
        
        // Verify
        verify(rallyRestClient).get(anyString(), eq(projectConfig), eq(RallyReleaseResponse.class));
        // projectReleaseRepo.save should not be called
        verify(projectReleaseRepo, never()).save(any(ProjectRelease.class));
    }
}
