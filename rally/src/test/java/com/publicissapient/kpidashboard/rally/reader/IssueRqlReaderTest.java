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

package com.publicissapient.kpidashboard.rally.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.rally.config.FetchProjectConfiguration;
import com.publicissapient.kpidashboard.rally.config.RallyProcessorConfig;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.ReadData;
import com.publicissapient.kpidashboard.rally.service.RallyCommonService;

@ExtendWith(MockitoExtension.class)
public class IssueRqlReaderTest {

    @InjectMocks
    private IssueRqlReader issueRqlReader;

    @Mock
    private FetchProjectConfiguration fetchProjectConfiguration;

    @Mock
    private RallyCommonService rallyCommonService;

    @Mock
    private RallyProcessorConfig rallyProcessorConfig;

    @Mock
    private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepo;

    private ProjectConfFieldMapping projectConfFieldMapping;
    private String projectId = "test-project-id";
    private String processorId = new ObjectId().toString();

    @BeforeEach
    public void setup() {
        projectConfFieldMapping = new ProjectConfFieldMapping();
        projectConfFieldMapping.setBasicProjectConfigId(new ObjectId("5f8d0c1e4b3a2b001f8d0c1e"));
        projectConfFieldMapping.setProjectName("Test Project");
        issueRqlReader.projectId = projectId;
        issueRqlReader.processorId = processorId;
    }

    @Test
    void testReadWithNoConfiguration() throws Exception {
        when(fetchProjectConfiguration.fetchConfiguration(projectId)).thenReturn(null);

        ReadData readData = issueRqlReader.read();
        assertNull(readData);
        verify(fetchProjectConfiguration).fetchConfiguration(projectId);
    }

    @Test
    void testReadWithEmptyResults() throws Exception {
        when(fetchProjectConfiguration.fetchConfiguration(projectId)).thenReturn(projectConfFieldMapping);
        when(rallyProcessorConfig.getPageSize()).thenReturn(50);
        when(rallyProcessorConfig.getPrevMonthCountToFetchData()).thenReturn(6);
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdAndProgressStatsFalse(
                RallyConstants.RALLY, projectConfFieldMapping.getBasicProjectConfigId().toString()))
                .thenReturn(Collections.emptyList());
        when(rallyCommonService.fetchIssuesBasedOnJql(any(), anyInt(), anyString()))
                .thenReturn(Collections.emptyList());

        ReadData readData = issueRqlReader.read();
        assertNull(readData);
    }

    @Test
    void testReadWithSinglePage() throws Exception {
        when(fetchProjectConfiguration.fetchConfiguration(projectId)).thenReturn(projectConfFieldMapping);
        when(rallyProcessorConfig.getPageSize()).thenReturn(50);
        when(rallyProcessorConfig.getPrevMonthCountToFetchData()).thenReturn(6);

        ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
        traceLog.setLastSuccessfulRun("2025-05-19");
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdAndProgressStatsFalse(
                RallyConstants.RALLY, projectConfFieldMapping.getBasicProjectConfigId().toString()))
                .thenReturn(Arrays.asList(traceLog));

        HierarchicalRequirement hr = new HierarchicalRequirement();
        hr.setName("HR-1");
        when(rallyCommonService.fetchIssuesBasedOnJql(any(), anyInt(), anyString()))
                .thenReturn(Arrays.asList(hr));

        ReadData readData = issueRqlReader.read();
        assertNotNull(readData);
        assertEquals(hr, readData.getHierarchicalRequirement());
        assertEquals(projectConfFieldMapping, readData.getProjectConfFieldMapping());
        assertEquals(new ObjectId(processorId), readData.getProcessorId());
        assertEquals(false, readData.isSprintFetch());
    }

    @Test
    void testReadMultiplePages() throws Exception {
        when(fetchProjectConfiguration.fetchConfiguration(projectId)).thenReturn(projectConfFieldMapping);
        when(rallyProcessorConfig.getPageSize()).thenReturn(1);
        when(rallyProcessorConfig.getPrevMonthCountToFetchData()).thenReturn(6);

        ProcessorExecutionTraceLog traceLog = new ProcessorExecutionTraceLog();
        traceLog.setLastSuccessfulRun("2025-05-19");
        when(processorExecutionTraceLogRepo.findByProcessorNameAndBasicProjectConfigIdAndProgressStatsFalse(
                RallyConstants.RALLY, projectConfFieldMapping.getBasicProjectConfigId().toString()))
                .thenReturn(Arrays.asList(traceLog));

        HierarchicalRequirement hr1 = new HierarchicalRequirement();
        hr1.setName("HR-1");
        HierarchicalRequirement hr2 = new HierarchicalRequirement();
        hr2.setName("HR-2");

        when(rallyCommonService.fetchIssuesBasedOnJql(any(), eq(0), anyString()))
                .thenReturn(Arrays.asList(hr1));
        when(rallyCommonService.fetchIssuesBasedOnJql(any(), eq(1), anyString()))
                .thenReturn(Arrays.asList(hr2));

        ReadData firstRead = issueRqlReader.read();
        assertNotNull(firstRead);
        assertEquals(hr1, firstRead.getHierarchicalRequirement());

        ReadData secondRead = issueRqlReader.read();
        assertNotNull(secondRead);
        assertEquals(hr2, secondRead.getHierarchicalRequirement());

        ReadData thirdRead = issueRqlReader.read();
        assertNull(thirdRead);
    }
}
