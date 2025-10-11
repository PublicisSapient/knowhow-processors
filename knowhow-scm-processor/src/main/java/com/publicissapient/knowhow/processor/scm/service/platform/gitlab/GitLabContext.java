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