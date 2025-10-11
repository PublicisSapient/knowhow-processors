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

package com.publicissapient.knowhow.processor.scm.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.util.StringUtils;

/**
 * MongoDB configuration for git_metadata database.
 */
@Configuration
@PropertySource({"classpath:application.yml"})
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