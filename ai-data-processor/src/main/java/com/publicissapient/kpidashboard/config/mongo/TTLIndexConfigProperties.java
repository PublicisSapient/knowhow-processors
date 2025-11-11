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

package com.publicissapient.kpidashboard.config.mongo;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "mongo.ttl-index")
public class TTLIndexConfigProperties {

	private Map<String, TTLIndexConfig> configs;

	@Data
	public static class TTLIndexConfig {
		private int expiration;

		private String collectionName;
		private String ttlField;

		private TimeUnit timeUnit;

		private Sort.Direction sortDirection;
	}
}
