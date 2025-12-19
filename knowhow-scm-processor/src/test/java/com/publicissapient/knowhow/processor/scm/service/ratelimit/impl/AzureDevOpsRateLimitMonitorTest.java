package com.publicissapient.knowhow.processor.scm.service.ratelimit.impl;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitStatus;
import org.azd.connection.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureDevOpsRateLimitMonitorTest {

    @InjectMocks
    private AzureDevOpsRateLimitMonitor azureDevOpsRateLimitMonitor;

    private static final String TEST_TOKEN = "test-token";
    private static final String TEST_BASE_URL_DEV = "https://dev.azure.com/myorg";
    private static final String TEST_BASE_URL_VS = "https://myorg.visualstudio.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(azureDevOpsRateLimitMonitor, "threshold", 0.8);
    }

    @Test
    void testGetPlatformName() {
        assertEquals("Azure DevOps", azureDevOpsRateLimitMonitor.getPlatformName());
    }

    @Test
    void testCheckRateLimit_WithNullToken_ReturnsDefaultStatus() {
        RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit(null, TEST_BASE_URL_DEV);

        assertNotNull(status);
        assertEquals("Azure DevOps", status.getPlatform());
        assertEquals(250, status.getRemaining());
        assertEquals(300, status.getLimit());
        assertEquals(50, status.getUsed());
    }

    @Test
    void testCheckRateLimit_WithEmptyToken_ReturnsDefaultStatus() {
        RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit("  ", TEST_BASE_URL_DEV);

        assertNotNull(status);
        assertEquals("Azure DevOps", status.getPlatform());
        assertEquals(250, status.getRemaining());
        assertEquals(300, status.getLimit());
    }

    @Test
    void testCheckRateLimit_WithDevAzureUrl_Success() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> {
                    when(mock.getOrganization()).thenReturn("myorg");
                })) {

            RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL_DEV);

            assertNotNull(status);
            assertEquals("Azure DevOps", status.getPlatform());
            assertEquals(250, status.getRemaining());
            assertEquals(300, status.getLimit());
            assertEquals(50, status.getUsed());
        }
    }

    @Test
    void testCheckRateLimit_WithVisualStudioUrl_Success() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> {
                    when(mock.getOrganization()).thenReturn("myorg");
                })) {

            RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL_VS);

            assertNotNull(status);
            assertEquals("Azure DevOps", status.getPlatform());
            assertEquals(250, status.getRemaining());
            assertEquals(300, status.getLimit());
        }
    }

    @Test
    void testCheckRateLimit_WithInvalidUrl_ReturnsDefaultStatus() {
        RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit(TEST_TOKEN, "invalid-url");

        assertNotNull(status);
        assertEquals("Azure DevOps", status.getPlatform());
        assertEquals(250, status.getRemaining());
    }

    @Test
    void testCheckRateLimit_WithNullUrl_ReturnsDefaultStatus() {
        RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit(TEST_TOKEN, null);

        assertNotNull(status);
        assertEquals("Azure DevOps", status.getPlatform());
        assertEquals(250, status.getRemaining());
    }

    @Test
    void testCheckRateLimit_WithConnectionException_ReturnsDefaultStatus() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> {
                    when(mock.getOrganization()).thenThrow(new RuntimeException("Connection failed"));
                })) {

            RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL_DEV);

            assertNotNull(status);
            assertEquals("Azure DevOps", status.getPlatform());
            assertEquals(250, status.getRemaining());
        }
    }

    @Test
    void testCheckRateLimit_WithEmptyOrganization_ReturnsDefaultStatus() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> {
                    when(mock.getOrganization()).thenReturn("");
                })) {

            RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL_DEV);

            assertNotNull(status);
            assertEquals("Azure DevOps", status.getPlatform());
            assertEquals(250, status.getRemaining());
        }
    }

    @Test
    void testCheckRateLimit_WithNullOrganization_ReturnsDefaultStatus() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> {
                    when(mock.getOrganization()).thenReturn(null);
                })) {

            RateLimitStatus status = azureDevOpsRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL_DEV);

            assertNotNull(status);
            assertEquals("Azure DevOps", status.getPlatform());
            assertEquals(250, status.getRemaining());
        }
    }

    @Test
    void testGetDefaultThreshold() {
        assertEquals(0.8, azureDevOpsRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testGetDefaultThreshold_WithCustomValue() {
        ReflectionTestUtils.setField(azureDevOpsRateLimitMonitor, "threshold", 0.9);
        assertEquals(0.9, azureDevOpsRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testSupports_WithAzureDevOps() {
        assertTrue(azureDevOpsRateLimitMonitor.supports("Azure DevOps"));
    }

    @Test
    void testSupports_WithAzureDevOpsLowerCase() {
        assertTrue(azureDevOpsRateLimitMonitor.supports("azure devops"));
    }

    @Test
    void testSupports_WithUnsupportedPlatform() {
        assertFalse(azureDevOpsRateLimitMonitor.supports("GitHub"));
    }
}
