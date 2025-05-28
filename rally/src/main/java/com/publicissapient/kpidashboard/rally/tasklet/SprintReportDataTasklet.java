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

    @Value("#{jobParameters['processorId']}")
    private String processorId;

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
//        log.info("Sprint report job started for the sprint : {}", sprintId);
        ProjectConfFieldMapping projConfFieldMapping = fetchProjectConfiguration.fetchConfiguration(projectId);
            List<SprintDetails> sprintDetailsList = sprintRepository.findByBasicProjectConfigId(projConfFieldMapping.getBasicProjectConfigId());
        List<JiraIssue> jiraIssueList = jiraIssueRepository.findByBasicProjectConfigIdIn(projConfFieldMapping.getBasicProjectConfigId().toString());
        List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = jiraIssueCustomHistoryRepository
                    .findByBasicProjectConfigIdIn(projConfFieldMapping.getBasicProjectConfigId().toString());
            if (CollectionUtils.isNotEmpty(sprintDetailsList)) {
                // filtering the sprint need to update
                sprintDataProcessor.processSprintReportData(sprintDetailsList, jiraIssueCustomHistoryList, jiraIssueList);
                sprintRepository.saveAll(sprintDetailsList);
            }

        return RepeatStatus.FINISHED;
    }
}
