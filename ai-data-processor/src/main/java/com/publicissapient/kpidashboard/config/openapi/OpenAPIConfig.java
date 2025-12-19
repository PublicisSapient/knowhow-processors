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

package com.publicissapient.kpidashboard.config.openapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class OpenAPIConfig {

	@Value("${info.app.version}")
	private String appVersion;

	@Value("${info.app.name}")
	private String appName;

	@Value("${info.app.description:Multi-platform Git metadata scanner and analyzer}")
	private String appDescription;

	@Bean
	public OpenAPI gitScannerOpenAPI() {
		return new OpenAPI().info(createApiInfo());
	}

	private Info createApiInfo() {
		return new Info().title(appName + " API").description(createApiDescription()).version(appVersion)
				.license(createLicenseInfo());
	}

	private String createApiDescription() {
		return appDescription;
	}

	private static License createLicenseInfo() {
		return new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0");
	}
}
