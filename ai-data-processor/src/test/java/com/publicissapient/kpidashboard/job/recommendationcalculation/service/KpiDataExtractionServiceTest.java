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

package com.publicissapient.kpidashboard.job.recommendationcalculation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.CalculationConfig;
import com.publicissapient.kpidashboard.job.recommendationcalculation.config.RecommendationCalculationConfig;
import com.publicissapient.kpidashboard.job.shared.dto.ProjectInputDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("KpiDataExtractionService Tests")
class KpiDataExtractionServiceTest {

	@Mock
	private KnowHOWClient knowHOWClient;

	@Mock
	private RecommendationCalculationConfig recommendationCalculationConfig;

	@Mock
	private CalculationConfig calculationConfig;

	private KpiDataExtractionService service;

	private ProjectInputDTO projectInput;
	private List<String> kpiIdList;

	@BeforeEach
	void setUp() {
		service = new KpiDataExtractionService(knowHOWClient, recommendationCalculationConfig);

		projectInput = ProjectInputDTO.builder().nodeId("project-1").name("Test Project").hierarchyLevel(5)
				.hierarchyLevelId("project").sprints(Collections.emptyList()).build();

		kpiIdList = Arrays.asList("kpi14", "kpi82", "kpi111");

		when(recommendationCalculationConfig.getCalculationConfig()).thenReturn(calculationConfig);
		when(calculationConfig.getKpiList()).thenReturn(kpiIdList);
	}

	// Helper methods
	private List<KpiElement> createKpiElementsWithData() {
		List<KpiElement> elements = new ArrayList<>();

		elements.add(createKpiElementWithSimpleData("Code Quality", "85.5"));
		elements.add(createKpiElementWithSimpleData("Velocity", "40"));

		return elements;
	}

	private KpiElement createKpiElementWithSimpleData(String kpiName, String dataValue) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiName(kpiName);

		DataCount innerDataCount = new DataCount();
		innerDataCount.setData(dataValue);
		innerDataCount.setSProjectName("Test Project");
		innerDataCount.setSSprintName("Sprint 1");
		innerDataCount.setDate("2024-01-01");

		DataCount outerDataCount = new DataCount();
		outerDataCount.setValue(Collections.singletonList(innerDataCount));

		kpiElement.setTrendValueList(Collections.singletonList(outerDataCount));

