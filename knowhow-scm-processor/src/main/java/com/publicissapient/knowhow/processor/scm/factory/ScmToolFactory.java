package com.publicissapient.knowhow.processor.scm.factory;

import com.publicissapient.knowhow.processor.scm.adapter.ScmToolAdapter;
import com.publicissapient.knowhow.processor.scm.factory.impl.AzureRepositoryFactory;
import com.publicissapient.knowhow.processor.scm.factory.impl.BitbucketFactory;
import com.publicissapient.knowhow.processor.scm.factory.impl.GitHubFactory;
import com.publicissapient.knowhow.processor.scm.factory.impl.GitLabFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ScmToolFactory {
    public abstract ScmToolAdapter createScmAdapter();

    /**
     * Static factory method to get the appropriate factory based on tool name.
     *
     * @param toolName the name of the SCM tool (GITHUB, GITLAB, BITBUCKET, AZUREREPO)
     * @return ScmToolFactory instance for the specified tool
     * @throws IllegalArgumentException if tool name is not supported
     */
    public static ScmToolFactory getFactory(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            log.error("Tool name cannot be null or empty");
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        String normalizedToolName = toolName.trim().toUpperCase();
        log.debug("Creating factory for SCM tool: {}", normalizedToolName);

        return switch (normalizedToolName) {
            case "GITHUB" -> new GitHubFactory();
            case "GITLAB" -> new GitLabFactory();
            case "BITBUCKET" -> new BitbucketFactory();
            case "AZUREREPO" -> new AzureRepositoryFactory();
            default -> {
                log.error("Unsupported SCM tool type: {}", toolName);
                throw new IllegalArgumentException("Unsupported SCM tool type: " + toolName +
                        ". Supported types are: GITHUB, GITLAB, BITBUCKET, AZUREREPO");
            }
        };
    }
}
