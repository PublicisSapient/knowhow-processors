package com.publicissapient.kpidashboard.job.storyhygienecalculation.config;

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

/** Spring Boot configuration for the story-hygiene-calculation batch job. */
@Data
@Component
@ConfigurationProperties(prefix = "jobs.story-hygiene-calculation")
public class StoryHygieneCalculationJobConfig implements ConfigValidator {

	private final M2MAuthConfig m2MAuthConfig;
	private final AiGatewayConfig aiGatewayConfig;

	private String name;
	private BatchConfig batching;
	private SchedulingConfig scheduling;
	private StoryHygieneJobConfig calculationConfig;
	private Set<String> configValidationErrors = new HashSet<>();

	@Autowired
	public StoryHygieneCalculationJobConfig(
			M2MAuthConfig m2MAuthConfig, AiGatewayConfig aiGatewayConfig) {
		this.m2MAuthConfig = m2MAuthConfig;
		this.aiGatewayConfig = aiGatewayConfig;
	}

	@Override
	public void validateConfiguration() {
		if (StringUtils.isEmpty(name)) {
			configValidationErrors.add("The job 'name' parameter is required");
		}
		if (m2MAuthConfig == null) {
			configValidationErrors.add(
					"M2M authentication configuration is required for AI Gateway access");
		} else {
			if (StringUtils.isEmpty(m2MAuthConfig.getIssuerServiceId())) {
				configValidationErrors.add("M2M auth 'issuerServiceId' is required");
			}
			if (StringUtils.isEmpty(m2MAuthConfig.getSecret())) {
				configValidationErrors.add("M2M auth 'secret' is required");
			}
		}
		if (aiGatewayConfig == null) {
			configValidationErrors.add("AI Gateway configuration is required");
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
		return Collections.unmodifiableSet(configValidationErrors);
	}

	@PostConstruct
	private void retrieveJobConfigValidationErrors() {
		validateConfiguration();
		calculationConfig.validateConfiguration();
		batching.validateConfiguration();
		scheduling.validateConfiguration();

		configValidationErrors.addAll(calculationConfig.getConfigValidationErrors());
		configValidationErrors.addAll(batching.getConfigValidationErrors());
		configValidationErrors.addAll(scheduling.getConfigValidationErrors());
	}
}
