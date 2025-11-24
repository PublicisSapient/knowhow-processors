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

package com.publicissapient.kpidashboard.job.shareddataservice.config;

import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Data
@Configuration
@ConfigurationProperties(prefix = "jobs.ai-usage-statistics-collector")
public class AIUsageStatisticsJobConfig implements ConfigValidator {
    private String name;
    private SchedulingConfig scheduling;
    private BatchConfig batching;

    private Set<String> configValidationErrors = new HashSet<>();

    @PostConstruct
    private void retrieveJobConfigValidationErrors() {
        this.validateConfiguration();

        this.batching.validateConfiguration();
        this.scheduling.validateConfiguration();

        this.configValidationErrors = new HashSet<>();
        this.configValidationErrors.addAll(this.batching.getConfigValidationErrors());
        this.configValidationErrors.addAll(this.scheduling.getConfigValidationErrors());
    }

    @Override
    public void validateConfiguration() {
        if(StringUtils.isEmpty(this.name)) {
            configValidationErrors.add("The job 'name' parameter is required");
        }
    }

    @Override
    public Set<String> getConfigValidationErrors() {
        return Collections.unmodifiableSet(this.configValidationErrors);
    }
}
