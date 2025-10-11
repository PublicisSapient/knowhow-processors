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

import lombok.Getter;

/**
 * Exception thrown when platform API operations fail.
 * 
 * This exception is used for errors related to platform-specific API calls,
 * rate limiting, authentication, and other platform integration issues.
 */
@Getter
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

}