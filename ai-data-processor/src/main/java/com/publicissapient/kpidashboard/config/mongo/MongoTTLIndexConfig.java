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

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Configuration
@RequiredArgsConstructor
public class MongoTTLIndexConfig {

	private static final String TTL_INDEX_NAME_FORMAT = "%s-%s";

	private final MongoTemplate mongoTemplate;
	private final TTLIndexConfigProperties ttlIndexConfigProperties;

	@PostConstruct
	private void initializeTTLIndex() {
		for (Map.Entry<String, TTLIndexConfigProperties.TTLIndexConfig> indexConfigEntry : ttlIndexConfigProperties
				.getConfigs().entrySet()) {
			TTLIndexConfigProperties.TTLIndexConfig ttlIndexConfig = indexConfigEntry.getValue();

			if (ttlIndexAlreadyExists(ttlIndexConfig.getCollectionName(), indexConfigEntry.getKey(),
					ttlIndexConfig.getTtlField())) {
				mongoTemplate.indexOps(ttlIndexConfig.getCollectionName())
						.dropIndex(String.format(TTL_INDEX_NAME_FORMAT, indexConfigEntry.getKey(), ttlIndexConfig.getTtlField()));
			}
			mongoTemplate.indexOps(ttlIndexConfig.getCollectionName())
					.ensureIndex(new Index().on(ttlIndexConfig.getTtlField(), ttlIndexConfig.getSortDirection())
							.named(String.format(TTL_INDEX_NAME_FORMAT, indexConfigEntry.getKey(), ttlIndexConfig.getTtlField()))
							.expire(ttlIndexConfig.getExpiration(), ttlIndexConfig.getTimeUnit()));
		}
	}

	private boolean ttlIndexAlreadyExists(String collectionName, String ttlIndexName, String ttlIndexField) {
		return mongoTemplate.indexOps(collectionName).getIndexInfo().stream().anyMatch(
				indexInfo -> indexInfo.getName().equalsIgnoreCase(String.format(TTL_INDEX_NAME_FORMAT, ttlIndexName, ttlIndexField)));
	}
}
