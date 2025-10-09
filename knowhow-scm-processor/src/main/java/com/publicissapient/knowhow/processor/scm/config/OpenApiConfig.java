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

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for the Git Metadata Scanner API.
 * 
 * This configuration provides comprehensive API documentation including:
 * - API information and metadata
 * - Server configurations for different environments
 * - Tag definitions for endpoint grouping
 * - Contact and license information
 */
@Configuration
public class  OpenApiConfig {

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${info.app.version:1.0.0-SNAPSHOT}")
    private String appVersion;

    @Value("${info.app.name:Git Metadata Scanner}")
    private String appName;

    @Value("${info.app.description:Multi-platform Git metadata scanner and analyzer}")
    private String appDescription;

    /**
     * Configures the OpenAPI specification for the Git Metadata Scanner API.
     * 
     * @return OpenAPI configuration with comprehensive API documentation
     */
    @Bean
    public OpenAPI gitScannerOpenAPI() {
        return new OpenAPI()
                .info(createApiInfo())
                .servers(createServerList())
                .tags(createTagList());
    }

    /**
     * Creates API information including title, description, version, and contact details.
     */
    private Info createApiInfo() {
        return new Info()
                .title(appName + " API")
                .description(createApiDescription())
                .version(appVersion)
                .contact(createContactInfo())
                .license(createLicenseInfo());
    }

    /**
     * Creates detailed API description with feature overview.
     */
    private String createApiDescription() {
        return appDescription + "\n\n" +
                "## Features\n" +
                "- **Multi-Platform Support**: Scan repositories from GitHub, GitLab, Azure DevOps, and Bitbucket\n" +
                "- **Flexible Scanning**: Both synchronous and asynchronous scanning operations\n" +
                "- **Comprehensive Data**: Collect commits, merge requests, and user information\n" +
                "- **Rate Limiting**: Built-in API rate limit management\n" +
                "- **Health Monitoring**: System health and status endpoints\n\n" +
                "## Supported Platforms\n" +
                "- GitHub\n" +
                "- GitLab\n" +
                "- Azure DevOps (Azure Repos)\n" +
                "- Bitbucket\n\n" +
                "## Authentication\n" +
                "All scanning operations require valid access tokens for the respective Git platforms.";
    }

    /**
     * Creates contact information for API support.
     */
    private Contact createContactInfo() {
        return new Contact()
                .name("Git Scanner Team")
                .email("support@gitscanner.com")
                .url("https://github.com/your-org/git-metadata-scanner");
    }

    /**
     * Creates license information for the API.
     */
    private License createLicenseInfo() {
        return new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0");
    }

    /**
     * Creates server configurations for different environments.
     */
    private List<Server> createServerList() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local Development Server"),
                new Server()
                        .url("https://api-dev.gitscanner.com")
                        .description("Development Server"),
                new Server()
                        .url("https://api.gitscanner.com")
                        .description("Production Server")
        );
    }

    /**
     * Creates tag definitions for organizing API endpoints.
     */
    private List<Tag> createTagList() {
        return List.of(
                new Tag()
                        .name("Repository Scanning")
                        .description("Operations for scanning Git repositories and collecting metadata"),
                new Tag()
                        .name("Health & Monitoring")
                        .description("System health checks and monitoring endpoints"),
                new Tag()
                        .name("Project Management")
                        .description("Project-based scanning and configuration management")
        );
    }
}