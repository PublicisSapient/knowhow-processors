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

package com.publicissapient.kpidashboard.job.scheduler;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.job.orchestrator.JobOrchestrator;
import com.publicissapient.kpidashboard.job.registry.AiDataJobRegistry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiDataJobScheduler {

	private final AiDataJobRegistry aiDataJobRegistry;
	private final JobOrchestrator jobOrchestrator;
	private final ThreadPoolTaskScheduler threadPoolTaskScheduler;

	@PostConstruct
	private void scheduleAllJobs() {
		log.info("Initializing all cron jobs...");

		try {
			aiDataJobRegistry.getJobStrategyMap().forEach((jobName, jobStrategy) -> {
				if (jobStrategy.getSchedulingConfig().isPresent()) {
					CronTrigger cronTrigger = new CronTrigger(jobStrategy.getSchedulingConfig().get().getCron());
					threadPoolTaskScheduler.schedule(() -> jobOrchestrator.runJob(jobName), cronTrigger);
				}
			});
		} catch (Exception e) {
			log.error("Encountered the following error while scheduling all cron jobs {}", e.getMessage());
		}

		log.info("Cron job initialization finished");
	}
}
