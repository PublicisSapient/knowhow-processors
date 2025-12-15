package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.config;

import com.publicissapient.kpidashboard.job.config.base.BatchConfig;
import com.publicissapient.kpidashboard.job.config.base.SchedulingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KpiBenchmarkCalculationConfigTest {

    @Mock
    private BatchConfig batchConfig;

    @Mock
    private SchedulingConfig schedulingConfig;

    private KpiBenchmarkCalculationConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new KpiBenchmarkCalculationConfig();
        config.setBatching(batchConfig);
        config.setScheduling(schedulingConfig);
    }

    @Test
    void testValidateConfiguration_WithValidName() {
        config.setName("test-job");
        config.validateConfiguration();
        assertTrue(config.getConfigValidationErrors().isEmpty());
    }

    @Test
    void testValidateConfiguration_WithEmptyName() {
        config.setName("");
        config.validateConfiguration();
        assertTrue(config.getConfigValidationErrors().contains("The job 'name' parameter is required"));
    }

    @Test
    void testValidateConfiguration_WithNullName() {
        config.setName(null);
        config.validateConfiguration();
        assertTrue(config.getConfigValidationErrors().contains("The job 'name' parameter is required"));
    }



    @Test
    void testGetConfigValidationErrors_ReturnsUnmodifiableSet() {
        config.setName("test-job");
        Set<String> errors = config.getConfigValidationErrors();
        assertThrows(UnsupportedOperationException.class, () -> errors.add("new error"));
    }
}