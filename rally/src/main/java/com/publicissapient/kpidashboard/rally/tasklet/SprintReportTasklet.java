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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.rally.aspect.TrackExecutionTime;
import com.publicissapient.kpidashboard.rally.config.FetchProjectConfiguration;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.service.FetchSprintReport;
import com.publicissapient.kpidashboard.rally.service.RallyClientService;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * @author girpatha
 */
@Slf4j
@Component
@StepScope
public class SprintReportTasklet implements Tasklet {

	@Autowired
	FetchProjectConfiguration fetchProjectConfiguration;

	@Autowired
	private FetchSprintReport fetchSprintReport;

	@Autowired
	private SprintRepository sprintRepository;

	@Value("#{jobParameters['sprintId']}")
	private String sprintId;

	@Value("#{jobParameters['processorId']}")
	private String processorId;

	/**
	 * @param sc
	 *          StepContribution
	 * @param cc
	 *          ChunkContext
	 * @return RepeatStatus
	 * @throws Exception
	 *           Exception
	 */
	@TrackExecutionTime
	@Override
	public RepeatStatus execute(StepContribution sc, ChunkContext cc) throws Exception {
		log.info("Sprint report job started for the sprint : {}", sprintId);
		ProjectConfFieldMapping projConfFieldMapping = fetchProjectConfiguration
				.fetchConfigurationBasedOnSprintId(sprintId);
		SprintDetails sprintDetails = sprintRepository.findBySprintID(sprintId);
		List<String> originalBoardIds = sprintDetails.getOriginBoardId();
		for (String boardId : originalBoardIds) {
			List<SprintDetails> sprintDetailsList = fetchSprintReport.getSprints(projConfFieldMapping, boardId);
			if (CollectionUtils.isNotEmpty(sprintDetailsList)) {
				// filtering the sprint need to update
				Set<SprintDetails> sprintDetailSet = sprintDetailsList.stream()
						.filter(s -> s.getSprintID().equalsIgnoreCase(sprintId)).collect(Collectors.toSet());
				Set<SprintDetails> setOfSprintDetails = fetchSprintReport.fetchSprints(projConfFieldMapping, sprintDetailSet,
						 true, new ObjectId(processorId));
				sprintRepository.saveAll(setOfSprintDetails);
			}
		}
		return RepeatStatus.FINISHED;
	}
}
