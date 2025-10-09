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

package com.publicissapient.knowhow.processor.scm.exception;

/**
 * Exception thrown when platform API operations fail.
 * 
 * This exception is used for errors related to platform-specific API calls,
 * rate limiting, authentication, and other platform integration issues.
 */
public class PlatformApiException extends GitScannerException {

    private final String platform;
    private final int statusCode;

    public PlatformApiException(String platform, String message) {
        super("PLATFORM_API_ERROR", message);
        this.platform = platform;
        this.statusCode = -1;
    }

    public PlatformApiException(String platform, String message, Throwable cause) {
        super("PLATFORM_API_ERROR", message, cause);
        this.platform = platform;
        this.statusCode = -1;
    }

    public PlatformApiException(String platform, int statusCode, String message) {
        super("PLATFORM_API_ERROR", message);
        this.platform = platform;
        this.statusCode = statusCode;
    }

    public PlatformApiException(String platform, int statusCode, String message, Throwable cause) {
        super("PLATFORM_API_ERROR", message, cause);
        this.platform = platform;
        this.statusCode = statusCode;
    }

    public String getPlatform() {
        return platform;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Exception for API rate limiting errors.
     */
    public static class RateLimitExceededException extends PlatformApiException {
        private final long resetTime;

        public RateLimitExceededException(String platform, long resetTime) {
            super(platform, "API rate limit exceeded. Reset time: " + resetTime);
            this.resetTime = resetTime;
        }

        public long getResetTime() {
            return resetTime;
        }
    }

    /**
     * Exception for API authentication failures.
     */
    public static class ApiAuthenticationException extends PlatformApiException {
        public ApiAuthenticationException(String platform) {
            super(platform, 401, "API authentication failed for platform: " + platform);
        }
    }

    /**
     * Exception for API authorization failures.
     */
    public static class ApiAuthorizationException extends PlatformApiException {
        public ApiAuthorizationException(String platform, String resource) {
            super(platform, 403, "API authorization failed for platform: " + platform + ", resource: " + resource);
        }
    }

    /**
     * Exception for API resource not found errors.
     */
    public static class ApiResourceNotFoundException extends PlatformApiException {
        public ApiResourceNotFoundException(String platform, String resource) {
            super(platform, 404, "API resource not found for platform: " + platform + ", resource: " + resource);
        }
    }

    /**
     * Exception for API server errors.
     */
    public static class ApiServerException extends PlatformApiException {
        public ApiServerException(String platform, int statusCode, String message) {
            super(platform, statusCode, "API server error for platform: " + platform + ", message: " + message);
        }
    }

    /**
     * Exception for unsupported platform errors.
     */
    public static class UnsupportedPlatformException extends PlatformApiException {
        public UnsupportedPlatformException(String platform) {
            super(platform, "Unsupported platform: " + platform);
        }
    }
}