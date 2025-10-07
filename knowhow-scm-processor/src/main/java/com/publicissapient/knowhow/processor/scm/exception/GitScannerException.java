package com.publicissapient.knowhow.processor.scm.exception;

/**
 * Base exception class for all Git Scanner related exceptions.
 * 
 * This serves as the parent class for all custom exceptions in the application,
 * providing common functionality and consistent error handling.
 */
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

    public GitScannerException(String errorCode, String message, Object... parameters) {
        super(message);
        this.errorCode = errorCode;
        this.parameters = parameters;
    }

    public GitScannerException(String errorCode, String message, Throwable cause, Object... parameters) {
        super(message, cause);
        this.errorCode = errorCode;
        this.parameters = parameters;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getParameters() {
        return parameters;
    }
}