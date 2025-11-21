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

package com.publicissapient.kpidashboard.client.customapi.deserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrendValuesListDeserializer extends JsonDeserializer<Object> {

	private final ObjectMapper objectMapper = JsonMapper.builder()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.build();

	@Override
	public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
		List<?> trendValuesList = jsonParser.readValueAs(List.class);

		List<DataCount> dataCountList = new ArrayList<>();
		List<DataCountGroup> dataCountGroupList = new ArrayList<>();

		if (CollectionUtils.isNotEmpty(trendValuesList)) {
			LinkedHashMap<?, ?> linkedHashMap = (LinkedHashMap<?, ?>) trendValuesList.get(0);
			if (linkedHashMap.containsKey("filter") || linkedHashMap.containsKey("filter1")
					|| linkedHashMap.containsKey("filter2")) {
				dataCountGroupList = trendValuesList.stream().map(trendValue -> {
					DataCountGroup dataCountGroup = objectMapper.convertValue(trendValue, DataCountGroup.class);
					List<?> dataCountGroupValues = dataCountGroup.getValue();
					if (CollectionUtils.isNotEmpty(dataCountGroupValues)) {
						dataCountGroup.setValue(convertToDataCountList(dataCountGroupValues));
					}
					return dataCountGroup;
				}).toList();
			} else {
				dataCountList = convertToDataCountList(trendValuesList);
			}
		}
		if (CollectionUtils.isNotEmpty(dataCountList)) {
			return dataCountList;
		}
		if (CollectionUtils.isNotEmpty(dataCountGroupList)) {
			return dataCountGroupList;
		}
		return new Object();
	}

	private List<DataCount> convertToDataCountList(List<?> trendValuesList) {
		return trendValuesList.stream().map(trendValue -> {
			DataCount dataCount = objectMapper.convertValue(trendValue, DataCount.class);
			List<?> dataCountValues = (List<?>) dataCount.getValue();
			if (CollectionUtils.isNotEmpty(dataCountValues)) {
				dataCount.setValue(dataCountValues.stream()
						.map(dataCountValue -> objectMapper.convertValue(dataCountValue, DataCount.class)).toList());
			}
			return dataCount;
		}).toList();
	}
}
