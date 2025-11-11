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

package com.publicissapient.kpidashboard.client.customapi.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "knowhow-api-config")
public class KnowHOWApiClientConfig {
    private String baseUrl;
    private String apiKey;

    private final RateLimiting rateLimiting = new RateLimiting();

    private final RetryPolicy retryPolicy = new RetryPolicy();

    private final Map<String, EndpointConfig> endpoints = new HashMap<>();

    @Data
    public static class RateLimiting {
        private int maxConcurrentCalls;
    }

    @Data
    public static class RetryPolicy {
        private int maxAttempts;
        private int minBackoffDuration;

        private TimeUnit minBackoffTimeUnit;
    }

    @Data
    public static class EndpointConfig {
        private String path;
    }

    public EndpointConfig getKpiIntegrationValuesEndpointConfig() {
        return this.endpoints.get("kpi-integration-values");
    }
}