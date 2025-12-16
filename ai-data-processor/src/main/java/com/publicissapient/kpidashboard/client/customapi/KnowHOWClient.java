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

package com.publicissapient.kpidashboard.client.customapi;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.publicissapient.kpidashboard.client.customapi.config.KnowHOWApiClientConfig;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

@Slf4j
@Component
public class KnowHOWClient {

	private static final int MAX_IN_MEMORY_SIZE_BYTE_COUNT = 10 * 1024 * 1024; // 10MB

	private final KnowHOWApiClientConfig knowHOWApiClientConfig;

	private final Semaphore semaphore;

	private final WebClient knowHOWWebClient;

	public KnowHOWClient(WebClient.Builder webClientBuilder, KnowHOWApiClientConfig knowHOWApiClientConfig) {
		this.knowHOWApiClientConfig = knowHOWApiClientConfig;

		this.knowHOWWebClient = webClientBuilder.defaultHeader("X-Api-Key", knowHOWApiClientConfig.getApiKey())
				.codecs(clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs()
						.maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTE_COUNT))
				.baseUrl(knowHOWApiClientConfig.getBaseUrl()).build();

		this.semaphore = new Semaphore(knowHOWApiClientConfig.getRateLimiting().getMaxConcurrentCalls());
	}

	public List<KpiElement> getKpiIntegrationValues(List<KpiRequest> kpiRequests) {
		return Flux.fromIterable(kpiRequests).publishOn(Schedulers.boundedElastic()).flatMap(kpiRequest -> {
			try {
				semaphore.acquire();
				return this.knowHOWWebClient.post()
						.uri(this.knowHOWApiClientConfig.getKpiIntegrationValuesEndpointConfig().getPath())
						.bodyValue(kpiRequest).retrieve().bodyToFlux(KpiElement.class).retryWhen(retrySpec())
						.collectList().doFinally(signalType -> semaphore.release());
			} catch (InterruptedException e) {
				log.error("Could not get kpi integration values for kpiRequest {}", kpiRequest);
				Thread.currentThread().interrupt();
				return Flux.error(e);
			}
		}).flatMapIterable(list -> list).collectList().block();
	}

	public List<KpiElement> getKpiIntegrationValuesKanban(List<KpiRequest> kpiRequests) {
		return Flux.fromIterable(kpiRequests).publishOn(Schedulers.boundedElastic()).flatMap(kpiRequest -> {
			try {
				semaphore.acquire();
				return this.knowHOWWebClient.post()
						.uri(this.knowHOWApiClientConfig.getKpiIntegrationValuesKanbanEndpointConfig().getPath())
						.bodyValue(kpiRequest).retrieve().bodyToFlux(KpiElement.class).retryWhen(retrySpec())
						.collectList().doFinally(signalType -> semaphore.release());
			} catch (InterruptedException e) {
				log.error("Could not get kpi integration values kanban for kpiRequest {}", kpiRequest);
				Thread.currentThread().interrupt();
				return Flux.error(e);
			}
		}).flatMapIterable(list -> list).collectList().block();
	}

    public void evictKnowHowCache(String cacheName) {
        try {
            semaphore.acquire();
            String path = this.knowHOWApiClientConfig.getKnowHowCacheEvictionEndpointConfig().getPath();
            log.info("Calling cache eviction endpoint: {} with cacheName: {}", path, cacheName);
            this.knowHOWWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path).path(cacheName).build())
                    .retrieve()
                    .bodyToMono(Void.class)
                    .retryWhen(retrySpec())
                    .block();
            log.info("Cache {} cleared successfully", cacheName);
        } catch (InterruptedException e) {
            log.error("Could not clear cache for {}", cacheName);
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
    }

	private RetryBackoffSpec retrySpec() {
		return Retry
				.backoff(knowHOWApiClientConfig.getRetryPolicy().getMaxAttempts(),
						Duration.of(knowHOWApiClientConfig.getRetryPolicy().getMinBackoffDuration(),
								knowHOWApiClientConfig.getRetryPolicy().getMinBackoffTimeUnit().toChronoUnit()))
				.filter(KnowHOWClient::shouldRetry).doBeforeRetry(retrySignal -> log.info("Retry #{} due to {}",
						retrySignal.totalRetries(), retrySignal.failure().toString()));
	}

	private static boolean shouldRetry(Throwable throwable) {
		if (throwable instanceof WebClientResponseException ex) {
			return ex.getStatusCode().is5xxServerError();
		}

		return throwable instanceof ConnectException;
	}
}
