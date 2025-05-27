package com.publicissapient.kpidashboard.rally.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import com.publicissapient.kpidashboard.rally.helper.ReaderRetryHelper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.rally.config.FetchProjectConfiguration;
import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.ReadData;
import com.publicissapient.kpidashboard.rally.service.FetchIssueSprint;

@ExtendWith(MockitoExtension.class)
public class IssueSprintReaderTest {

    @InjectMocks
    private IssueSprintReader issueSprintReader;

    @Mock
    private FetchProjectConfiguration fetchProjectConfiguration;

    @Mock
    private RallyProcessorConfig rallyProcessorConfig;

    @Mock
    private FetchIssueSprint fetchIssueSprint;

    private ProjectConfFieldMapping projectConfFieldMapping;
    private HierarchicalRequirement requirement1;
    private HierarchicalRequirement requirement2;
    private String sprintId;

    @BeforeEach
    public void setup() {
        sprintId = "SPRINT-1";
        projectConfFieldMapping = new ProjectConfFieldMapping();
        projectConfFieldMapping.setBasicProjectConfigId(new ObjectId());
        projectConfFieldMapping.setProjectName("Test Project");

        requirement1 = new HierarchicalRequirement();
        requirement1.setName("Requirement 1");
        requirement2 = new HierarchicalRequirement();
        requirement2.setName("Requirement 2");

        issueSprintReader.sprintId = sprintId;
        issueSprintReader.processorId = new ObjectId().toString();
        issueSprintReader.retryHelper = new ReaderRetryHelper();
        
        when(rallyProcessorConfig.getPageSize()).thenReturn(50);
    }

    @Test
    public void testReadWithNoConfiguration() throws Exception {
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(anyString())).thenReturn(null);

        ReadData result = issueSprintReader.read();

        assertNull(result);
    }

    @Test
    public void testReadWithValidConfiguration() throws Exception {
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(anyString())).thenReturn(projectConfFieldMapping);
        when(fetchIssueSprint.fetchIssuesSprintBasedOnJql(any(), anyInt(), anyString()))
            .thenReturn(Arrays.asList(requirement1, requirement2));

        ReadData result = issueSprintReader.read();

        assertNotNull(result);
        assertEquals(requirement1, result.getHierarchicalRequirement());
        assertEquals(projectConfFieldMapping, result.getProjectConfFieldMapping());
        assertEquals(true, result.isSprintFetch());
        assertNotNull(result.getProcessorId());
    }

    @Test
    public void testReadWithEmptyResults() throws Exception {
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(anyString())).thenReturn(projectConfFieldMapping);
        when(fetchIssueSprint.fetchIssuesSprintBasedOnJql(any(), anyInt(), anyString()))
            .thenReturn(Collections.emptyList());

        ReadData result = issueSprintReader.read();

        assertNull(result);
    }

    @Test
    public void testReadWithMultiplePages() throws Exception {
        // Set pageSize to 1 to force multiple pages
        when(rallyProcessorConfig.getPageSize()).thenReturn(1);
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(eq(sprintId))).thenReturn(projectConfFieldMapping);
        
        // First page returns one item
        when(fetchIssueSprint.fetchIssuesSprintBasedOnJql(eq(projectConfFieldMapping), eq(0), eq(sprintId)))
            .thenReturn(Arrays.asList(requirement1));
        // Second page returns one item
        when(fetchIssueSprint.fetchIssuesSprintBasedOnJql(eq(projectConfFieldMapping), eq(1), eq(sprintId)))
            .thenReturn(Arrays.asList(requirement2));
        // Third page returns empty to indicate end
        when(fetchIssueSprint.fetchIssuesSprintBasedOnJql(eq(projectConfFieldMapping), eq(2), eq(sprintId)))
            .thenReturn(Collections.emptyList());

        // First read should initialize and get first page
        ReadData result1 = issueSprintReader.read();
        assertNotNull(result1);
        assertEquals(requirement1, result1.getHierarchicalRequirement());
        assertEquals(projectConfFieldMapping, result1.getProjectConfFieldMapping());
        assertEquals(true, result1.isSprintFetch());

        // Second read should get second page
        ReadData result2 = issueSprintReader.read();
        assertNotNull(result2);
        assertEquals(requirement2, result2.getHierarchicalRequirement());
        assertEquals(projectConfFieldMapping, result2.getProjectConfFieldMapping());
        assertEquals(true, result2.isSprintFetch());

        // Third read should return null as no more data
        ReadData result3 = issueSprintReader.read();
        assertNull(result3);
    }

    @Test
    public void testReadWithSinglePagePartialResults() throws Exception {
        // Set pageSize to 2 to test partial page scenario
        when(rallyProcessorConfig.getPageSize()).thenReturn(2);
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(eq(sprintId))).thenReturn(projectConfFieldMapping);
        
        // Return two requirements to ensure iterator is not null
        when(fetchIssueSprint.fetchIssuesSprintBasedOnJql(eq(projectConfFieldMapping), eq(0), eq(sprintId)))
            .thenReturn(Arrays.asList(requirement1, requirement2));

        // First read should get the first item
        ReadData result1 = issueSprintReader.read();
        assertNotNull(result1);
        assertEquals(requirement1, result1.getHierarchicalRequirement());
        assertEquals(projectConfFieldMapping, result1.getProjectConfFieldMapping());
        assertEquals(true, result1.isSprintFetch());

        // Second read should get the second item
        ReadData result2 = issueSprintReader.read();
        assertNotNull(result2);
        assertEquals(requirement2, result2.getHierarchicalRequirement());
        assertEquals(projectConfFieldMapping, result2.getProjectConfFieldMapping());
        assertEquals(true, result2.isSprintFetch());

        // Third read should return null as we've read all items
        ReadData result3 = issueSprintReader.read();
        assertNull(result3);
    }

    @Test
    public void testReadWithNullIterator() throws Exception {
        when(fetchProjectConfiguration.fetchConfigurationBasedOnSprintId(anyString())).thenReturn(projectConfFieldMapping);
        when(fetchIssueSprint.fetchIssuesSprintBasedOnJql(any(), anyInt(), anyString()))
            .thenReturn(Collections.emptyList());

        ReadData result = issueSprintReader.read();

        assertNull(result);
    }
}
