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

package com.publicissapient.kpidashboard.client.shareddataservice;

import com.publicissapient.kpidashboard.client.shareddataservice.config.SharedDataServiceConfig;
import com.publicissapient.kpidashboard.job.aiusagestatisticscollector.dto.AIUsagePerOrgLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.net.ConnectException;
import java.time.Duration;

@Slf4j
@Component
public class SharedDataServiceClient {
    private final WebClient webClient;
    private final SharedDataServiceConfig sharedDataServiceConfig;

    private static final String LEVEL_NAME_PARAM = "levelName";
    private static final String API_KEY_HEADER = "x-api-key";

    public SharedDataServiceClient(SharedDataServiceConfig sharedDataServiceConfig) {
        this.sharedDataServiceConfig = sharedDataServiceConfig;

        String baseUrl = sharedDataServiceConfig.getBaseUrl();
        String apiKeyValue = sharedDataServiceConfig.getApiKey();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(API_KEY_HEADER, apiKeyValue)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public AIUsagePerOrgLevel getAIUsageStatsAsync(String levelName) {
        RetryBackoffSpec retrySpec = Retry.backoff(
                sharedDataServiceConfig.getRetryPolicy().getMaxAttempts(),
                Duration.of(sharedDataServiceConfig.getRetryPolicy().getMinBackoffDuration(),
                        sharedDataServiceConfig.getRetryPolicy().getMinBackoffTimeUnit().toChronoUnit()))
                .filter(SharedDataServiceClient::shouldRetry)
                .doBeforeRetry(retrySignal ->
                        log.info("Retry #{} due to {}", retrySignal.totalRetries(), retrySignal.failure().toString()));

        String path = sharedDataServiceConfig.getAiUsageStatisticsEndpoint().getPath();

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam(LEVEL_NAME_PARAM, levelName)
                        .build())
                .retrieve()
                .bodyToMono(AIUsagePerOrgLevel.class)
                .retryWhen(retrySpec)
                .block();
    }

    private static boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof ConnectException;
    }
}
