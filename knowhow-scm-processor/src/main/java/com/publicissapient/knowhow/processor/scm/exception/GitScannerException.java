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
 * Base exception class for all Git Scanner related exceptions.
 * 
 * This serves as the parent class for all custom exceptions in the application,
 * providing common functionality and consistent error handling.
 */
@Getter
public class GitScannerException extends RuntimeException {

    private final String errorCode;
    private final Object[] parameters;

    public GitScannerException(String message) {
        super(message);
        this.errorCode = null;
        this.parameters = null;
    }

    public GitScannerException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.parameters = null;
    }

    public GitScannerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.parameters = null;
    }

    public GitScannerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.parameters = null;
    }

}