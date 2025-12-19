package com.publicissapient.kpidashboard.job.shared.dto;

import lombok.Builder;
import lombok.Data;

@Builder
public record KpiDataDTO(String kpiId, String kpiName, String chartType, String kpiFilter) {
}
