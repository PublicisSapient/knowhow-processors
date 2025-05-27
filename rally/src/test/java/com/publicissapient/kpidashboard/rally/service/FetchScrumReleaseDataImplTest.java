package com.publicissapient.kpidashboard.rally.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectRelease;
import com.publicissapient.kpidashboard.common.model.application.ProjectVersion;
import com.publicissapient.kpidashboard.common.repository.application.ProjectReleaseRepo;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;
import com.publicissapient.kpidashboard.common.service.ProjectHierarchyService;

/**
 * Test for FetchScrumReleaseDataImpl
 */
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

    @InjectMocks
    private FetchScrumReleaseDataImpl fetchScrumReleaseDataImpl;

    private ProjectBasicConfig projectBasicConfig;
    private ObjectId projectConfigId;

    @BeforeEach
    public void setup() {
        projectConfigId = new ObjectId();
        
        projectBasicConfig = new ProjectBasicConfig();
        projectBasicConfig.setId(projectConfigId);
        projectBasicConfig.setProjectName("Test Project");
        projectBasicConfig.setProjectNodeId("project123");
    }

    @Test
    public void testSaveScrumAccountHierarchy() {
        // Setup
        ProjectRelease projectRelease = new ProjectRelease();
        projectRelease.setId(new ObjectId());
        projectRelease.setProjectName(projectBasicConfig.getProjectName());
        projectRelease.setProjectId(projectBasicConfig.getProjectNodeId());
        projectRelease.setConfigId(projectBasicConfig.getId());
        
        List<ProjectVersion> versions = new ArrayList<>();
        ProjectVersion version = new ProjectVersion();
        version.setId(123L);
        version.setName("FY 23 Q4");
        version.setReleased(true);
        version.setStartDate(new DateTime(2023, 10, 1, 0, 0));
        version.setReleaseDate(new DateTime(2023, 12, 31, 0, 0));
        versions.add(version);
        
        projectRelease.setListProjectVersion(versions);
        
        // Mock hierarchy level service
        HierarchyLevel releaseHierarchyLevel = new HierarchyLevel();
        releaseHierarchyLevel.setHierarchyLevelId("RELEASE");
        
        when(hierarchyLevelService.getReleaseHierarchyLevel()).thenReturn(releaseHierarchyLevel);
        when(projectHierarchyService.getProjectHierarchyMapByConfigIdAndHierarchyLevelId(
                any(String.class), any(String.class))).thenReturn(new HashMap<>());
        
        // Execute
        fetchScrumReleaseDataImpl.saveScrumAccountHierarchy(projectBasicConfig, projectRelease);
        
        // Verify
        verify(hierarchyLevelService).getReleaseHierarchyLevel();
        verify(projectHierarchyService).getProjectHierarchyMapByConfigIdAndHierarchyLevelId(
                any(String.class), any(String.class));
    }
}
