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

package com.publicissapient.kpidashboard.job.shareddataservice.strategy;

import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogServiceImpl;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.shareddataservice.config.AIUsageStatisticsJobConfig;
import com.publicissapient.kpidashboard.job.shareddataservice.dto.PagedAIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.shareddataservice.listener.AIUsageStatisticsJobCompletionListener;
import com.publicissapient.kpidashboard.job.shareddataservice.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.job.shareddataservice.processor.AccountItemProcessor;
import com.publicissapient.kpidashboard.job.shareddataservice.reader.AccountItemReader;
import com.publicissapient.kpidashboard.job.shareddataservice.service.AIUsageStatisticsService;
import com.publicissapient.kpidashboard.job.shareddataservice.service.AccountBatchService;
import com.publicissapient.kpidashboard.job.shareddataservice.writer.AccountItemWriter;
import com.publicissapient.kpidashboard.job.strategy.JobStrategy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.concurrent.Future;

@Slf4j
@Component
@AllArgsConstructor
public class AIUsageStatisticsJobStrategy implements JobStrategy {

    private final AIUsageStatisticsJobConfig aiUsageStatisticsJobConfig;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AIUsageStatisticsService aiUsageStatisticsService;
    private final ProcessorExecutionTraceLogServiceImpl processorExecutionTraceLogServiceImpl;
    private final AccountBatchService accountBatchService;
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public String getJobName() {
        return aiUsageStatisticsJobConfig.getName();
    }

    @Override
    public Job getJob() {
        Step startStep = chunkProcessAIUsageStatisticsForAccounts();
        AIUsageStatisticsJobCompletionListener jobListener = new AIUsageStatisticsJobCompletionListener(
                this.accountBatchService, this.processorExecutionTraceLogServiceImpl);
        return new JobBuilder(aiUsageStatisticsJobConfig.getName(), jobRepository)
                .start(startStep)
                .listener(jobListener)
                .build();
    }

    @Override
    public Optional<SchedulingConfig> getSchedulingConfig() {
        return Optional.of(aiUsageStatisticsJobConfig.getScheduling());
    }

    private Step chunkProcessAIUsageStatisticsForAccounts() {

        return new StepBuilder("process-ai-usage-statistics", jobRepository)
                .<PagedAIUsagePerOrgLevel, Future<AIUsageStatistics>>chunk(
                        aiUsageStatisticsJobConfig.getBatching().getChunkSize(), transactionManager)
                .reader(new AccountItemReader(accountBatchService))
                .processor(asyncAccountProcessor())
                .writer(asyncItemWriter())
                .build();
    }

    private AsyncItemProcessor<PagedAIUsagePerOrgLevel, AIUsageStatistics> asyncAccountProcessor() {
        AsyncItemProcessor<PagedAIUsagePerOrgLevel, AIUsageStatistics> asyncItemProcessor = new AsyncItemProcessor<>();
        asyncItemProcessor.setDelegate(new AccountItemProcessor(this.aiUsageStatisticsService));
        asyncItemProcessor.setTaskExecutor(threadPoolTaskExecutor);
        return asyncItemProcessor;
    }

    private AsyncItemWriter<AIUsageStatistics> asyncItemWriter() {
        AsyncItemWriter<AIUsageStatistics> writer = new AsyncItemWriter<>();
        writer.setDelegate(new AccountItemWriter(this.aiUsageStatisticsService));
        return writer;
    }
}
