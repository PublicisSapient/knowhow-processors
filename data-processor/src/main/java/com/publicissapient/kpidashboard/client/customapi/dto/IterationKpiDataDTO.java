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

package com.publicissapient.kpidashboard.client.customapi.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Label/value shaped {@code trendValueList} entry.
 *
 * <p>Some KPIs (e.g. Epic Hygiene - kpi312) are not sprint scoped and therefore publish a flat list
 * of labelled scalars instead of the usual {@code DataCount} / {@code DataCountGroup} trend line.
 * Those entries would lose their {@code label} if they were mapped onto {@code DataCount}, which is
 * why they get their own DTO.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IterationKpiDataDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String label;
	private Double value;
	private Double value1;
	private String labelInfo;
	private String unit;
	private String unit1;
}
