package com.publicissapient.knowhow.processor.scm.domain.enums;

/**
 * Enumeration representing the status of a repository in the scanning process.
 * 
 * This enum tracks the lifecycle of repository scanning operations,
 * from initial discovery through completion or failure.
 */
public enum RepositoryStatus {
    
    /**
     * Repository has been discovered but scanning has not started
     */
    PENDING("Pending"),
    
    /**
     * Repository scanning is currently in progress
     */
    IN_PROGRESS("In Progress"),
    
    /**
     * Repository scanning completed successfully
     */
    COMPLETED("Completed"),
    
    /**
     * Repository scanning failed due to an error
     */
    FAILED("Failed"),
    
    /**
     * Repository scanning was cancelled by user or system
     */
    CANCELLED("Cancelled"),
    
    /**
     * Repository is currently being retried after a previous failure
     */
    RETRYING("Retrying"),
    
    /**
     * Repository scanning is paused
     */
    PAUSED("Paused"),
    
    /**
     * Repository is queued for scanning
     */
    QUEUED("Queued");

    private final String displayName;

    RepositoryStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if the status represents a terminal state (no further processing expected).
     * 
     * @return true if the status is terminal, false otherwise
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * Checks if the status represents an active processing state.
     * 
     * @return true if the status indicates active processing, false otherwise
     */
    public boolean isActive() {
        return this == IN_PROGRESS || this == RETRYING;
    }

    /**
     * Checks if the status represents a waiting state.
     * 
     * @return true if the status indicates waiting, false otherwise
     */
    public boolean isWaiting() {
        return this == PENDING || this == QUEUED || this == PAUSED;
    }
}