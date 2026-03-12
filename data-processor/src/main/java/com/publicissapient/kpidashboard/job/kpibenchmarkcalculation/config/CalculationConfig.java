package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import lombok.Data;

@Data
public class CalculationConfig implements ConfigValidator {

	@Data
	public static class DataPoints {
		private int count;
	}

	private final DataPoints kanbanDataPoints = new DataPoints();

	private static final int MAXIMUM_DATA_POINTS_ALLOWED = 15;

	private final Set<String> configValidationErrors = new HashSet<>();

	@Override
	public void validateConfiguration() {

		if (this.kanbanDataPoints.count < 1
				|| this.kanbanDataPoints.count > MAXIMUM_DATA_POINTS_ALLOWED) {
			configValidationErrors.add(
					"The data points used for kpi maturity calculation must be between 1 and 15");
		}
	}

	@Override
	public Set<String> getConfigValidationErrors() {
		return Collections.unmodifiableSet(this.configValidationErrors);
	}
}
