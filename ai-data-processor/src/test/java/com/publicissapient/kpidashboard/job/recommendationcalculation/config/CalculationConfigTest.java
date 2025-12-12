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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;

class CalculationConfigTest {

	private CalculationConfig calculationConfig;

	@BeforeEach
	void setUp() {
		calculationConfig = new CalculationConfig();
	}

	@Test
	void when_NoEnabledPersonaConfigured_Then_ValidationErrorAdded() {
		// Arrange
		calculationConfig.setEnabledPersona(null);
		calculationConfig.setKpiList(List.of("kpi14", "kpi82"));

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
		assertTrue(errors.contains("No enabled persona configured for recommendation calculation"));
	}

	@Test
	void when_NoKpiListConfigured_Then_ValidationErrorAdded() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		calculationConfig.setKpiList(null);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
		assertTrue(errors.contains("No KPI list configured for recommendation calculation"));
	}

	@Test
	void when_EmptyKpiListConfigured_Then_ValidationErrorAdded() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		calculationConfig.setKpiList(Collections.emptyList());

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertFalse(errors.isEmpty());
		assertTrue(errors.contains("No KPI list configured for recommendation calculation"));
	}

	@Test
	void when_BothPersonaAndKpiListMissing_Then_BothValidationErrorsAdded() {
		// Arrange
		calculationConfig.setEnabledPersona(null);
		calculationConfig.setKpiList(null);

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		assertEquals(2, errors.size());
		assertTrue(errors.contains("No enabled persona configured for recommendation calculation"));
		assertTrue(errors.contains("No KPI list configured for recommendation calculation"));
	}

	@Test
	void when_ValidPersonaAndKpiListConfigured_Then_NoValidationErrors() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		calculationConfig.setKpiList(List.of("kpi14", "kpi82", "kpi111"));

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_ExecutiveSponsorPersonaConfigured_Then_NoValidationErrors() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.EXECUTIVE_SPONSOR);
		calculationConfig.setKpiList(List.of("kpi14", "kpi82"));

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_ScrumMasterPersonaConfigured_Then_NoValidationErrors() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.SCRUM_MASTER);
		calculationConfig.setKpiList(List.of("kpi14", "kpi82"));

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_Complete26KpiListConfigured_Then_NoValidationErrors() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		calculationConfig.setKpiList(List.of(
				"kpi14", "kpi82", "kpi111", "kpi35", "kpi34",
				"kpi37", "kpi28", "kpi36", "kpi126", "kpi42",
				"kpi16", "kpi17", "kpi38", "kpi27", "kpi72",
				"kpi84", "kpi11", "kpi62", "kpi64", "kpi67",
				"kpi65", "kpi157", "kpi158", "kpi116", "kpi118",
				"kpi997"
		));

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_SingleKpiInList_Then_NoValidationErrors() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		calculationConfig.setKpiList(List.of("kpi14"));

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_ValidationErrorsExist_Then_ReturnsUnmodifiableSet() {
		// Arrange
		calculationConfig.setEnabledPersona(null);
		calculationConfig.setKpiList(null);
		calculationConfig.validateConfiguration();

		// Act
		Set<String> errors = calculationConfig.getConfigValidationErrors();

		// Assert
		assertThrows(UnsupportedOperationException.class, () -> {
			errors.add("Should not be able to modify");
		});
	}

	@Test
	void when_GetEnabledPersona_Then_ReturnsConfiguredPersona() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);

		// Act
		Persona result = calculationConfig.getEnabledPersona();

		// Assert
		assertNotNull(result);
		assertEquals(Persona.ENGINEERING_LEAD, result);
	}

	@Test
	void when_GetKpiList_Then_ReturnsConfiguredList() {
		// Arrange
		List<String> kpiList = List.of("kpi14", "kpi82", "kpi111");
		calculationConfig.setKpiList(kpiList);

		// Act
		List<String> result = calculationConfig.getKpiList();

		// Assert
		assertNotNull(result);
		assertEquals(3, result.size());
		assertTrue(result.contains("kpi14"));
		assertTrue(result.contains("kpi82"));
		assertTrue(result.contains("kpi111"));
	}

	@Test
	void when_NoPersonaSet_Then_ReturnsNull() {
		// Arrange - Don't set persona

		// Act
		Persona result = calculationConfig.getEnabledPersona();

		// Assert
		assertNull(result);
	}

	@Test
	void when_NoKpiListSet_Then_ReturnsNull() {
		// Arrange - Don't set KPI list

		// Act
		List<String> result = calculationConfig.getKpiList();

		// Assert
		assertNull(result);
	}

	@Test
	void when_MultipleValidationCallsWithSameErrors_Then_ErrorsNotDuplicated() {
		// Arrange
		calculationConfig.setEnabledPersona(null);
		calculationConfig.setKpiList(null);

		// Act
		calculationConfig.validateConfiguration();
		calculationConfig.validateConfiguration();

		// Assert
		Set<String> errors = calculationConfig.getConfigValidationErrors();
		// Errors should still be only 2 (persona and kpi list), not 4
		assertEquals(2, errors.size());
	}

	@Test
	void when_ConfigurationFixedAfterValidation_Then_ValidationPassesOnRetry() {
		// Arrange
		calculationConfig.setEnabledPersona(null);
		calculationConfig.setKpiList(null);
		calculationConfig.validateConfiguration();
		assertEquals(2, calculationConfig.getConfigValidationErrors().size());

		// Act - Fix configuration
		calculationConfig = new CalculationConfig(); // Reset to clear errors
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		calculationConfig.setKpiList(List.of("kpi14"));
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_AllPersonaEnumValuesUsed_Then_AllValidate() {
		// Test all available persona values
		List<String> kpiList = List.of("kpi14", "kpi82");

		for (Persona persona : Persona.values()) {
			// Arrange
			CalculationConfig config = new CalculationConfig();
			config.setEnabledPersona(persona);
			config.setKpiList(kpiList);

			// Act
			config.validateConfiguration();

			// Assert
			assertTrue(config.getConfigValidationErrors().isEmpty(),
					"Persona " + persona + " should validate successfully");
		}
	}

	@Test
	void when_DuplicateKpisInList_Then_NoValidationErrors() {
		// Arrange - List with duplicates (though not recommended)
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		calculationConfig.setKpiList(List.of("kpi14", "kpi14", "kpi82"));

		// Act
		calculationConfig.validateConfiguration();

		// Assert
		assertTrue(calculationConfig.getConfigValidationErrors().isEmpty());
	}

	@Test
	void when_ConfigValidatorInterfaceImplemented_Then_MethodsAccessible() {
		// Arrange
		calculationConfig.setEnabledPersona(Persona.ENGINEERING_LEAD);
		calculationConfig.setKpiList(List.of("kpi14"));

		// Act
		calculationConfig.validateConfiguration();
		Set<String> errors = calculationConfig.getConfigValidationErrors();

		// Assert - Methods from ConfigValidator interface should be callable
		assertNotNull(errors);
		assertTrue(errors.isEmpty());
	}
}
