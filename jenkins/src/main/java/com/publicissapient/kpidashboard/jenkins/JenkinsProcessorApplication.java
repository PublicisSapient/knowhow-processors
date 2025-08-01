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

package com.publicissapient.kpidashboard.jenkins;

import javax.net.ssl.HttpsURLConnection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.client.RestTemplate;

/** JenkinsProcessorApplication configuration and bootstrap. */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableCaching
@EnableMongoRepositories(basePackages = {"com.publicissapient.**.repository"})
@ComponentScan(basePackages = {"com.publicissapient", "com.knowhow.retro.notifications"})
public class JenkinsProcessorApplication {

	@Value("${jenkins.defaultHostnameVerifier:true}")
	private static boolean defaultHostnameVerifier;

	public static void main(String[] args) {
		HttpsURLConnection.setDefaultHostnameVerifier((s, sslSession) -> defaultHostnameVerifier);
		SpringApplication.run(JenkinsProcessorApplication.class, args);
	}

	/**
	 * Instantiate RestTemplate.
	 *
	 * @return the RestTemplate
	 */
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
