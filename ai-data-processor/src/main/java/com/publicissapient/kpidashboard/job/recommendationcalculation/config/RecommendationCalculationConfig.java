/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.job.recommendationcalculation.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.knowhow.retro.aigatewayclient.client.config.AiGatewayConfig;
import com.knowhow.retro.aigatewayclient.m2mauth.config.M2MAuthConfig;
import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import jakarta.annotation.PostConstruct;
import lombok.Data;

/**
 * Main configuration class for recommendation calculation job.
 */
@Data
@Component
@ConfigurationProperties(prefix = "jobs.recommendation-calculation")
public class RecommendationCalculationConfig implements ConfigValidator {

	private final M2MAuthConfig m2MAuthConfig;
	private final AiGatewayConfig aiGatewayConfig;
	private String name;
	private BatchConfig batching;
	private SchedulingConfig scheduling;
	private CalculationConfig calculationConfig;
	private Set<String> configValidationErrors = new HashSet<>();

	@Autowired
	public RecommendationCalculationConfig(M2MAuthConfig m2MAuthConfig, AiGatewayConfig aiGatewayConfig) {
		this.m2MAuthConfig = m2MAuthConfig;
		this.aiGatewayConfig = aiGatewayConfig;
	}

	@Override
	public void validateConfiguration() {
		if (StringUtils.isEmpty(this.name)) {
			configValidationErrors.add("The job 'name' parameter is required");
		}

		// Validate M2M Auth configuration
		if (m2MAuthConfig == null) {
			configValidationErrors.add("M2M authentication configuration is required for AI Gateway access");
		} else {
			if (StringUtils.isEmpty(m2MAuthConfig.getIssuerServiceId())) {
				configValidationErrors.add("M2M auth 'issuerServiceId' is required");
			}
			if (StringUtils.isEmpty(m2MAuthConfig.getSecret())) {
				configValidationErrors.add("M2M auth 'secret' is required");
			}
		}

		// Validate AI Gateway configuration
		if (aiGatewayConfig == null) {
			configValidationErrors.add("AI Gateway configuration is required for recommendation calculation");
		} else {
			if (StringUtils.isEmpty(aiGatewayConfig.getBaseUrl())) {
				configValidationErrors.add("AI Gateway 'baseUrl' is required");
			}
			if (StringUtils.isEmpty(aiGatewayConfig.getAudience())) {
				configValidationErrors.add("AI Gateway 'audience' is required");
			}
		}
	}

	@Override
	public Set<String> getConfigValidationErrors() {
		return Collections.unmodifiableSet(this.configValidationErrors);
	}

	@PostConstruct
	private void retrieveJobConfigValidationErrors() {
		this.validateConfiguration();

		this.calculationConfig.validateConfiguration();
		this.batching.validateConfiguration();
		this.scheduling.validateConfiguration();

		this.configValidationErrors.addAll(this.calculationConfig.getConfigValidationErrors());
		this.configValidationErrors.addAll(this.batching.getConfigValidationErrors());
		this.configValidationErrors.addAll(this.scheduling.getConfigValidationErrors());
	}
}
