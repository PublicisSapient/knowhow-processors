package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.config;

import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Data
@Configuration
@ConfigurationProperties(prefix = "jobs.kpi-benchmark-calculation")
public class KpiBenchmarkCalculationConfig implements ConfigValidator {

    private String name;

    private BatchConfig batching;
    private SchedulingConfig scheduling;

    private Set<String> configValidationErrors = new HashSet<>();

    @PostConstruct
    private void retrieveJobConfigValidationErrors() {
        this.validateConfiguration();

        this.batching.validateConfiguration();
        this.scheduling.validateConfiguration();

        this.configValidationErrors.addAll(this.batching.getConfigValidationErrors());
        this.configValidationErrors.addAll(this.scheduling.getConfigValidationErrors());
    }

    @Override
    public void validateConfiguration() {
        if(StringUtils.isEmpty(this.name)) {
            configValidationErrors.add("The job 'name' parameter is required");
        }
    }

    @Override
    public Set<String> getConfigValidationErrors() {
        return Collections.unmodifiableSet(this.configValidationErrors);
    }

}
