package com.publicissapient.kpidashboard.rally.writer;

import com.publicissapient.kpidashboard.common.model.application.ProjectHierarchy;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.jira.AssigneeDetails;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.AssigneeDetailsRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.service.ProjectHierarchyService;
import com.publicissapient.kpidashboard.rally.model.CompositeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueScrumWriterTest {

    @Mock
    private JiraIssueRepository jiraIssueRepository;

    @Mock
    private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

    @Mock
    private ProjectHierarchyService projectHierarchyService;

    @Mock
    private AssigneeDetailsRepository assigneeDetailsRepository;

    @Mock
    private SprintRepository sprintRepository;

    @InjectMocks
    private IssueScrumWriter issueScrumWriter;

    private JiraIssue jiraIssue;
    private JiraIssueCustomHistory jiraIssueCustomHistory;
    private ProjectHierarchy projectHierarchy;
    private AssigneeDetails assigneeDetails;
    private SprintDetails sprintDetails;
    private CompositeResult compositeResult;

    @BeforeEach
    void setUp() {
        // Initialize JiraIssue
        jiraIssue = new JiraIssue();
        jiraIssue.setNumber("ISSUE-1");
        jiraIssue.setBasicProjectConfigId(new org.bson.types.ObjectId().toString());

        // Initialize JiraIssueCustomHistory
        jiraIssueCustomHistory = new JiraIssueCustomHistory();
        jiraIssueCustomHistory.setStoryID("ISSUE-1");
        jiraIssueCustomHistory.setBasicProjectConfigId(new org.bson.types.ObjectId().toString());

        // Initialize ProjectHierarchy
        projectHierarchy = new ProjectHierarchy();
        projectHierarchy.setNodeId("node1");
        projectHierarchy.setNodeName("Test Project");

        // Initialize Assignee
        assigneeDetails = new AssigneeDetails();
        assigneeDetails.setBasicProjectConfigId(new org.bson.types.ObjectId().toString());
        Set<Assignee> assignees = new HashSet<>();
        Assignee assignee = new Assignee("john.doe", "John Doe"); 
        assignees.add(assignee);
        assigneeDetails.setAssignee(assignees);

        // Initialize SprintDetails
        sprintDetails = new SprintDetails();
        sprintDetails.setSprintID("sprint1");
        sprintDetails.setSprintName("Sprint 1");
        sprintDetails.setBasicProjectConfigId(new org.bson.types.ObjectId());
        Set<SprintIssue> totalIssues = new HashSet<>();
        SprintIssue sprintIssue = new SprintIssue();
        sprintIssue.setNumber("ISSUE-1");
        totalIssues.add(sprintIssue);
        sprintDetails.setTotalIssues(totalIssues);

        // Initialize CompositeResult
        compositeResult = new CompositeResult();
        compositeResult.setJiraIssue(jiraIssue);
        compositeResult.setJiraIssueCustomHistory(jiraIssueCustomHistory);
        Set<ProjectHierarchy> projectHierarchies = new HashSet<>();
        projectHierarchies.add(projectHierarchy);
        compositeResult.setProjectHierarchies(projectHierarchies);
        compositeResult.setAssigneeDetails(assigneeDetails);
        Set<SprintDetails> sprintDetailsSet = new HashSet<>();
        sprintDetailsSet.add(sprintDetails);
        compositeResult.setSprintDetailsSet(sprintDetailsSet);
    }

    @Test
    void testWriteWithAllData() throws Exception {
        // Create a list of CompositeResult with one item
        Chunk<CompositeResult> results = new Chunk<>(Arrays.asList(compositeResult));

        // Mock repository calls
        when(sprintRepository.findBySprintID(any())).thenReturn(null);

        // Call the write method
        issueScrumWriter.write(results);

        // Verify repository calls
        verify(jiraIssueRepository).saveAll(anyList());
        verify(jiraIssueCustomHistoryRepository).saveAll(anyList());
        verify(projectHierarchyService).saveAll(any());
        verify(assigneeDetailsRepository).saveAll(anyList());
        verify(sprintRepository).save(any(SprintDetails.class));
    }

    @Test
    void testWriteWithExistingSprint() throws Exception {
        // Create a list of CompositeResult with one item
        Chunk<CompositeResult> results = new Chunk<>(Arrays.asList(compositeResult));

        // Create existing sprint with different data
        SprintDetails existingSprint = new SprintDetails();
        existingSprint.setSprintID("sprint1");
        existingSprint.setSprintName("Old Sprint 1");
        Set<SprintIssue> existingIssues = new HashSet<>();
        SprintIssue oldIssue = new SprintIssue();
        oldIssue.setNumber("OLD-ISSUE-1");
        existingIssues.add(oldIssue);
        existingSprint.setTotalIssues(existingIssues);

        // Mock repository calls
        when(sprintRepository.findBySprintID(any())).thenReturn(existingSprint);

        // Call the write method
        issueScrumWriter.write(results);

        // Verify repository calls
        verify(jiraIssueRepository).saveAll(anyList());
        verify(jiraIssueCustomHistoryRepository).saveAll(anyList());
        verify(projectHierarchyService).saveAll(any());
        verify(assigneeDetailsRepository).saveAll(anyList());
        verify(sprintRepository).save(any(SprintDetails.class));

        // Verify sprint was updated with merged data
        verify(sprintRepository).save(argThat(sprint -> 
            sprint.getSprintName().equals("Sprint 1") && // New name
            sprint.getTotalIssues().size() == 2 && // Merged issues
            sprint.getTotalIssues().stream().anyMatch(issue -> issue.getNumber().equals("ISSUE-1")) && // New issue
            sprint.getTotalIssues().stream().anyMatch(issue -> issue.getNumber().equals("OLD-ISSUE-1")) // Old issue
        ));
    }

    @Test
    void testWriteWithNullData() throws Exception {
        // Create a CompositeResult with null data
        CompositeResult emptyResult = new CompositeResult();
        Chunk<CompositeResult> results = new Chunk<>(Arrays.asList(emptyResult));

        // Call the write method
        issueScrumWriter.write(results);

        // Verify no repository calls were made
        verify(jiraIssueRepository, never()).saveAll(anyList());
        verify(jiraIssueCustomHistoryRepository, never()).saveAll(anyList());
        verify(projectHierarchyService, never()).saveAll(any());
        verify(assigneeDetailsRepository, never()).saveAll(anyList());
        verify(sprintRepository, never()).save(any(SprintDetails.class));
    }

    @Test
    void testWriteWithMultipleResults() throws Exception {
        // Create second composite result with different data
        CompositeResult compositeResult2 = new CompositeResult();
        
        JiraIssue jiraIssue2 = new JiraIssue();
        jiraIssue2.setNumber("ISSUE-2");
        jiraIssue2.setBasicProjectConfigId(new org.bson.types.ObjectId().toString());
        compositeResult2.setJiraIssue(jiraIssue2);

        SprintDetails sprintDetails2 = new SprintDetails();
        sprintDetails2.setSprintID("sprint2");
        sprintDetails2.setSprintName("Sprint 2");
        Set<SprintDetails> sprintDetailsSet2 = new HashSet<>();
        sprintDetailsSet2.add(sprintDetails2);
        compositeResult2.setSprintDetailsSet(sprintDetailsSet2);

        // Create chunk with multiple results
        Chunk<CompositeResult> results = new Chunk<>(Arrays.asList(compositeResult, compositeResult2));

        // Mock repository calls
        when(sprintRepository.findBySprintID(any())).thenReturn(null);

        // Call the write method
        issueScrumWriter.write(results);

        // Verify repository calls with correct number of items
        verify(jiraIssueRepository).saveAll(argThat(issues -> ((List<JiraIssue>)issues).size() == 2));
        verify(sprintRepository, times(2)).save(any(SprintDetails.class));
    }
}
