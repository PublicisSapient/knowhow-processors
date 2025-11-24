package com.publicissapient.kpidashboard.job.shareddataservice.dto.mapper;

import com.publicissapient.kpidashboard.job.shareddataservice.dto.PagedAIUsagePerOrgLevel;
import com.publicissapient.kpidashboard.job.shareddataservice.model.AIUsageStatistics;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;


@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AIUsageStatisticsMapper {

    @Mapping(target = "users", ignore = true)
    @Mapping(target = "ingestTimestamp", expression = "java(java.time.Instant.now())")
    AIUsageStatistics toEntity(PagedAIUsagePerOrgLevel pagedAIUsagePerOrgLevel);
}
