package com.publicissapient.kpidashboard.job.shared.dto;

import lombok.Builder;

@Builder
public record KpiDataDTO(
		String kpiId,
		String kpiName,
		String chartType,
		String kpiFilter,
		Boolean isPositiveTrend,
		String kpiCategory,
		boolean kanban) {}
