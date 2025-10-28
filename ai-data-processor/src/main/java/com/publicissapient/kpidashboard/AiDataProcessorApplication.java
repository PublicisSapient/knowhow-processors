package com.publicissapient.kpidashboard;

import javax.net.ssl.HttpsURLConnection;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableCaching
@ComponentScan(basePackages = {"com.publicissapient", "com.knowhow.retro.notifications"})
@EnableMongoRepositories(basePackages = {"com.publicissapient.**.repository"})
@EnableBatchProcessing
@EnableAsync
@EnableScheduling
public class AiDataProcessorApplication {
    private static final boolean SSL_HOST_NAME_FLAG = true;

    public static void main(String[] args) {
        HttpsURLConnection.setDefaultHostnameVerifier((s, sslSession) -> SSL_HOST_NAME_FLAG);
        SpringApplication.run(AiDataProcessorApplication.class, args);
    }
}