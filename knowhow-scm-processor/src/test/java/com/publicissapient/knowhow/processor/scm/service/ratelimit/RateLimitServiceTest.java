package com.publicissapient.knowhow.processor.scm.service.ratelimit;

import com.publicissapient.knowhow.processor.scm.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock(lenient = true)
    private RateLimitMonitor gitHubMonitor;

    @Mock(lenient = true)
    private RateLimitMonitor gitLabMonitor;

    private RateLimitService rateLimitService;

    private static final String TEST_TOKEN = "test-token";
    private static final String TEST_REPO = "test-repo";
    private static final String TEST_BASE_URL = "https://api.github.com";

    @BeforeEach
    void setUp() {
        when(gitHubMonitor.getPlatformName()).thenReturn("GitHub");
        when(gitLabMonitor.getPlatformName()).thenReturn("GitLab");
        when(gitHubMonitor.getDefaultThreshold()).thenReturn(0.8);
        when(gitLabMonitor.getDefaultThreshold()).thenReturn(0.8);

        rateLimitService = new RateLimitService(Arrays.asList(gitHubMonitor, gitLabMonitor));
        ReflectionTestUtils.setField(rateLimitService, "defaultThreshold", 0.8);
        ReflectionTestUtils.setField(rateLimitService, "rateLimitCheckEnabled", true);
        ReflectionTestUtils.setField(rateLimitService, "maxCooldownHours", 24);
        ReflectionTestUtils.setField(rateLimitService, "failOnExcessiveCooldown", false);
    }

    @Test
    void testCheckRateLimit_WithDisabledCheck_DoesNothing() throws IOException {
        ReflectionTestUtils.setField(rateLimitService, "rateLimitCheckEnabled", false);

        rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor, never()).checkRateLimit(anyString(), anyString());
    }

    @Test
    void testCheckRateLimit_WithNullToken_DoesNothing() throws IOException {
        rateLimitService.checkRateLimit("GitHub", null, TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor, never()).checkRateLimit(anyString(), anyString());
    }

    @Test
    void testCheckRateLimit_WithEmptyToken_DoesNothing() throws IOException {
        rateLimitService.checkRateLimit("GitHub", "  ", TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor, never()).checkRateLimit(anyString(), anyString());
    }

    @Test
    void testCheckRateLimit_WithUnknownPlatform_DoesNothing() throws IOException {
        rateLimitService.checkRateLimit("Unknown", TEST_TOKEN, TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor, never()).checkRateLimit(anyString(), anyString());
        verify(gitLabMonitor, never()).checkRateLimit(anyString(), anyString());
    }

    @Test
    void testCheckRateLimit_BelowThreshold_Success() throws IOException {
        RateLimitStatus status = new RateLimitStatus("GitHub", 4000, 5000, System.currentTimeMillis() + 3600000, 1000);
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor).checkRateLimit(TEST_TOKEN, TEST_BASE_URL);
    }

    @Test
    void testCheckRateLimit_ExceptionThrown_DoesNotFail() throws IOException {
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenThrow(new IOException("API Error"));

        assertDoesNotThrow(() -> rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL));
    }

    @Test
    void testCheckRateLimit_ExceedsThreshold_WithNegativeWaitTime() throws IOException {
        long pastTime = System.currentTimeMillis() - 3600000;
        RateLimitStatus status = new RateLimitStatus("GitHub", 500, 5000, pastTime, 4500);
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor).checkRateLimit(TEST_TOKEN, TEST_BASE_URL);
    }

    @Test
    void testCheckRateLimit_ExceedsThreshold_WithExcessiveCooldown_FailDisabled() throws IOException {
        long futureTime = System.currentTimeMillis() + (30L * 60 * 60 * 1000); // 30 hours
        RateLimitStatus status = new RateLimitStatus("GitHub", 500, 5000, futureTime, 4500);
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        assertDoesNotThrow(() -> rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL));
    }

    @Test
    void testCheckRateLimit_ExceedsThreshold_WithExcessiveCooldown_FailEnabled() throws IOException {
        ReflectionTestUtils.setField(rateLimitService, "failOnExcessiveCooldown", true);
        long futureTime = System.currentTimeMillis() + (30L * 60 * 60 * 1000); // 30 hours
        RateLimitStatus status = new RateLimitStatus("GitHub", 500, 5000, futureTime, 4500);
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        // The exception is caught and logged, not rethrown, so we just verify it doesn't throw
        assertDoesNotThrow(() -> rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL));
    }

    @Test
    void testCheckRateLimit_GitLabPlatform_Success() throws IOException {
        RateLimitStatus status = new RateLimitStatus("GitLab", 1500, 2000, System.currentTimeMillis() + 3600000, 500);
        when(gitLabMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        rateLimitService.checkRateLimit("GitLab", TEST_TOKEN, TEST_REPO, TEST_BASE_URL);

        verify(gitLabMonitor).checkRateLimit(TEST_TOKEN, TEST_BASE_URL);
    }

    @Test
    void testCheckRateLimit_CaseInsensitivePlatform() throws IOException {
        RateLimitStatus status = new RateLimitStatus("GitHub", 4000, 5000, System.currentTimeMillis() + 3600000, 1000);
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        rateLimitService.checkRateLimit("github", TEST_TOKEN, TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor).checkRateLimit(TEST_TOKEN, TEST_BASE_URL);
    }

    @Test
    void testCheckRateLimit_WithNullRepositoryName() throws IOException {
        RateLimitStatus status = new RateLimitStatus("GitHub", 4000, 5000, System.currentTimeMillis() + 3600000, 1000);
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        assertDoesNotThrow(() -> rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, null, TEST_BASE_URL));
    }

    @Test
    void testCheckRateLimit_WithDefaultThresholdZero_UsesMonitorThreshold() throws IOException {
        ReflectionTestUtils.setField(rateLimitService, "defaultThreshold", 0.0);
        RateLimitStatus status = new RateLimitStatus("GitHub", 4000, 5000, System.currentTimeMillis() + 3600000, 1000);
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor).checkRateLimit(TEST_TOKEN, TEST_BASE_URL);
        verify(gitHubMonitor).getDefaultThreshold();
    }

    @Test
    void testCheckRateLimit_WithNegativeDefaultThreshold_UsesMonitorThreshold() throws IOException {
        ReflectionTestUtils.setField(rateLimitService, "defaultThreshold", -0.5);
        RateLimitStatus status = new RateLimitStatus("GitHub", 4000, 5000, System.currentTimeMillis() + 3600000, 1000);
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(status);

        rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL);

        verify(gitHubMonitor).checkRateLimit(TEST_TOKEN, TEST_BASE_URL);
        verify(gitHubMonitor).getDefaultThreshold();
    }

    @Test
    void testCheckRateLimit_WithNullStatus_DoesNotThrow() throws IOException {
        when(gitHubMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)).thenReturn(null);

        assertDoesNotThrow(() -> rateLimitService.checkRateLimit("GitHub", TEST_TOKEN, TEST_REPO, TEST_BASE_URL));
    }
}
