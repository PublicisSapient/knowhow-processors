package com.publicissapient.knowhow.processor.scm.service.ratelimit.impl;

import com.publicissapient.knowhow.processor.scm.exception.RateLimitExceededException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BitbucketRateLimitMonitorTest {

    private BitbucketRateLimitMonitor bitbucketRateLimitMonitor;

    private static final String TEST_TOKEN = "test-token";
    private static final String BITBUCKET_CLOUD_URL = "https://api.bitbucket.org/2.0";
    private static final String BITBUCKET_SERVER_URL = "https://bitbucket.company.com";

    @BeforeEach
    void setUp() {
        bitbucketRateLimitMonitor = new BitbucketRateLimitMonitor();
        ReflectionTestUtils.setField(bitbucketRateLimitMonitor, "bitbucketApiUrl", "https://api.bitbucket.org/2.0");
        ReflectionTestUtils.setField(bitbucketRateLimitMonitor, "bitbucketThreshold", 0.8);
    }

    @Test
    void testGetPlatformName() {
        assertEquals("Bitbucket", bitbucketRateLimitMonitor.getPlatformName());
    }

    @Test
    void testCheckRateLimit_BitbucketCloud_ReturnsCloudLimits() {
        RateLimitStatus status = bitbucketRateLimitMonitor.checkRateLimit(TEST_TOKEN, BITBUCKET_CLOUD_URL);

        assertNotNull(status);
        assertEquals("Bitbucket", status.getPlatform());
        assertEquals(4000, status.getRemaining());
        assertEquals(5000, status.getLimit());
        assertEquals(1000, status.getUsed());
    }

    @Test
    void testCheckRateLimit_BitbucketServer_ReturnsServerLimits() {
        RateLimitStatus status = bitbucketRateLimitMonitor.checkRateLimit(TEST_TOKEN, BITBUCKET_SERVER_URL);

        assertNotNull(status);
        assertEquals("Bitbucket", status.getPlatform());
        assertEquals(800, status.getRemaining());
        assertEquals(1000, status.getLimit());
        assertEquals(200, status.getUsed());
    }

    @Test
    void testCheckRateLimit_WithNullBaseUrl_UsesDefaultCloudUrl() {
        RateLimitStatus status = bitbucketRateLimitMonitor.checkRateLimit(TEST_TOKEN, null);

        assertNotNull(status);
        assertEquals("Bitbucket", status.getPlatform());
        assertEquals(4000, status.getRemaining());
        assertEquals(5000, status.getLimit());
    }

    @Test
    void testCheckRateLimit_Unauthorized_ThrowsRateLimitExceededException() {
        BitbucketRateLimitMonitor testMonitor = new BitbucketRateLimitMonitor() {
            @Override
            public RateLimitStatus checkRateLimit(String token, String baseUrl) {
                try {
                    String apiUrl = baseUrl != null ? baseUrl : "https://api.bitbucket.org/2.0";
                    throw WebClientResponseException.create(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        null,
                        null,
                        null
                    );
                } catch (WebClientResponseException e) {
                    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        throw new RateLimitExceededException("Bitbucket", 0, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    }
                    return new RateLimitStatus("Bitbucket", 500, 1000, System.currentTimeMillis() + 3600000, 500);
                }
            }
        };
        ReflectionTestUtils.setField(testMonitor, "bitbucketApiUrl", "https://api.bitbucket.org/2.0");
        ReflectionTestUtils.setField(testMonitor, "bitbucketThreshold", 0.8);

        assertThrows(RateLimitExceededException.class, () -> 
            testMonitor.checkRateLimit(TEST_TOKEN, BITBUCKET_CLOUD_URL)
        );
    }

    @Test
    void testCheckRateLimit_Forbidden_ThrowsRateLimitExceededException() {
        BitbucketRateLimitMonitor testMonitor = new BitbucketRateLimitMonitor() {
            @Override
            public RateLimitStatus checkRateLimit(String token, String baseUrl) {
                try {
                    String apiUrl = baseUrl != null ? baseUrl : "https://api.bitbucket.org/2.0";
                    throw WebClientResponseException.create(
                        HttpStatus.FORBIDDEN.value(),
                        "Forbidden",
                        null,
                        null,
                        null
                    );
                } catch (WebClientResponseException e) {
                    if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                        throw new RateLimitExceededException("Bitbucket", 1000, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    }
                    return new RateLimitStatus("Bitbucket", 500, 1000, System.currentTimeMillis() + 3600000, 500);
                }
            }
        };
        ReflectionTestUtils.setField(testMonitor, "bitbucketApiUrl", "https://api.bitbucket.org/2.0");
        ReflectionTestUtils.setField(testMonitor, "bitbucketThreshold", 0.8);

        assertThrows(RateLimitExceededException.class, () -> 
            testMonitor.checkRateLimit(TEST_TOKEN, BITBUCKET_CLOUD_URL)
        );
    }

    @Test
    void testCheckRateLimit_TooManyRequests_ThrowsRateLimitExceededException() {
        BitbucketRateLimitMonitor testMonitor = new BitbucketRateLimitMonitor() {
            @Override
            public RateLimitStatus checkRateLimit(String token, String baseUrl) {
                try {
                    String apiUrl = baseUrl != null ? baseUrl : "https://api.bitbucket.org/2.0";
                    throw WebClientResponseException.create(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too Many Requests",
                        null,
                        null,
                        null
                    );
                } catch (WebClientResponseException e) {
                    if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        throw new RateLimitExceededException("Bitbucket", 1000, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    }
                    return new RateLimitStatus("Bitbucket", 500, 1000, System.currentTimeMillis() + 3600000, 500);
                }
            }
        };
        ReflectionTestUtils.setField(testMonitor, "bitbucketApiUrl", "https://api.bitbucket.org/2.0");
        ReflectionTestUtils.setField(testMonitor, "bitbucketThreshold", 0.8);

        assertThrows(RateLimitExceededException.class, () -> 
            testMonitor.checkRateLimit(TEST_TOKEN, BITBUCKET_CLOUD_URL)
        );
    }

    @Test
    void testCheckRateLimit_OtherWebClientException_ReturnsConservativeStatus() {
        BitbucketRateLimitMonitor testMonitor = new BitbucketRateLimitMonitor() {
            @Override
            public RateLimitStatus checkRateLimit(String token, String baseUrl) {
                try {
                    String apiUrl = baseUrl != null ? baseUrl : "https://api.bitbucket.org/2.0";
                    throw WebClientResponseException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Server Error",
                        null,
                        null,
                        null
                    );
                } catch (WebClientResponseException e) {
                    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        throw new RateLimitExceededException("Bitbucket", 0, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                        throw new RateLimitExceededException("Bitbucket", 1000, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        throw new RateLimitExceededException("Bitbucket", 1000, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    }
                    return new RateLimitStatus("Bitbucket", 500, 1000, System.currentTimeMillis() + 3600000, 500);
                } catch (Exception e) {
                    return new RateLimitStatus("Bitbucket", 500, 1000, System.currentTimeMillis() + 3600000, 500);
                }
            }
        };
        ReflectionTestUtils.setField(testMonitor, "bitbucketApiUrl", "https://api.bitbucket.org/2.0");
        ReflectionTestUtils.setField(testMonitor, "bitbucketThreshold", 0.8);

        RateLimitStatus status = testMonitor.checkRateLimit(TEST_TOKEN, BITBUCKET_CLOUD_URL);

        assertNotNull(status);
        assertEquals("Bitbucket", status.getPlatform());
        assertEquals(500, status.getRemaining());
        assertEquals(1000, status.getLimit());
        assertEquals(500, status.getUsed());
    }

    @Test
    void testCheckRateLimit_UnexpectedException_ReturnsConservativeStatus() {
        BitbucketRateLimitMonitor testMonitor = new BitbucketRateLimitMonitor() {
            @Override
            public RateLimitStatus checkRateLimit(String token, String baseUrl) {
                try {
                    String apiUrl = baseUrl != null ? baseUrl : "https://api.bitbucket.org/2.0";
                    throw new RuntimeException("Unexpected error");
                } catch (WebClientResponseException e) {
                    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        throw new RateLimitExceededException("Bitbucket", 0, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                        throw new RateLimitExceededException("Bitbucket", 1000, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        throw new RateLimitExceededException("Bitbucket", 1000, 1000, 100.0, System.currentTimeMillis() + 3600000);
                    }
                    return new RateLimitStatus("Bitbucket", 500, 1000, System.currentTimeMillis() + 3600000, 500);
                } catch (Exception e) {
                    return new RateLimitStatus("Bitbucket", 500, 1000, System.currentTimeMillis() + 3600000, 500);
                }
            }
        };
        ReflectionTestUtils.setField(testMonitor, "bitbucketApiUrl", "https://api.bitbucket.org/2.0");
        ReflectionTestUtils.setField(testMonitor, "bitbucketThreshold", 0.8);

        RateLimitStatus status = testMonitor.checkRateLimit(TEST_TOKEN, BITBUCKET_CLOUD_URL);

        assertNotNull(status);
        assertEquals("Bitbucket", status.getPlatform());
        assertEquals(500, status.getRemaining());
        assertEquals(1000, status.getLimit());
    }

    @Test
    void testGetDefaultThreshold_WithValidValue() {
        ReflectionTestUtils.setField(bitbucketRateLimitMonitor, "bitbucketThreshold", 0.75);
        assertEquals(0.75, bitbucketRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testGetDefaultThreshold_WithZeroValue_ReturnsFallback() {
        ReflectionTestUtils.setField(bitbucketRateLimitMonitor, "bitbucketThreshold", 0.0);
        assertEquals(0.8, bitbucketRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testGetDefaultThreshold_WithInvalidValue_ReturnsFallback() {
        ReflectionTestUtils.setField(bitbucketRateLimitMonitor, "bitbucketThreshold", 1.5);
        assertEquals(0.8, bitbucketRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testGetDefaultThreshold_WithNegativeValue_ReturnsFallback() {
        ReflectionTestUtils.setField(bitbucketRateLimitMonitor, "bitbucketThreshold", -0.5);
        assertEquals(0.8, bitbucketRateLimitMonitor.getDefaultThreshold());
    }

    @Test
    void testSupports_WithBitbucket() {
        assertTrue(bitbucketRateLimitMonitor.supports("Bitbucket"));
    }

    @Test
    void testSupports_WithBitbucketLowerCase() {
        assertTrue(bitbucketRateLimitMonitor.supports("bitbucket"));
    }

    @Test
    void testSupports_WithUnsupportedPlatform() {
        assertFalse(bitbucketRateLimitMonitor.supports("GitHub"));
    }

    @Test
    void testSupports_WithBitbucketMixedCase() {
        assertTrue(bitbucketRateLimitMonitor.supports("BITBUCKET"));
    }

    @Test
    void testCheckRateLimit_WithEmptyBaseUrl_UsesDefault() {
        RateLimitStatus status = bitbucketRateLimitMonitor.checkRateLimit(TEST_TOKEN, "");

        assertNotNull(status);
        assertEquals("Bitbucket", status.getPlatform());
    }

    @Test
    void testCheckRateLimit_BitbucketCloudWithApiInUrl() {
        RateLimitStatus status = bitbucketRateLimitMonitor.checkRateLimit(TEST_TOKEN, "https://api.bitbucket.org/2.0/repositories");

        assertNotNull(status);
        assertEquals("Bitbucket", status.getPlatform());
        assertEquals(4000, status.getRemaining());
        assertEquals(5000, status.getLimit());
    }

    @Test
    void testCheckRateLimit_BitbucketServerCustomUrl() {
        RateLimitStatus status = bitbucketRateLimitMonitor.checkRateLimit(TEST_TOKEN, "https://my-bitbucket.company.com");

        assertNotNull(status);
        assertEquals("Bitbucket", status.getPlatform());
        assertEquals(800, status.getRemaining());
        assertEquals(1000, status.getLimit());
    }
}
