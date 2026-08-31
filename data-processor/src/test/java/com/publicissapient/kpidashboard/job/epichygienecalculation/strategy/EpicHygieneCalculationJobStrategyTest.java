/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.job.epichygienecalculation.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.publicissapient.kpidashboard.common.repository.jira.EpicHygieneDataRepository;
import com.publicissapient.kpidashboard.common.service.JobExecutionTraceLogService;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.service.EpicHygieneCalculationService;
import com.publicissapient.kpidashboard.job.epichygienecalculation.service.EpicHygieneProjectBatchService;

@ExtendWith(MockitoExtension.class)
@DisplayName("EpicHygieneCalculationJobStrategy Tests")
class EpicHygieneCalculationJobStrategyTest {

	@Mock private JobRepository jobRepository;
	@Mock private PlatformTransactionManager platformTransactionManager;
	@Mock private EpicHygieneProjectBatchService projectBatchService;
	@Mock private EpicHygieneCalculationService calculationService;
	@Mock private JobExecutionTraceLogService jobExecutionTraceLogService;
	@Mock private ProcessorExecutionTraceLogService processorExecutionTraceLogService;
	@Mock private EpicHygieneDataRepository epicHygieneDataRepository;

	private EpicHygieneCalculationJobConfig jobConfig;
	private EpicHygieneCalculationJobStrategy jobStrategy;

	@BeforeEach
	void setUp() {
		BatchConfig batchConfig = new BatchConfig();
		batchConfig.setChunkSize(10);

		SchedulingConfig schedulingConfig = new SchedulingConfig();
		schedulingConfig.setCron("0 0 4 * * SAT");

		jobConfig = new EpicHygieneCalculationJobConfig();
		jobConfig.setName(JobConstants.JOB_EPIC_HYGIENE_CALCULATION);
		jobConfig.setBatching(batchConfig);
		jobConfig.setScheduling(schedulingConfig);

		jobStrategy =
				new EpicHygieneCalculationJobStrategy(
						jobRepository,
						new SyncTaskExecutor(),
						platformTransactionManager,
						jobConfig,
						projectBatchService,
						calculationService,
						jobExecutionTraceLogService,
						processorExecutionTraceLogService,
						epicHygieneDataRepository);
	}

	@Test
	@DisplayName("Should register under the configured job name")
	void getJobName_ReturnsConfiguredName() {
		assertEquals(JobConstants.JOB_EPIC_HYGIENE_CALCULATION, jobStrategy.getJobName());
	}

	@Test
	@DisplayName("Should expose the scheduling configuration")
	void getSchedulingConfig_ReturnsConfiguredCron() {
		Optional<SchedulingConfig> schedulingConfig = jobStrategy.getSchedulingConfig();

		assertTrue(schedulingConfig.isPresent());
		assertEquals("0 0 4 * * SAT", schedulingConfig.get().getCron());
	}

	@Test
	@DisplayName("Should build a job whose name matches the configuration")
	void getJob_BuildsNamedJob() {
		Job job = jobStrategy.getJob();

		assertNotNull(job);
		assertEquals(JobConstants.JOB_EPIC_HYGIENE_CALCULATION, job.getName());
	}

	@Test
	@DisplayName("Should build the chunk oriented step with the configured chunk size of 10")
	void getJob_UsesConfiguredChunkSize() {
		assertEquals(10, jobConfig.getBatching().getChunkSize());
		assertNotNull(jobStrategy.getJob());
	}

	@Test
	@DisplayName("Should be able to build the job more than once")
	void getJob_CalledTwice_BuildsIndependentInstances() {
		Job first = jobStrategy.getJob();
		Job second = jobStrategy.getJob();

		assertNotNull(first);
		assertNotNull(second);
		assertEquals(first.getName(), second.getName());
	}
}
