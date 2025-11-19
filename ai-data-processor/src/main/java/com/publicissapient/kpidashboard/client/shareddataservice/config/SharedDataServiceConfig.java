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

package com.publicissapient.kpidashboard.client.shareddataservice.config;

import com.publicissapient.kpidashboard.client.customapi.config.RetryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "shared-data-service-api-config")
public class SharedDataServiceConfig {
    private String baseUrl;
    private final AIUsageStatisticsEndpoint aiUsageStatisticsEndpoint = new AIUsageStatisticsEndpoint();
    private final RetryPolicy retryPolicy = new RetryPolicy();

    @Data
    public static class AIUsageStatisticsEndpoint {
        private String name;
        private String path;
        private ApiKey apiKey = new ApiKey();
    }

    @Data
    public static class ApiKey {
        private String name;
        private String value;
    }
}
