package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import org.springframework.stereotype.Component;

/**
 * Context holder for GitLab-specific information during request processing.
 * Uses ThreadLocal to store repository URL for the current request thread.
 */
@Component
public class GitLabContext {
    
    private static final ThreadLocal<String> repositoryUrlContext = new ThreadLocal<>();
    
    /**
     * Sets the repository URL for the current thread.
     * 
     * @param repositoryUrl the repository URL
     */
    public static void setRepositoryUrl(String repositoryUrl) {
        repositoryUrlContext.set(repositoryUrl);
    }
    
    /**
     * Gets the repository URL for the current thread.
     * 
     * @return the repository URL, or null if not set
     */
    public static String getRepositoryUrl() {
        return repositoryUrlContext.get();
    }
    
    /**
     * Clears the repository URL for the current thread.
     * Should be called after request processing is complete.
     */
    public static void clear() {
        repositoryUrlContext.remove();
    }
    
    /**
     * Executes a runnable with the given repository URL context.
     * Automatically clears the context after execution.
     * 
     * @param repositoryUrl the repository URL to set
     * @param runnable the code to execute
     */
    public static void withRepositoryUrl(String repositoryUrl, Runnable runnable) {
        try {
            setRepositoryUrl(repositoryUrl);
            runnable.run();
        } finally {
            clear();
        }
    }
}