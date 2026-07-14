/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.jira.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ProjectHierarchy;
import com.publicissapient.kpidashboard.common.model.jira.KanbanJiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.application.ProjectHierarchyRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.jira.dataFactories.KanbanJiraIssueDataFactory;
import com.publicissapient.kpidashboard.jira.dataFactories.ProjectHierarchiesDataFactory;

@RunWith(MockitoJUnitRunner.class)
public class ProjectHierarchySyncServiceImplTest {

	@InjectMocks private ProjectHierarchySyncServiceImpl projectHierarchySyncServiceImpl;

	@Mock ProjectHierarchyRepository accountHierarchyRepository;

	@Mock SprintRepository sprintRepository;

	List<ProjectHierarchy> accountHierarchyList;
	List<SprintDetails> sprintDetailsList;
	List<KanbanJiraIssue> kanbanJiraIssueList;
	List<ProjectHierarchy> fetchedReleasedHierarchy;

	@Before
	public void setUp() {
		ProjectHierarchiesDataFactory accountHierarchiesDataFactory =
				ProjectHierarchiesDataFactory.newInstance("/json/default/project_hierarchy.json");
		accountHierarchyList = accountHierarchiesDataFactory.getAccountHierarchies();
		SprintDetails sprint1 = new SprintDetails();
		sprint1.setSprintID("Sprint1");
		SprintDetails sprint2 = new SprintDetails();
		sprint2.setSprintID("Sprint2");
		sprintDetailsList = List.of(sprint1, sprint2);
		KanbanJiraIssueDataFactory kanbanJiraIssueDataFactory =
				KanbanJiraIssueDataFactory.newInstance("/json/default/kanban_jira_issue.json");
		kanbanJiraIssueList = kanbanJiraIssueDataFactory.getKanbanJiraIssues();
		ProjectHierarchy projectHierarchy = new ProjectHierarchy();
		projectHierarchy.setNodeId("Release1");
		ProjectHierarchy projectHierarchy2 = new ProjectHierarchy();
		projectHierarchy2.setNodeId("Release2");
		fetchedReleasedHierarchy = List.of(projectHierarchy, projectHierarchy2);
	}

	@Test
	public void syncScrumSprintHierarchyNoSprintsToDeleteFalseHit() {
		ObjectId projectId = new ObjectId();
		List<String> nonMatchingNodeIds = List.of();

		when(sprintRepository.findByBasicProjectConfigId(projectId)).thenReturn(sprintDetailsList);

		projectHierarchySyncServiceImpl.syncScrumSprintHierarchy(projectId);

		verify(sprintRepository, never())
				.deleteBySprintIDInAndBasicProjectConfigId(nonMatchingNodeIds, projectId);
	}

	@Test
	public void syncScrumReleaseHierarchyDeletesNonMatchingReleasesFalseHit() {
		ObjectId projectId = new ObjectId();
		List<String> distinctReleaseNodeIds = List.of("Release1", "Release2");
		List<String> entriesToDelete = List.of("Release3");

		when(accountHierarchyRepository.findNodeIdsByBasicProjectConfigIdAndNodeIdNotIn(
						projectId, distinctReleaseNodeIds, CommonConstant.HIERARCHY_LEVEL_ID_RELEASE))
				.thenReturn(accountHierarchyList);

		projectHierarchySyncServiceImpl.syncReleaseHierarchy(projectId, fetchedReleasedHierarchy);

		verify(accountHierarchyRepository)
				.findNodeIdsByBasicProjectConfigIdAndNodeIdNotIn(
						projectId, distinctReleaseNodeIds, CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);
	}

	@Test
	public void syncKanbanReleaseHierarchyNoReleasesToDeleteFalseHit() {
		ObjectId projectId = new ObjectId();
		List<String> distinctReleaseNodeIds = List.of("Release1", "Release2");
		List<ProjectHierarchy> entriesToDelete = List.of();

		when(accountHierarchyRepository.findNodeIdsByBasicProjectConfigIdAndNodeIdNotIn(
						projectId, distinctReleaseNodeIds, CommonConstant.HIERARCHY_LEVEL_ID_RELEASE))
				.thenReturn(entriesToDelete);

		projectHierarchySyncServiceImpl.syncReleaseHierarchy(projectId, fetchedReleasedHierarchy);

		verify(accountHierarchyRepository)
				.findNodeIdsByBasicProjectConfigIdAndNodeIdNotIn(
						projectId, distinctReleaseNodeIds, CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);
	}

	@Test
	public void syncScrumReleaseHierarchyDeletesNonMatchingReleases() {
		ObjectId projectId = new ObjectId();
		List<String> distinctReleaseNodeIds = List.of("Release1", "Release2");

		projectHierarchySyncServiceImpl.syncReleaseHierarchy(projectId, fetchedReleasedHierarchy);

		verify(accountHierarchyRepository)
				.findNodeIdsByBasicProjectConfigIdAndNodeIdNotIn(
						projectId, distinctReleaseNodeIds, CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);
	}

	@Test
	public void syncKanbanReleaseHierarchyDeletesNonMatchingReleases() {
		ObjectId projectId = new ObjectId();

		List<String> distinctReleaseNodeIds = List.of("Release1", "Release2");
		projectHierarchySyncServiceImpl.syncReleaseHierarchy(projectId, fetchedReleasedHierarchy);

		verify(accountHierarchyRepository)
				.findNodeIdsByBasicProjectConfigIdAndNodeIdNotIn(
						projectId, distinctReleaseNodeIds, CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);
	}

	@Test
	public void deleteNonMatchingEntriesDeletesNonMatchingEntries() {
		ObjectId projectId = new ObjectId();
		List<String> distinctReleaseNodeIds = List.of("Node1", "Node2");

		projectHierarchySyncServiceImpl.deleteNonMatchingEntries(
				projectId, distinctReleaseNodeIds, CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);

		verify(accountHierarchyRepository)
				.deleteByBasicProjectConfigIdAndNodeIdIn(
						projectId, distinctReleaseNodeIds, CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);
	}

	@Test
	public void syncScrumSprintHierarchyNoSprintsToDeleteTrueHit() {
		ObjectId projectId = new ObjectId();
		List<String> nonMatchingNodeIds = List.of();

		when(sprintRepository.findByBasicProjectConfigId(projectId)).thenReturn(sprintDetailsList);
		when(accountHierarchyRepository.findNodeIdsByBasicProjectConfigIdAndNodeIdNotIn(
						projectId,
						sprintDetailsList.stream().map(SprintDetails::getSprintID).toList(),
						CommonConstant.HIERARCHY_LEVEL_ID_SPRINT))
				.thenReturn(accountHierarchyList);

		projectHierarchySyncServiceImpl.syncScrumSprintHierarchy(projectId);

		verify(sprintRepository, never())
				.deleteBySprintIDInAndBasicProjectConfigId(nonMatchingNodeIds, projectId);
	}
}
