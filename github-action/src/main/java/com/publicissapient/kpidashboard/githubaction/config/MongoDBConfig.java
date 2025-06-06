/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.githubaction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

@Configuration
@PropertySource({"classpath:application.properties"})
public class MongoDBConfig {

	@Value("${mongodb.connection.atlas}")
	private boolean useAtlasDB;

	@Value("${spring.data.mongodb.uri}")
	private String mongoDBUri;

	@Value("${spring.data.mongodb.atlas.uri}")
	private String atlasUri;

	public String getMongoDBUri() {
		return useAtlasDB ? atlasUri : mongoDBUri;
	}

	@Bean
	public MongoClient mongoClient() {
		return MongoClients.create(getMongoDBUri());
	}
}
