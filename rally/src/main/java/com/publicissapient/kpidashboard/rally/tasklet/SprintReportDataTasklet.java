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

package com.publicissapient.kpidashboard.rally.tasklet;

import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;

import com.publicissapient.kpidashboard.rally.aspect.TrackExecutionTime;
import com.publicissapient.kpidashboard.rally.config.FetchProjectConfiguration;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.processor.SprintDataProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@StepScope
public class SprintReportDataTasklet implements Tasklet {
    @Autowired
    FetchProjectConfiguration fetchProjectConfiguration;

    @Autowired
    private SprintDataProcessor sprintDataProcessor;

    @Autowired
    private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

    @Autowired
    private JiraIssueRepository jiraIssueRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Value("#{jobParameters['projectId']}")
    private String projectId;

    /**
     * @param sc
     *          StepContribution
     * @param cc
     *          ChunkContext
     * @return RepeatStatus9/;l
     * @throws Exception
     *           Exception
     */
    @TrackExecutionTime
    @Override
    public RepeatStatus execute(StepContribution sc, ChunkContext cc) throws Exception {
        log.info("Sprint report job started for the project : {}", projectId);
        ProjectConfFieldMapping projConfFieldMapping = fetchProjectConfiguration.fetchConfiguration(projectId);
            List<SprintDetails> sprintDetailsList = sprintRepository.findByBasicProjectConfigId(projConfFieldMapping.getBasicProjectConfigId());
        List<JiraIssue> jiraIssueList = jiraIssueRepository.findByBasicProjectConfigIdIn(projConfFieldMapping.getBasicProjectConfigId().toString());
        List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = jiraIssueCustomHistoryRepository
                    .findByBasicProjectConfigIdIn(projConfFieldMapping.getBasicProjectConfigId().toString());
            if (CollectionUtils.isNotEmpty(sprintDetailsList)) {
                sprintDataProcessor.processSprintReportData(sprintDetailsList, jiraIssueCustomHistoryList, jiraIssueList);
                sprintRepository.saveAll(sprintDetailsList);
            }

        return RepeatStatus.FINISHED;
    }
}
