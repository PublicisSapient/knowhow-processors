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

package com.publicissapient.kpidashboard.argocd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.client.RestTemplate;

/** ArgoCDProcessorApplication configuration and bootstrap. */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableCaching
@ComponentScan(basePackages = {"com.publicissapient", "com.knowhow.retro.notifications"})
@EnableMongoRepositories(basePackages = "com.publicissapient.**.repository")
public class ArgoCDProcessorApplication {

	/**
	 * Main thread from where ArgoCDProcessorApplication starts.
	 *
	 * @param args
	 *          the command line argument
	 */
	public static void main(String[] args) {
		SpringApplication.run(ArgoCDProcessorApplication.class, args);
	}

	/**
	 * Bean for RestTemplate
	 *
	 * @return RestTemplate
	 */
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
