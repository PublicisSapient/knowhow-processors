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

package com.publicissapient.kpidashboard.job.recommendationcalculation.config;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;
import com.publicissapient.kpidashboard.common.service.ProcessorExecutionTraceLogService;
import com.publicissapient.kpidashboard.job.recommendationcalculation.processor.ProjectItemProcessor;
import com.publicissapient.kpidashboard.job.recommendationcalculation.reader.ProjectItemReader;
import com.publicissapient.kpidashboard.job.recommendationcalculation.service.RecommendationCalculationService;
import com.publicissapient.kpidashboard.job.recommendationcalculation.service.RecommendationProjectBatchService;
import com.publicissapient.kpidashboard.job.recommendationcalculation.writer.ProjectItemWriter;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

import lombok.RequiredArgsConstructor;


/**
 * Spring Batch configuration for recommendation calculation job.
 */
@Configuration
@RequiredArgsConstructor
public class RecommendationCalculationBatchConfig {
    
	private final RecommendationProjectBatchService projectBatchService;
    private final RecommendationCalculationService recommendationCalculationService;
    private final ProcessorExecutionTraceLogService processorExecutionTraceLogService;
    private final RecommendationRepository recommendationRepository;
    private final TaskExecutor taskExecutor;
    
    /**
     * Creates ItemReader bean with @StepScope for proper Spring management.
     */
    @Bean
    @StepScope
    public ItemReader<ProjectInputDTO> recommendationProjectItemReader() {
        return new ProjectItemReader(projectBatchService);
    }
    
    /**
     * Creates ItemProcessor bean with @StepScope.
     */
    @Bean
    @StepScope
    public ItemProcessor<ProjectInputDTO, RecommendationsActionPlan> recommendationProjectItemProcessor() {
        return new ProjectItemProcessor(
                recommendationCalculationService,
                processorExecutionTraceLogService
        );
    }
    
    /**
     * Creates ItemWriter bean with @StepScope.
     */
    @Bean
    @StepScope
    public ItemWriter<RecommendationsActionPlan> recommendationProjectItemWriter() {
        return new ProjectItemWriter(
                recommendationRepository,
                processorExecutionTraceLogService
        );
    }
    
    /**
     * Creates async processor wrapper as Spring bean.
     */
    @Bean
    public AsyncItemProcessor<ProjectInputDTO, RecommendationsActionPlan> recommendationAsyncProjectProcessor() {
        AsyncItemProcessor<ProjectInputDTO, RecommendationsActionPlan> asyncItemProcessor = 
                new AsyncItemProcessor<>();
        asyncItemProcessor.setDelegate(recommendationProjectItemProcessor());
        asyncItemProcessor.setTaskExecutor(taskExecutor);
        return asyncItemProcessor;
    }
    
    /**
     * Creates async writer wrapper as Spring bean.
     */
    @Bean
    public AsyncItemWriter<RecommendationsActionPlan> recommendationAsyncItemWriter() {
        AsyncItemWriter<RecommendationsActionPlan> writer = new AsyncItemWriter<>();
        writer.setDelegate(recommendationProjectItemWriter());
        return writer;
    }
}