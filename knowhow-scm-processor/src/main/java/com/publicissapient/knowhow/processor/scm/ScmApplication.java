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

package com.publicissapient.knowhow.processor.scm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Git Metadata Scanner.
 * 
 * This Spring Boot application scans Git repositories from various platforms
 * (GitHub, GitLab, Azure DevOps, Bitbucket) and collects metadata including
 * commits, merge requests, and user information.
 * 
 * Features: - Multi-platform Git repository scanning - Async and scheduled
 * scanning - REST API for on-demand scanning - MongoDB persistence with
 * auditing - Caching for performance optimization - Configurable scanning
 * strategies
 * 
 * @author Git Scanner Team
 * @version 1.0.0
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableConfigurationProperties
@EnableAsync
@EnableScheduling
@EnableCaching
@EnableMongoAuditing
@ComponentScan(basePackages = { "com.publicissapient", "com.knowhow.retro.notifications" })
@EnableMongoRepositories(basePackages = { "com.publicissapient.**.repository" })
public class ScmApplication {

	private static final Logger logger = LoggerFactory.getLogger(ScmApplication.class);

	public static void main(String[] args) {
		try {
			logger.info("Starting KnowHOW SCM Application...");

			ConfigurableApplicationContext context = SpringApplication.run(ScmApplication.class, args);

			logger.info("KnowHOW SCM Application started successfully");

			// Log configuration summary
			logConfigurationSummary(context);

		} catch (Exception e) {
			logger.error("Failed to start KnowHOW SCM Application: {}", e.getMessage(), e);
			System.exit(1);
		}
	}

	/**
	 * Logs a summary of key configuration properties.
	 */
	private static void logConfigurationSummary(ConfigurableApplicationContext context) {
		try {
			String mongoHost = context.getEnvironment().getProperty("spring.data.mongodb.host", "localhost");
			String mongoPort = context.getEnvironment().getProperty("spring.data.mongodb.port", "27017");
			String mongoDatabase = context.getEnvironment().getProperty("spring.data.mongodb.database", "git_metadata");

			boolean githubEnabled = Boolean
					.parseBoolean(context.getEnvironment().getProperty("git.platforms.github.enabled", "true"));
			boolean gitlabEnabled = Boolean
					.parseBoolean(context.getEnvironment().getProperty("git.platforms.gitlab.enabled", "true"));
			boolean azureEnabled = Boolean
					.parseBoolean(context.getEnvironment().getProperty("git.platforms.azure.enabled", "true"));
			boolean bitbucketEnabled = Boolean
					.parseBoolean(context.getEnvironment().getProperty("git.platforms.bitbucket.enabled", "true"));

			logger.info("=== Configuration Summary ===");
			logger.info("MongoDB: {}:{}/{}", mongoHost, mongoPort, mongoDatabase);
			logger.info("Platforms - GitHub: {}, GitLab: {}, Azure: {}, Bitbucket: {}", githubEnabled, gitlabEnabled,
					azureEnabled, bitbucketEnabled);
			logger.info("==============================");

		} catch (Exception e) {
			logger.warn("Could not log configuration summary: {}", e.getMessage());
		}
	}
}