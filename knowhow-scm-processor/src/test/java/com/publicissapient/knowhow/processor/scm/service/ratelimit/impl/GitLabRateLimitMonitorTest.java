package com.publicissapient.knowhow.processor.scm.service.ratelimit.impl;

import com.publicissapient.knowhow.processor.scm.exception.RateLimitExceededException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitStatus;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.UserApi;
import org.gitlab4j.api.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitLabRateLimitMonitorTest {

    @InjectMocks
    private GitLabRateLimitMonitor gitLabRateLimitMonitor;

    private static final String TEST_TOKEN = "test-token";
    private static final String TEST_BASE_URL = "https://gitlab.example.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gitLabRateLimitMonitor, "gitlabApiUrl", "https://gitlab.com/api/v4");
        ReflectionTestUtils.setField(gitLabRateLimitMonitor, "gitlabThreshold", 0.8);
    }

    @Test
    void testGetPlatformName() {
        assertEquals("GitLab", gitLabRateLimitMonitor.getPlatformName());
    }

    @Test
    void testCheckRateLimit_WithNullToken_ReturnsUnauthenticatedLimits() throws RateLimitExceededException {
        RateLimitStatus status = gitLabRateLimitMonitor.checkRateLimit(null, TEST_BASE_URL);

        assertNotNull(status);
        assertEquals("GitLab", status.getPlatform());
        assertEquals(250, status.getRemaining());
        assertEquals(300, status.getLimit());
        assertEquals(50, status.getUsed());
    }

    @Test
    void testCheckRateLimit_WithEmptyToken_ReturnsUnauthenticatedLimits() throws RateLimitExceededException {
        RateLimitStatus status = gitLabRateLimitMonitor.checkRateLimit("  ", TEST_BASE_URL);

        assertNotNull(status);
        assertEquals("GitLab", status.getPlatform());
        assertEquals(250, status.getRemaining());
        assertEquals(300, status.getLimit());
        assertEquals(50, status.getUsed());
    }

    @Test
    void testCheckRateLimit_Success_ReturnsDefaultStatus() throws Exception {
        try (MockedConstruction<GitLabApi> mockedGitLabApi = mockConstruction(GitLabApi.class,
                (mock, context) -> {
                    UserApi userApi = mock(UserApi.class);
                    User user = mock(User.class);
                    when(mock.getUserApi()).thenReturn(userApi);
                    when(userApi.getCurrentUser()).thenReturn(user);
                })) {

            RateLimitStatus status = gitLabRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL);

            assertNotNull(status);
            assertEquals("GitLab", status.getPlatform());
            assertEquals(1900, status.getRemaining());
            assertEquals(2000, status.getLimit());
            assertEquals(100, status.getUsed());
        }
    }

    @Test
    void testCheckRateLimit_AuthenticationFailed_ThrowsRateLimitExceededException() {
        try (MockedConstruction<GitLabApi> mockedGitLabApi = mockConstruction(GitLabApi.class,
                (mock, context) -> {
                    UserApi userApi = mock(UserApi.class);
                    when(mock.getUserApi()).thenReturn(userApi);
                    when(userApi.getCurrentUser()).thenThrow(new GitLabApiException("Unauthorized", 401));
                })) {

            assertThrows(RateLimitExceededException.class, () -> 
                gitLabRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL)
            );
        }
    }

    @Test
    void testCheckRateLimit_ForbiddenAccess_ReturnsConservativeStatus() throws Exception {
        try (MockedConstruction<GitLabApi> mockedGitLabApi = mockConstruction(GitLabApi.class,
                (mock, context) -> {
                    UserApi userApi = mock(UserApi.class);
                    when(mock.getUserApi()).thenReturn(userApi);
                    when(userApi.getCurrentUser()).thenThrow(new GitLabApiException("Forbidden", 403));
                })) {

            RateLimitStatus status = gitLabRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL);

            assertNotNull(status);
            assertEquals("GitLab", status.getPlatform());
            assertEquals(10, status.getRemaining());
            assertEquals(2000, status.getLimit());
            assertEquals(1990, status.getUsed());
        }
    }

    @Test
    void testCheckRateLimit_NotFound_ReturnsConservativeStatus() throws Exception {
        try (MockedConstruction<GitLabApi> mockedGitLabApi = mockConstruction(GitLabApi.class,
                (mock, context) -> {
                    UserApi userApi = mock(UserApi.class);
                    when(mock.getUserApi()).thenReturn(userApi);
                    when(userApi.getCurrentUser()).thenThrow(new GitLabApiException("Not Found", 404));
                })) {

            RateLimitStatus status = gitLabRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL);

            assertNotNull(status);
            assertEquals("GitLab", status.getPlatform());
            assertEquals(1000, status.getRemaining());
            assertEquals(2000, status.getLimit());
            assertEquals(1000, status.getUsed());
        }
    }

    @Test
    void testCheckRateLimit_OtherGitLabApiException_ReturnsConservativeStatus() throws Exception {
        try (MockedConstruction<GitLabApi> mockedGitLabApi = mockConstruction(GitLabApi.class,
                (mock, context) -> {
                    UserApi userApi = mock(UserApi.class);
                    when(mock.getUserApi()).thenReturn(userApi);
                    when(userApi.getCurrentUser()).thenThrow(new GitLabApiException("Server Error", 500));
                })) {

            RateLimitStatus status = gitLabRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL);

            assertNotNull(status);
            assertEquals("GitLab", status.getPlatform());
            assertEquals(500, status.getRemaining());
            assertEquals(2000, status.getLimit());
            assertEquals(1500, status.getUsed());
        }
    }

    @Test
    void testCheckRateLimit_UnexpectedException_ReturnsConservativeStatus() throws Exception {
        try (MockedConstruction<GitLabApi> mockedGitLabApi = mockConstruction(GitLabApi.class,
                (mock, context) -> {
                    when(mock.getUserApi()).thenThrow(new RuntimeException("Unexpected error"));
                })) {

            RateLimitStatus status = gitLabRateLimitMonitor.checkRateLimit(TEST_TOKEN, TEST_BASE_URL);

            assertNotNull(status);
            assertEquals("GitLab", status.getPlatform());
            assertEquals(500, status.getRemaining());
            assertEquals(2000, status.getLimit());
            assertEquals(1500, status.getUsed());
        }
    }

    @Test
    void testGetDefaultThreshold_WithValidConfiguredValue() {
        ReflectionTestUtils.setField(gitLabRateLimitMonitor, "gitlabThreshold", 0.75);
        assertEquals(0.75, gitLabRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testGetDefaultThreshold_WithInvalidValue_ReturnsFallback() {
        ReflectionTestUtils.setField(gitLabRateLimitMonitor, "gitlabThreshold", 1.5);
        assertEquals(0.8, gitLabRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testGetDefaultThreshold_WithZeroValue_ReturnsFallback() {
        ReflectionTestUtils.setField(gitLabRateLimitMonitor, "gitlabThreshold", 0.0);
        assertEquals(0.8, gitLabRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testSupports_WithGitLabUpperCase() {
        assertTrue(gitLabRateLimitMonitor.supports("GitLab"));
    }

    @Test
    void testSupports_WithGitLabLowerCase() {
        assertTrue(gitLabRateLimitMonitor.supports("gitlab"));
    }

    @Test
    void testSupports_WithGitLabMixedCase() {
        assertTrue(gitLabRateLimitMonitor.supports("GITLAB"));
    }

    @Test
    void testSupports_WithUnsupportedPlatform() {
        assertFalse(gitLabRateLimitMonitor.supports("GitHub"));
    }
}
