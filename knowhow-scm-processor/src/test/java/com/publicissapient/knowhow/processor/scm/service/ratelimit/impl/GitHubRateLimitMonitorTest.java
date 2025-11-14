package com.publicissapient.knowhow.processor.scm.service.ratelimit.impl;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubRateLimitMonitorTest {

    @InjectMocks
    private GitHubRateLimitMonitor gitHubRateLimitMonitor;

    @Mock
    private GitHub github;

    @Mock
    private GHRateLimit ghRateLimit;

    @Mock
    private GHRateLimit.Record coreRecord;

    private static final String TEST_TOKEN = "test-token";
    private static final String TEST_BASE_URL = "https://api.github.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gitHubRateLimitMonitor, "githubApiUrl", "https://api.github.com");
        ReflectionTestUtils.setField(gitHubRateLimitMonitor, "githubThreshold", 0.8);
    }

    @Test
    void testGetPlatformName() {
        assertEquals("GitHub", gitHubRateLimitMonitor.getPlatformName());
    }

    @Test
    void testCheckRateLimit_Success() throws IOException {
        try (MockedConstruction<GitHubBuilder> mockedBuilder = mockConstruction(GitHubBuilder.class,
                (mock, context) -> {
                    when(mock.withEndpoint(anyString())).thenReturn(mock);
                    when(mock.withOAuthToken(anyString())).thenReturn(mock);
                    when(mock.build()).thenReturn(github);
                })) {

            when(github.getRateLimit()).thenReturn(ghRateLimit);
            when(ghRateLimit.getCore()).thenReturn(coreRecord);
            when(coreRecord.getLimit()).thenReturn(5000);
            when(coreRecord.getRemaining()).thenReturn(4500);
            when(coreRecord.getResetDate()).thenReturn(new Date(System.currentTimeMillis() + 3600000));

            RateLimitStatus status = gitHubRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL);

            assertNotNull(status);
            assertEquals("GitHub", status.getPlatform());
            assertEquals(4500, status.getRemaining());
            assertEquals(5000, status.getLimit());
            assertEquals(500, status.getUsed());
        }
    }

    @Test
    void testCheckRateLimit_WithZeroRemaining() throws IOException {
        try (MockedConstruction<GitHubBuilder> mockedBuilder = mockConstruction(GitHubBuilder.class,
                (mock, context) -> {
                    when(mock.withEndpoint(anyString())).thenReturn(mock);
                    when(mock.withOAuthToken(anyString())).thenReturn(mock);
                    when(mock.build()).thenReturn(github);
                })) {

            when(github.getRateLimit()).thenReturn(ghRateLimit);
            when(ghRateLimit.getCore()).thenReturn(coreRecord);
            when(coreRecord.getLimit()).thenReturn(5000);
            when(coreRecord.getRemaining()).thenReturn(0);
            when(coreRecord.getResetDate()).thenReturn(new Date(System.currentTimeMillis() + 3600000));

            RateLimitStatus status = gitHubRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL);

            assertNotNull(status);
            assertEquals(0, status.getRemaining());
            assertEquals(5000, status.getUsed());
        }
    }

    @Test
    void testCheckRateLimit_ThrowsIOException() {
        try (MockedConstruction<GitHubBuilder> mockedBuilder = mockConstruction(GitHubBuilder.class,
                (mock, context) -> {
                    when(mock.withEndpoint(anyString())).thenReturn(mock);
                    when(mock.withOAuthToken(anyString())).thenReturn(mock);
                    when(mock.build()).thenThrow(new IOException("Connection failed"));
                })) {

            assertThrows(IOException.class, () -> gitHubRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL));
        }
    }

    @Test
    void testGetDefaultThreshold_WithValidValue() {
        ReflectionTestUtils.setField(gitHubRateLimitMonitor, "githubThreshold", 0.75);
        assertEquals(0.75, gitHubRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testGetDefaultThreshold_WithZeroValue_ReturnsFallback() {
        ReflectionTestUtils.setField(gitHubRateLimitMonitor, "githubThreshold", 0.0);
        assertEquals(0.8, gitHubRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testGetDefaultThreshold_WithNegativeValue_ReturnsFallback() {
        ReflectionTestUtils.setField(gitHubRateLimitMonitor, "githubThreshold", -0.5);
        assertEquals(0.8, gitHubRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testSupports_WithGitHub() {
        assertTrue(gitHubRateLimitMonitor.supports("GitHub"));
    }

    @Test
    void testSupports_WithGitHubLowerCase() {
        assertTrue(gitHubRateLimitMonitor.supports("github"));
    }

    @Test
    void testSupports_WithUnsupportedPlatform() {
        assertFalse(gitHubRateLimitMonitor.supports("GitLab"));
    }
}
