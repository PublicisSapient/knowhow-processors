package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;

import java.util.List;

/**
 * Strategy interface for fetching commit data from different sources.
 * 
 * This interface defines the contract for different strategies to fetch
 * commit data, either through JGit (local cloning) or REST APIs.
 * 
 * Implements the Strategy pattern to allow switching between different
 * data fetching approaches based on configuration or requirements.
 */
public interface CommitDataFetchStrategy {

    /**
     * Fetches commit data for a repository.
     *
     * @param toolConfigId the tool configuration ID
     * @param repositoryUrl the repository URL
     * @param branchName the branch name (optional, null for all branches)
     * @param credentials the repository credentials
     * @return list of commits
     * @throws DataProcessingException if data fetching fails
     */
    List<ScmCommits> fetchCommits(String toolType, String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName, RepositoryCredentials credentials, java.time.LocalDateTime since)
            throws DataProcessingException;


    /**
     * Checks if this strategy supports the given repository URL.
     * 
     * @param repositoryUrl the repository URL to check
     * @return true if supported, false otherwise
     */
    boolean supports(String repositoryUrl, String toolType);

    /**
     * Gets the strategy name for identification purposes.
     * 
     * @return the strategy name
     */
    String getStrategyName();

    /**
     * Data class for repository credentials.
     */
    class RepositoryCredentials {
        private final String username;
        private final String password;
        private final String token;
        private final String sshKey;
        private final String sshKeyPassphrase;

        private RepositoryCredentials(Builder builder) {
            this.username = builder.username;
            this.password = builder.password;
            this.token = builder.token;
            this.sshKey = builder.sshKey;
            this.sshKeyPassphrase = builder.sshKeyPassphrase;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getToken() { return token; }
        public String getSshKey() { return sshKey; }
        public String getSshKeyPassphrase() { return sshKeyPassphrase; }

        public boolean hasUsernamePassword() {
            return username != null && password != null;
        }

        public boolean hasToken() {
            return token != null && !token.trim().isEmpty();
        }

        public boolean hasSshKey() {
            return sshKey != null && !sshKey.trim().isEmpty();
        }

        public static class Builder {
            private String username;
            private String password;
            private String token;
            private String sshKey;
            private String sshKeyPassphrase;

            public Builder username(String username) { this.username = username; return this; }
            public Builder password(String password) { this.password = password; return this; }
            public Builder token(String token) { this.token = token; return this; }
            public Builder sshKey(String sshKey) { this.sshKey = sshKey; return this; }
            public Builder sshKeyPassphrase(String sshKeyPassphrase) { this.sshKeyPassphrase = sshKeyPassphrase; return this; }

            public RepositoryCredentials build() {
                return new RepositoryCredentials(this);
            }
        }
    }
}