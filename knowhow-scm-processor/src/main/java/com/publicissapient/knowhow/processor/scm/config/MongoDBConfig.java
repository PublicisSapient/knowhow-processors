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
@PropertySource({"classpath:application-${spring.profiles.active}.yml"})
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