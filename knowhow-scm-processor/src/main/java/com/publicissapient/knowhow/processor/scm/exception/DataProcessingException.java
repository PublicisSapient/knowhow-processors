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
 * Exception thrown when data processing operations fail.
 * 
 * This exception is used for errors related to data transformation,
 * validation, persistence, and other data processing operations.
 */
public class DataProcessingException extends GitScannerException {

    public DataProcessingException(String message) {
        super("DATA_PROCESSING_ERROR", message);
    }

    public DataProcessingException(String message, Throwable cause) {
        super("DATA_PROCESSING_ERROR", message, cause);
    }

    public DataProcessingException(String errorCode, String message) {
        super(errorCode, message);
    }

    public DataProcessingException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

}