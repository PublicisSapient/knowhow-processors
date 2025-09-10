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

    /**
     * Exception for data validation errors.
     */
    public static class DataValidationException extends DataProcessingException {
        public DataValidationException(String field, String value) {
            super("DATA_VALIDATION_ERROR", "Invalid value for field '" + field + "': " + value);
        }

        public DataValidationException(String message) {
            super("DATA_VALIDATION_ERROR", message);
        }
    }

    /**
     * Exception for data transformation errors.
     */
    public static class DataTransformationException extends DataProcessingException {
        public DataTransformationException(String sourceType, String targetType, Throwable cause) {
            super("DATA_TRANSFORMATION_ERROR", 
                  "Failed to transform data from " + sourceType + " to " + targetType, cause);
        }
    }

    /**
     * Exception for data persistence errors.
     */
    public static class DataPersistenceException extends DataProcessingException {
        public DataPersistenceException(String operation, Throwable cause) {
            super("DATA_PERSISTENCE_ERROR", "Failed to perform " + operation + " operation", cause);
        }
    }

    /**
     * Exception for duplicate data errors.
     */
    public static class DuplicateDataException extends DataProcessingException {
        public DuplicateDataException(String entityType, String identifier) {
            super("DUPLICATE_DATA_ERROR", 
                  "Duplicate " + entityType + " found with identifier: " + identifier);
        }
    }

    /**
     * Exception for data not found errors.
     */
    public static class DataNotFoundException extends DataProcessingException {
        public DataNotFoundException(String entityType, String identifier) {
            super("DATA_NOT_FOUND_ERROR", 
                  entityType + " not found with identifier: " + identifier);
        }
    }
}