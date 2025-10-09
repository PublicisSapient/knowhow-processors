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
 * Exception thrown when repository operations fail.
 * 
 * This exception is used for errors related to repository access,
 * cloning, authentication, and other repository-specific operations.
 */
public class RepositoryException extends GitScannerException {

    public RepositoryException(String message) {
        super("REPOSITORY_ERROR", message);
    }

    public RepositoryException(String message, Throwable cause) {
        super("REPOSITORY_ERROR", message, cause);
    }

    public RepositoryException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Exception for repository access denied errors.
     */
    public static class RepositoryAccessDeniedException extends RepositoryException {
        public RepositoryAccessDeniedException(String repositoryUrl) {
            super("REPOSITORY_ACCESS_DENIED", "Access denied to repository: " + repositoryUrl);
        }
    }

    /**
     * Exception for repository authentication failures.
     */
    public static class RepositoryAuthenticationException extends RepositoryException {
        public RepositoryAuthenticationException(String repositoryUrl) {
            super("REPOSITORY_AUTH_FAILED", "Authentication failed for repository: " + repositoryUrl);
        }
    }
}