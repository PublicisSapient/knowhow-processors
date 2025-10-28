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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.publicissapient.kpidashboard.client.customapi.config.CustomApiClientConfig;
import com.publicissapient.kpidashboard.client.customapi.model.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.model.KpiRequest;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomApiClient {

	private static final int DURATION_BETWEEN_RETRYING_A_CALL_IN_SECONDS = 5;

	private final WebClient.Builder webClientBuilder;

	private final CustomApiClientConfig customApiClientConfig;

	private Semaphore semaphore;

	private WebClient customApiWebClient;

	@PostConstruct
	private void initializeCustomApiClient() {
		this.customApiWebClient = webClientBuilder.defaultHeader("X-Api-Key", customApiClientConfig.getApiKey())
				.baseUrl(customApiClientConfig.getBaseUrl()).build();
		this.semaphore = new Semaphore(customApiClientConfig.getMaxConcurrentCalls());
	}

	public List<KpiElement> getKpiIntegrationValues(List<KpiRequest> kpiRequests) {
		return Flux.fromIterable(kpiRequests).publishOn(Schedulers.boundedElastic()).flatMap(kpiRequest -> {
			try {
				semaphore.acquire();
				return this.customApiWebClient.post().uri("/kpiIntegrationValues").bodyValue(kpiRequest).retrieve()
						.bodyToFlux(KpiElement.class)
						.retryWhen(Retry.backoff(customApiClientConfig.getNumberOfRetries(),
								Duration.ofSeconds(DURATION_BETWEEN_RETRYING_A_CALL_IN_SECONDS)))
						.collectList().doFinally(signalType -> semaphore.release());
			} catch (InterruptedException e) {
				log.error("Could not get kpi integration values for kpiRequest {}", kpiRequest);
				Thread.currentThread().interrupt();
				return Flux.error(e);
			}
		}).flatMapIterable(list -> list).collectList().block();
	}
}
