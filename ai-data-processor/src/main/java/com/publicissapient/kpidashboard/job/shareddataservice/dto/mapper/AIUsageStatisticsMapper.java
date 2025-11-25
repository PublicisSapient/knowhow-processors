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
