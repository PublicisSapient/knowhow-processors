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

    public RepositoryException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * Exception for repository not found errors.
     */
    public static class RepositoryNotFoundException extends RepositoryException {
        public RepositoryNotFoundException(String repositoryUrl) {
            super("REPOSITORY_NOT_FOUND", "Repository not found: " + repositoryUrl);
        }
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
     * Exception for repository clone failures.
     */
    public static class RepositoryCloneException extends RepositoryException {
        public RepositoryCloneException(String repositoryUrl, Throwable cause) {
            super("REPOSITORY_CLONE_FAILED", "Failed to clone repository: " + repositoryUrl, cause);
        }
    }

    /**
     * Exception for invalid repository URL errors.
     */
    public static class InvalidRepositoryUrlException extends RepositoryException {
        public InvalidRepositoryUrlException(String repositoryUrl) {
            super("INVALID_REPOSITORY_URL", "Invalid repository URL: " + repositoryUrl);
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