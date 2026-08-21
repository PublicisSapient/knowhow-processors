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

package com.publicissapient.kpidashboard.job.epichygienecalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiElement;
import com.publicissapient.kpidashboard.client.customapi.dto.KpiRequest;
import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneCalculationJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.config.EpicHygieneJobConfig;
import com.publicissapient.kpidashboard.job.epichygienecalculation.exception.EpicHygieneKpiUnavailableException;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("EpicHygieneKpiClientService Tests")
class EpicHygieneKpiClientServiceTest {

	@Mock private KnowHOWClient knowHOWClient;

	private EpicHygieneCalculationJobConfig jobConfig;
	private EpicHygieneKpiClientService kpiClientService;
	private ProjectInputDTO projectInput;

	@BeforeEach
	void setUp() {
		EpicHygieneJobConfig calculationConfig = new EpicHygieneJobConfig();
		calculationConfig.setKpiId("kpi312");
		calculationConfig.setMaxRetryAttempts(3);
		// Keep the tests fast — the back-off logic itself is covered separately.
		calculationConfig.setRetryBackoffMillis(0L);

		BatchConfig batchConfig = new BatchConfig();
		batchConfig.setChunkSize(10);

		jobConfig = new EpicHygieneCalculationJobConfig();
		jobConfig.setName("epic-hygiene-calculation");
		jobConfig.setBatching(batchConfig);
		jobConfig.setCalculationConfig(calculationConfig);

		kpiClientService = new EpicHygieneKpiClientService(knowHOWClient, jobConfig);

		projectInput =
				ProjectInputDTO.builder()
						.nodeId("node-1")
						.name("Test Project")
						.hierarchyLevel(5)
						.hierarchyLevelId("project")
						.basicProjectConfigId("project-1")
						.sprints(Collections.emptyList())
						.build();
	}

	private KpiElement epicHygieneElement() {
		return KpiElement.builder().kpiId("kpi312").kpiName("Epic Hygiene").build();
	}

	@Nested
	@DisplayName("Happy path")
	class HappyPath {

		@Test
		@DisplayName("Should return KPI elements on the first successful attempt")
		void fetchEpicHygieneKpi_FirstAttemptSucceeds_ReturnsElements() {
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenReturn(List.of(epicHygieneElement()));

			List<KpiElement> result = kpiClientService.fetchEpicHygieneKpi(projectInput);

			assertNotNull(result);
			assertEquals(1, result.size());
			assertEquals("kpi312", result.get(0).getKpiId());
			verify(knowHOWClient, times(1)).getKpiIntegrationValuesSync(anyList());
		}

		@Test
		@DisplayName("Should build the KPI request from the project hierarchy")
		@SuppressWarnings("unchecked")
		void fetchEpicHygieneKpi_BuildsRequestFromProject() {
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenReturn(List.of(epicHygieneElement()));

			kpiClientService.fetchEpicHygieneKpi(projectInput);

			ArgumentCaptor<List<KpiRequest>> captor = ArgumentCaptor.forClass(List.class);
			verify(knowHOWClient).getKpiIntegrationValuesSync(captor.capture());

			KpiRequest request = captor.getValue().get(0);
			assertEquals(List.of("kpi312"), request.getKpiIdList());
			assertEquals(5, request.getLevel());
			assertEquals("project", request.getLabel());
			assertNotNull(request.getIds());
			assertEquals(1, request.getIds().length);
			assertEquals("node-1", request.getIds()[0]);
			assertTrue(request.getSelectedMap().containsValue(List.of("node-1")));
		}
	}

	@Nested
	@DisplayName("Retry behaviour")
	class RetryBehaviour {

		@Test
		@DisplayName("Should retry and succeed on the second attempt")
		void fetchEpicHygieneKpi_FailsThenSucceeds_ReturnsElements() {
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenThrow(new RuntimeException("gateway timeout"))
					.thenReturn(List.of(epicHygieneElement()));

			List<KpiElement> result = kpiClientService.fetchEpicHygieneKpi(projectInput);

			assertEquals(1, result.size());
			verify(knowHOWClient, times(2)).getKpiIntegrationValuesSync(anyList());
		}

		@Test
		@DisplayName("Should exhaust every configured attempt before giving up")
		void fetchEpicHygieneKpi_AllAttemptsFail_ThrowsAfterMaxAttempts() {
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenThrow(new RuntimeException("503 service unavailable"));

			EpicHygieneKpiUnavailableException exception =
					assertThrows(
							EpicHygieneKpiUnavailableException.class,
							() -> kpiClientService.fetchEpicHygieneKpi(projectInput));

			verify(knowHOWClient, times(3)).getKpiIntegrationValuesSync(anyList());
			assertTrue(exception.getMessage().contains("project-1"));
			assertTrue(exception.getMessage().contains("3 attempt(s)"));
			assertTrue(exception.getMessage().contains("503 service unavailable"));
			assertNotNull(exception.getCause());
		}