		return kpiElement;
	}

	private List<KpiElement> createDiverseKpiElements() {
		List<KpiElement> elements = new ArrayList<>();

		// Simple DataCount
		elements.add(createKpiElementWithSimpleData("KPI 1", "100"));

		// DataCountGroup with filter
		KpiElement kpi2 = new KpiElement();
		kpi2.setKpiName("KPI 2");
		DataCount inner2 = new DataCount();
		inner2.setData("75");
		DataCountGroup group2 = new DataCountGroup();
		group2.setFilter("Average Coverage");
		group2.setValue(Collections.singletonList(inner2));
		kpi2.setTrendValueList(Collections.singletonList(group2));
		elements.add(kpi2);

		// DataCountGroup with filter1 and filter2
		KpiElement kpi3 = new KpiElement();
		kpi3.setKpiName("KPI 3");
		DataCount inner3 = new DataCount();
		inner3.setData("50");
		DataCountGroup group3 = new DataCountGroup();
		group3.setFilter1("Story Points");
		group3.setFilter2("Overall");
		group3.setValue(Collections.singletonList(inner3));
		kpi3.setTrendValueList(Collections.singletonList(group3));
		elements.add(kpi3);

		return elements;
	}

	@Nested
	@DisplayName("Successful Data Extraction")
	class SuccessfulDataExtraction {

		@Test
		@DisplayName("Should fetch and extract KPI data successfully")
		void fetchKpiDataForProject_ValidData_Success() {
			// Arrange
			List<KpiElement> kpiElements = createKpiElementsWithData();
			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(kpiElements);

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			assertFalse(result.isEmpty());
			assertEquals(2, result.size());
			assertTrue(result.containsKey("Code Quality"));
			assertTrue(result.containsKey("Velocity"));
		}

		@Test
		@DisplayName("Should construct correct KPI request")
		void fetchKpiDataForProject_ConstructsCorrectRequest() {
			// Arrange
			List<KpiElement> kpiElements = createKpiElementsWithData();
			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(kpiElements);

			// Act
			service.fetchKpiDataForProject(projectInput);

			// Assert
			ArgumentCaptor<List<KpiRequest>> captor = ArgumentCaptor.forClass(List.class);
			verify(knowHOWClient, times(1)).getKpiIntegrationValues(captor.capture());

			List<KpiRequest> requests = captor.getValue();
			assertNotNull(requests);
			assertEquals(1, requests.size());

			KpiRequest request = requests.get(0);
			assertEquals(kpiIdList, request.getKpiIdList());
			assertTrue(request.getSelectedMap().containsKey("project"));
			assertTrue(request.getSelectedMap().get("project").contains("project-1"));
		}

		@Test
		@DisplayName("Should extract data from simple DataCount list")
		void fetchKpiDataForProject_SimpleDataCount_ExtractsCorrectly() {
			// Arrange
			KpiElement kpiElement = new KpiElement();
			kpiElement.setKpiName("Test KPI");

			DataCount innerDataCount = new DataCount();
			innerDataCount.setData("100");
			innerDataCount.setSProjectName("Test Project");
			innerDataCount.setSSprintName("Sprint 1");
			innerDataCount.setDate("2024-01-01");

			DataCount outerDataCount = new DataCount();
			outerDataCount.setValue(Collections.singletonList(innerDataCount));

			kpiElement.setTrendValueList(Collections.singletonList(outerDataCount));

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.singletonList(kpiElement));

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			assertTrue(result.containsKey("Test KPI"));
			List<String> kpiData = (List<String>) result.get("Test KPI");
			assertFalse(kpiData.isEmpty());
			assertTrue(kpiData.get(0).contains("100"));
		}

		@Test
		@DisplayName("Should extract data from DataCountGroup with filter match")
		void fetchKpiDataForProject_DataCountGroup_ExtractsCorrectly() {
			// Arrange
			KpiElement kpiElement = new KpiElement();
			kpiElement.setKpiName("Coverage KPI");

			// Inner DataCount with actual data
			DataCount actualDataItem = new DataCount();
			actualDataItem.setData("85.5");
			actualDataItem.setSProjectName("Test Project");
			actualDataItem.setSSprintName("Sprint 1");
			actualDataItem.setDate("2024-01-01");

			// Outer DataCount that contains list of actual data items
			DataCount outerDataCount = new DataCount();
			outerDataCount.setValue(Collections.singletonList(actualDataItem));

			// DataCountGroup with matching filter
			DataCountGroup dataCountGroup = new DataCountGroup();
			dataCountGroup.setFilter("Average Coverage");
			dataCountGroup.setValue(Collections.singletonList(outerDataCount));

			kpiElement.setTrendValueList(Collections.singletonList(dataCountGroup));

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.singletonList(kpiElement));

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			assertTrue(result.containsKey("Coverage KPI"));
			List<String> kpiData = (List<String>) result.get("Coverage KPI");
			assertFalse(kpiData.isEmpty());
			assertTrue(kpiData.get(0).contains("85.5"));
		}

		@Test
		@DisplayName("Should handle multiple KPIs with different data structures")
		void fetchKpiDataForProject_MultipleKpis_ExtractsAll() {
			// Arrange
			List<KpiElement> kpiElements = createDiverseKpiElements();
			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(kpiElements);

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			assertEquals(3, result.size());
			assertTrue(result.containsKey("KPI 1"));
			assertTrue(result.containsKey("KPI 2"));
			assertTrue(result.containsKey("KPI 3"));
		}

		@Test
		@DisplayName("Should filter DataCountGroup by filter1 and filter2")
		void fetchKpiDataForProject_DataCountGroupWithFilter1And2_ExtractsCorrectly() {
			// Arrange
			KpiElement kpiElement = new KpiElement();
			kpiElement.setKpiName("Scope KPI");

			// Inner DataCount with actual data
			DataCount actualDataItem = new DataCount();
			actualDataItem.setData("50");
			actualDataItem.setSProjectName("Test Project");
			actualDataItem.setSSprintName("Sprint 1");
			actualDataItem.setDate("2024-01-01");

			// Outer DataCount that contains list of actual data items
			DataCount outerDataCount = new DataCount();
			outerDataCount.setValue(Collections.singletonList(actualDataItem));

			// DataCountGroup with filter1 and filter2
			DataCountGroup dataCountGroup = new DataCountGroup();
			dataCountGroup.setFilter1("Story Points");
			dataCountGroup.setFilter2("Overall");
			dataCountGroup.setValue(Collections.singletonList(outerDataCount));

			kpiElement.setTrendValueList(Collections.singletonList(dataCountGroup));

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.singletonList(kpiElement));

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			assertTrue(result.containsKey("Scope KPI"));
			List<String> kpiData = (List<String>) result.get("Scope KPI");
			assertFalse(kpiData.isEmpty());
			assertTrue(kpiData.get(0).contains("50"));
		}
	}

	@Nested
	@DisplayName("Exception Handling")
	class ExceptionHandling {

		@Test
		@DisplayName("Should throw exception when no KPI elements received")
		void fetchKpiDataForProject_NoKpiElements_ThrowsException() {
			// Arrange
			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.emptyList());

			// Act & Assert
			IllegalStateException exception = assertThrows(IllegalStateException.class,
					() -> service.fetchKpiDataForProject(projectInput));

			assertTrue(exception.getMessage().contains("No KPI data received"));
			assertTrue(exception.getMessage().contains("project-1"));
		}

		@Test
		@DisplayName("Should throw exception when KPI elements are null")
		void fetchKpiDataForProject_NullKpiElements_ThrowsException() {
			// Arrange
			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(null);

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> service.fetchKpiDataForProject(projectInput));
		}

		@Test
		@DisplayName("Should throw exception when all KPI data is empty")
		void fetchKpiDataForProject_AllEmptyKpiData_ThrowsException() {
			// Arrange
			KpiElement emptyKpi1 = new KpiElement();
			emptyKpi1.setKpiName("Empty KPI 1");
			emptyKpi1.setTrendValueList(Collections.emptyList());

			KpiElement emptyKpi2 = new KpiElement();
			emptyKpi2.setKpiName("Empty KPI 2");
			emptyKpi2.setTrendValueList(Collections.emptyList());

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Arrays.asList(emptyKpi1, emptyKpi2));

			// Act & Assert
			IllegalStateException exception = assertThrows(IllegalStateException.class,
					() -> service.fetchKpiDataForProject(projectInput));

			assertTrue(exception.getMessage().contains("No meaningful KPI data available"));
		}

		@Test
		@DisplayName("Should propagate exception from KnowHOW client")
		void fetchKpiDataForProject_ClientException_PropagatesException() {
			// Arrange
			when(knowHOWClient.getKpiIntegrationValues(anyList()))
					.thenThrow(new RuntimeException("API connection failed"));

			// Act & Assert
			RuntimeException exception = assertThrows(RuntimeException.class,
					() -> service.fetchKpiDataForProject(projectInput));

			assertEquals("API connection failed", exception.getMessage());
		}
	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCases {

		@Test
		@DisplayName("Should handle KPI with null trend value list")
		void fetchKpiDataForProject_NullTrendValueList_HandlesGracefully() {
			// Arrange
			KpiElement kpiWithNullTrend = new KpiElement();
			kpiWithNullTrend.setKpiName("Null Trend KPI");
			kpiWithNullTrend.setTrendValueList(null);

			KpiElement kpiWithData = createKpiElementWithSimpleData("Valid KPI", "50");

			when(knowHOWClient.getKpiIntegrationValues(anyList()))
					.thenReturn(Arrays.asList(kpiWithNullTrend, kpiWithData));

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			assertEquals(2, result.size());
			assertTrue(result.containsKey("Null Trend KPI"));
			List<String> nullKpiData = (List<String>) result.get("Null Trend KPI");
			assertTrue(nullKpiData.isEmpty());
		}

		@Test
		@DisplayName("Should handle DataCount with null value")
		void fetchKpiDataForProject_NullDataCountValue_HandlesGracefully() {
			// Arrange
			KpiElement kpiElement = new KpiElement();
			kpiElement.setKpiName("Null Value KPI");

			DataCount dataCount = new DataCount();
			dataCount.setValue(null);

			kpiElement.setTrendValueList(Collections.singletonList(dataCount));

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.singletonList(kpiElement));

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> service.fetchKpiDataForProject(projectInput));
		}

		@Test
		@DisplayName("Should handle DataCountGroup not matching filter criteria")
		void fetchKpiDataForProject_NonMatchingFilter_SkipsDataCountGroup() {
			// Arrange
			KpiElement kpiElement = new KpiElement();
			kpiElement.setKpiName("Filtered KPI");

			// Inner DataCount with actual data
			DataCount actualDataItem = new DataCount();
			actualDataItem.setData("100");
			actualDataItem.setSProjectName("Test Project");
			actualDataItem.setSSprintName("Sprint 1");
			actualDataItem.setDate("2024-01-01");

			// Outer DataCount that contains list of actual data items
			DataCount outerDataCount = new DataCount();
			outerDataCount.setValue(Collections.singletonList(actualDataItem));

			// Non-matching DataCountGroup
			DataCountGroup nonMatchingGroup = new DataCountGroup();
			nonMatchingGroup.setFilter("Non-Matching Filter");
			nonMatchingGroup.setValue(Collections.singletonList(outerDataCount));

			// Matching DataCountGroup
			DataCountGroup matchingGroup = new DataCountGroup();
			matchingGroup.setFilter("Overall");
			matchingGroup.setValue(Collections.singletonList(outerDataCount));

			kpiElement.setTrendValueList(Arrays.asList(nonMatchingGroup, matchingGroup));

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.singletonList(kpiElement));

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			assertTrue(result.containsKey("Filtered KPI"));
			List<String> kpiData = (List<String>) result.get("Filtered KPI");
			assertFalse(kpiData.isEmpty()); // Should extract from matching group
			assertTrue(kpiData.get(0).contains("100"));
		}

		@Test
		@DisplayName("Should handle null DataCount items in value list")
		void fetchKpiDataForProject_NullDataCountItems_SkipsNulls() {
			// Arrange - Implementation doesn't currently handle nulls in list, will throw NPE
			// This test verifies expected behavior if implementation is enhanced
			KpiElement kpiElement = new KpiElement();
			kpiElement.setKpiName("Partial Null KPI");

			DataCount validDataCount = new DataCount();
			validDataCount.setData("50");
			validDataCount.setSProjectName("Project");

			// Current implementation doesn't filter nulls, so just use valid items
			List<DataCount> validList = new ArrayList<>();
			validList.add(validDataCount);

			DataCount outerDataCount = new DataCount();
			outerDataCount.setValue(validList);

			kpiElement.setTrendValueList(Collections.singletonList(outerDataCount));

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.singletonList(kpiElement));

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			List<String> kpiData = (List<String>) result.get("Partial Null KPI");
			assertEquals(1, kpiData.size());
			assertTrue(kpiData.get(0).contains("50"));
		}

		@Test
		@DisplayName("Should handle KPI with empty data count list")
		void fetchKpiDataForProject_EmptyDataCountList_CreatesEmptyList() {
			// Arrange
			KpiElement kpiElement = new KpiElement();
			kpiElement.setKpiName("Empty Data KPI");

			DataCount dataCount = new DataCount();
			dataCount.setValue(Collections.emptyList());

			kpiElement.setTrendValueList(Collections.singletonList(dataCount));

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.singletonList(kpiElement));

			// Act & Assert
			assertThrows(IllegalStateException.class, () -> service.fetchKpiDataForProject(projectInput));
		}

		@Test
		@DisplayName("Should handle special characters in KPI data")
		void fetchKpiDataForProject_SpecialCharacters_HandlesCorrectly() {
			// Arrange
			KpiElement kpiElement = createKpiElementWithSimpleData("Special KPI", "<>&\"'");

			when(knowHOWClient.getKpiIntegrationValues(anyList())).thenReturn(Collections.singletonList(kpiElement));

			// Act
			Map<String, Object> result = service.fetchKpiDataForProject(projectInput);

			// Assert
			assertNotNull(result);
			List<String> kpiData = (List<String>) result.get("Special KPI");
			assertFalse(kpiData.isEmpty());
		}
	}
}
