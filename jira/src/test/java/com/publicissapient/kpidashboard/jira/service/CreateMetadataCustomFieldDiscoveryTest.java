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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.atlassian.jira.rest.client.api.MetadataRestClient;
import com.atlassian.jira.rest.client.api.StatusCategory;
import com.atlassian.jira.rest.client.api.domain.Field;
import com.atlassian.jira.rest.client.api.domain.FieldType;
import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.jira.Identifier;
import com.publicissapient.kpidashboard.common.model.jira.MetadataIdentifier;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.jira.BoardMetadataRepository;
import com.publicissapient.kpidashboard.common.repository.jira.MetadataIdentifierRepository;
import com.publicissapient.kpidashboard.jira.cache.JiraProcessorCacheEvictor;
import com.publicissapient.kpidashboard.jira.client.ProcessorJiraRestClient;
import com.publicissapient.kpidashboard.jira.model.ProjectConfFieldMapping;

import io.atlassian.util.concurrent.Promise;

/**
 * Covers how a custom field discovered from the board metadata reaches the stored field mapping.
 *
 * <p>The interesting case is a project that is already configured: the mapping must not be
 * replaced, yet a custom field that was only just introduced still has to find its way in.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CreateMetadataCustomFieldDiscoveryTest {

	private static final String ACCEPTANCE_CRITERIA_TYPE = "jiraAcceptanceCriteriaCustomField";
	private static final String ACCEPTANCE_CRITERIA_FIELD_ID = "customfield_11101";
	private static final String SPRINT_FIELD_ID = "customfield_12700";
	private static final String USER_CHOSEN_SPRINT_FIELD = "customfield_98765";

	@Mock private BoardMetadataRepository boardMetadataRepository;
	@Mock private FieldMappingRepository fieldMappingRepository;
	@Mock private MetadataIdentifierRepository metadataIdentifierRepository;
	@Mock private JiraProcessorCacheEvictor jiraProcessorCacheEvictor;
	@Mock private ProcessorToolConnectionService processorToolConnectionService;
	@Mock private ProcessorJiraRestClient client;
	@Mock private Promise<Iterable<Field>> fieldPromise;
	@Mock private Promise<Iterable<IssueType>> issueTypePromise;
	@Mock private Promise<Iterable<Status>> statusPromise;

	@InjectMocks private CreateMetadataImpl createMetadata;

	@Before
	public void setup() throws URISyntaxException {
		MetadataRestClient metadataRestClient = mock(MetadataRestClient.class);
		when(client.getMetadataClient()).thenReturn(metadataRestClient);

		// the JRJC constructor takes the field id first and the display name second
		List<Field> fields =
				Arrays.asList(
						new Field(SPRINT_FIELD_ID, "Sprint", FieldType.JIRA, true, true, true, null),
						new Field(
								ACCEPTANCE_CRITERIA_FIELD_ID,
								"Acceptance Criteria",
								FieldType.JIRA,
								true,
								true,
								true,
								null));
		when(metadataRestClient.getFields()).thenReturn(fieldPromise);
		when(fieldPromise.claim()).thenReturn(fields);

		List<IssueType> issueTypes =
				Collections.singletonList(
						new IssueType(new URI("self"), 1L, "Story", false, "desc", new URI("iconURI")));
		when(metadataRestClient.getIssueTypes()).thenReturn(issueTypePromise);
		when(issueTypePromise.claim()).thenReturn(issueTypes);

		List<Status> statuses =
				Collections.singletonList(
						new Status(
								new URI("self"),
								1L,
								"Open",
								"desc",
								new URI("iconURI"),
								new StatusCategory(new URI("self"), "name", 1L, "key", "colorname")));
		when(metadataRestClient.getStatuses()).thenReturn(statusPromise);
		when(statusPromise.claim()).thenReturn(statuses);

		when(boardMetadataRepository.findByProjectBasicConfigId(any())).thenReturn(null);
	}

	@Test
	public void fillsInTheDiscoveredCustomFieldOnAConfiguredProject() {
		FieldMapping stored = configuredFieldMapping();
		ProjectConfFieldMapping projectConfig = projectConfig(stored);
		stubIdentifier(acceptanceCriteriaIdentifier(), sprintIdentifier());

		createMetadata.collectMetadata(projectConfig, client, "false");

		assertEquals(ACCEPTANCE_CRITERIA_FIELD_ID, stored.getJiraAcceptanceCriteriaCustomField());
		// the project had already picked a sprint field, that choice must survive
		assertEquals(USER_CHOSEN_SPRINT_FIELD, stored.getSprintName());
		// and nothing else about the configured mapping may be rewritten
		assertArrayEquals(new String[] {"Story"}, stored.getJiraIssueTypeNames());
		assertEquals(Collections.singletonList("Defect"), stored.getJiradefecttype());
		verify(fieldMappingRepository).save(stored);
	}

	@Test
	public void leavesAConfiguredProjectUntouchedWhenThereIsNothingToFillIn() {
		FieldMapping stored = configuredFieldMapping();
		stored.setJiraAcceptanceCriteriaCustomField("customfield_55555");
		ProjectConfFieldMapping projectConfig = projectConfig(stored);
		stubIdentifier(acceptanceCriteriaIdentifier(), sprintIdentifier());

		createMetadata.collectMetadata(projectConfig, client, "false");

		assertEquals("customfield_55555", stored.getJiraAcceptanceCriteriaCustomField());
		verify(fieldMappingRepository, never()).save(any(FieldMapping.class));
		verify(jiraProcessorCacheEvictor, never()).evictCache(any(), any());
	}

	@Test
	public void doesNothingWhenTheIdentifierIsNotConfiguredYet() {
		FieldMapping stored = configuredFieldMapping();
		ProjectConfFieldMapping projectConfig = projectConfig(stored);
		// a database that has not run the metadata identifier change unit yet
		stubIdentifier(sprintIdentifier());

		createMetadata.collectMetadata(projectConfig, client, "false");

		assertNull(stored.getJiraAcceptanceCriteriaCustomField());
		verify(fieldMappingRepository, never()).save(any(FieldMapping.class));
	}

	@Test
	public void skipsAnIdentifierTheBoardDoesNotExpose() {
		FieldMapping stored = configuredFieldMapping();
		ProjectConfFieldMapping projectConfig = projectConfig(stored);
		stubIdentifier(
				createIdentifier(ACCEPTANCE_CRITERIA_TYPE, Collections.singletonList("Not On This Board")));

		createMetadata.collectMetadata(projectConfig, client, "false");

		assertNull(stored.getJiraAcceptanceCriteriaCustomField());
		verify(fieldMappingRepository, never()).save(any(FieldMapping.class));
	}

	@Test
	public void setsTheDiscoveredCustomFieldOnABrandNewProject() {
		ProjectConfFieldMapping projectConfig = projectConfig(null);
		stubIdentifier(acceptanceCriteriaIdentifier(), sprintIdentifier());

		createMetadata.collectMetadata(projectConfig, client, "false");

		ArgumentCaptor<FieldMapping> saved = ArgumentCaptor.forClass(FieldMapping.class);
		verify(fieldMappingRepository).save(saved.capture());
		assertEquals(
				ACCEPTANCE_CRITERIA_FIELD_ID, saved.getValue().getJiraAcceptanceCriteriaCustomField());
		assertEquals(SPRINT_FIELD_ID, saved.getValue().getSprintName());
	}

	@Test
	public void resolvesTheFieldNameIgnoringCase() {
		FieldMapping stored = configuredFieldMapping();
		ProjectConfFieldMapping projectConfig = projectConfig(stored);
		stubIdentifier(
				createIdentifier(
						ACCEPTANCE_CRITERIA_TYPE, Collections.singletonList("acceptance criteria")));

		createMetadata.collectMetadata(projectConfig, client, "false");

		assertEquals(ACCEPTANCE_CRITERIA_FIELD_ID, stored.getJiraAcceptanceCriteriaCustomField());
	}

	private void stubIdentifier(Identifier... customFields) {
		MetadataIdentifier metadataIdentifier =
				new MetadataIdentifier(
						"Jira",
						"Standard Template",
						"7",
						false,
						false,
						Collections.singletonList(
								createIdentifier("jiraIssueTypeNames", Collections.singletonList("Story"))),
						Arrays.asList(customFields),
						Collections.singletonList(createIdentifier("dod", Collections.singletonList("Closed"))),
						new ArrayList<>(),
						new ArrayList<>());
		when(metadataIdentifierRepository.findByTemplateCodeAndToolAndIsKanban(any(), any(), any()))
				.thenReturn(metadataIdentifier);
	}

	private Identifier acceptanceCriteriaIdentifier() {
		return createIdentifier(
				ACCEPTANCE_CRITERIA_TYPE, Arrays.asList("Acceptance Criteria", "Acceptance Criteria (AC)"));
	}

	private Identifier sprintIdentifier() {
		return createIdentifier("sprintName", Collections.singletonList("Sprint"));
	}

	private Identifier createIdentifier(String type, List<String> value) {
		Identifier identifier = new Identifier();
		identifier.setType(type);
		identifier.setValue(value);
		return identifier;
	}

	/** A project a user has already set up by hand. */
	private FieldMapping configuredFieldMapping() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraIssueTypeNames(new String[] {"Story"});
		fieldMapping.setJiradefecttype(Collections.singletonList("Defect"));
		fieldMapping.setSprintName(USER_CHOSEN_SPRINT_FIELD);
		return fieldMapping;
	}

	private ProjectConfFieldMapping projectConfig(FieldMapping fieldMapping) {
		ProjectToolConfig toolConfig = new ProjectToolConfig();
		toolConfig.setId(new ObjectId());
		toolConfig.setOriginalTemplateCode("7");

		ProjectBasicConfig projectBasicConfig = new ProjectBasicConfig();
		projectBasicConfig.setId(new ObjectId());

		ProjectConfFieldMapping projectConfig = ProjectConfFieldMapping.builder().build();
		projectConfig.setProjectName("Test Project");
		projectConfig.setBasicProjectConfigId(projectBasicConfig.getId());
		projectConfig.setProjectBasicConfig(projectBasicConfig);
		projectConfig.setProjectToolConfig(toolConfig);
		projectConfig.setJiraToolConfigId(toolConfig.getId());
		projectConfig.setKanban(false);
		projectConfig.setFieldMapping(fieldMapping);
		return projectConfig;
	}
}