		@Test
		@DisplayName("Should treat an empty response as a retryable failure")
		void fetchEpicHygieneKpi_EmptyResponse_RetriesThenThrows() {
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenReturn(Collections.emptyList());

			assertThrows(
					EpicHygieneKpiUnavailableException.class,
					() -> kpiClientService.fetchEpicHygieneKpi(projectInput));

			verify(knowHOWClient, times(3)).getKpiIntegrationValuesSync(anyList());
		}

		@Test
		@DisplayName("Should recover when only the empty responses precede a valid one")
		void fetchEpicHygieneKpi_EmptyThenValid_ReturnsElements() {
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenReturn(Collections.emptyList())
					.thenReturn(List.of(epicHygieneElement()));

			assertEquals(1, kpiClientService.fetchEpicHygieneKpi(projectInput).size());
			verify(knowHOWClient, times(2)).getKpiIntegrationValuesSync(anyList());
		}

		@Test
		@DisplayName("Should call the KPI exactly once when retries are disabled")
		void fetchEpicHygieneKpi_SingleAttemptConfigured_CallsOnce() {
			jobConfig.getCalculationConfig().setMaxRetryAttempts(1);
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenThrow(new RuntimeException("boom"));

			assertThrows(
					EpicHygieneKpiUnavailableException.class,
					() -> kpiClientService.fetchEpicHygieneKpi(projectInput));

			verify(knowHOWClient, times(1)).getKpiIntegrationValuesSync(anyList());
		}

		@Test
		@DisplayName("Should fall back to a single attempt when the configured value is invalid")
		void fetchEpicHygieneKpi_NonPositiveAttempts_CallsOnce() {
			jobConfig.getCalculationConfig().setMaxRetryAttempts(0);
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenThrow(new RuntimeException("boom"));

			assertThrows(
					EpicHygieneKpiUnavailableException.class,
					() -> kpiClientService.fetchEpicHygieneKpi(projectInput));

			verify(knowHOWClient, times(1)).getKpiIntegrationValuesSync(anyList());
		}

		@Test
		@DisplayName("Should honour a positive back-off between attempts")
		void fetchEpicHygieneKpi_WithBackoff_WaitsBetweenAttempts() {
			jobConfig.getCalculationConfig().setMaxRetryAttempts(2);
			jobConfig.getCalculationConfig().setRetryBackoffMillis(30L);
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenThrow(new RuntimeException("boom"));

			long startedAt = System.currentTimeMillis();
			assertThrows(
					EpicHygieneKpiUnavailableException.class,
					() -> kpiClientService.fetchEpicHygieneKpi(projectInput));
			long elapsed = System.currentTimeMillis() - startedAt;

			verify(knowHOWClient, times(2)).getKpiIntegrationValuesSync(anyList());
			assertTrue(elapsed >= 30L, "Expected the back-off to delay the retry, elapsed=" + elapsed);
		}

		@Test
		@DisplayName("Should tolerate a negative back-off configuration")
		void fetchEpicHygieneKpi_NegativeBackoff_StillRetries() {
			jobConfig.getCalculationConfig().setRetryBackoffMillis(-100L);
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenThrow(new RuntimeException("boom"))
					.thenReturn(List.of(epicHygieneElement()));

			assertEquals(1, kpiClientService.fetchEpicHygieneKpi(projectInput).size());
		}
	}

	@Nested
	@DisplayName("Edge cases")
	class EdgeCases {

		@Test
		@DisplayName("Should reject a null project")
		void fetchEpicHygieneKpi_NullProject_Throws() {
			assertThrows(
					IllegalArgumentException.class, () -> kpiClientService.fetchEpicHygieneKpi(null));
		}

		@Test
		@DisplayName("Should stop retrying when the worker thread gets interrupted")
		void fetchEpicHygieneKpi_InterruptedDuringBackoff_StopsEarly() throws Exception {
			jobConfig.getCalculationConfig().setMaxRetryAttempts(3);
			jobConfig.getCalculationConfig().setRetryBackoffMillis(2000L);
			when(knowHOWClient.getKpiIntegrationValuesSync(anyList()))
					.thenThrow(new RuntimeException("boom"));

			Thread worker =
					new Thread(
							() ->
									assertThrows(
											EpicHygieneKpiUnavailableException.class,
											() -> kpiClientService.fetchEpicHygieneKpi(projectInput)));
			worker.start();
			Thread.sleep(150L);
			worker.interrupt();
			worker.join(3000L);

			assertFalse(
					worker.isAlive(), "Worker should have aborted instead of sleeping the full backoff");
			verify(knowHOWClient, times(1)).getKpiIntegrationValuesSync(anyList());
		}

		@Test
		@DisplayName("Should propagate a wrapped failure message even for null messages")
		void fetchEpicHygieneKpi_ExceptionWithoutMessage_StillReported() {
			when(knowHOWClient.getKpiIntegrationValuesSync(any())).thenThrow(new IllegalStateException());

			EpicHygieneKpiUnavailableException exception =
					assertThrows(
							EpicHygieneKpiUnavailableException.class,
							() -> kpiClientService.fetchEpicHygieneKpi(projectInput));

			assertTrue(exception.getMessage().contains("project-1"));
		}
	}
}
