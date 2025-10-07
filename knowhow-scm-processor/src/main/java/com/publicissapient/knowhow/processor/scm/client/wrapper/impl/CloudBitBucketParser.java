package com.publicissapient.knowhow.processor.scm.client.wrapper.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.publicissapient.knowhow.processor.scm.client.wrapper.BitbucketParser;


public class CloudBitBucketParser implements BitbucketParser {

    private static final Logger logger = LoggerFactory.getLogger(CloudBitBucketParser.class);

    /**
     * Parses diff content to extract file changes.
     */
    public List<ScmCommits.FileChange> parseDiffToFileChanges(String diffContent) {
        List<ScmCommits.FileChange> fileChanges = new ArrayList<>();

        if (diffContent == null || diffContent.trim().isEmpty()) {
            return fileChanges;
        }

        // Simple diff parsing - this is a basic implementation
        // In a production environment, you might want to use a more sophisticated diff parser
        String[] lines = diffContent.split("\n");
        String currentFile = null;
        int addedLines = 0;
        int removedLines = 0;
        Set<Integer> lineNumbers = new HashSet<>();
        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                // Save previous file if exists
                if (currentFile != null) {
                    fileChanges.add(ScmCommits.FileChange.builder()
                            .filePath(currentFile)
                            .addedLines(addedLines)
                            .removedLines(removedLines)
                            .changeType(determineChangeType(addedLines, removedLines))
                            .changedLineNumbers(lineNumbers.stream().toList())
                            .build());
                }

                // Extract file path
                String[] parts = line.split(" ");
                if (parts.length >= 4) {
                    currentFile = parts[3].substring(2); // Remove "b/" prefix
                }
                addedLines = 0;
                removedLines = 0;
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                addedLines++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removedLines++;
            } else if (line.startsWith("@@")) {
                String hunkInfo = line.substring(2, line.lastIndexOf("@@")).trim();
                for (String part : hunkInfo.split(" ")) {
                    if (part.startsWith("+") || part.startsWith("-")) {
                        String[] rangeParts = part.substring(1).split(",");
                        try {
                            int startLine = Integer.parseInt(rangeParts[0]);
                            int lineCount = rangeParts.length > 1 ? Integer.parseInt(rangeParts[1]) : 1;
                            for (int i = 0; i < lineCount; i++) {
                                lineNumbers.add(startLine + i);
                            }
                        } catch (NumberFormatException e) {
                            logger.warn("Failed to parse line number from hunk header: {}", line);
                        }
                    }
                }
            }
        }

        // Save last file
        if (currentFile != null) {
            fileChanges.add(ScmCommits.FileChange.builder()
                    .filePath(currentFile)
                    .addedLines(addedLines)
                    .removedLines(removedLines)
                    .changeType(determineChangeType(addedLines, removedLines))
                    .changedLineNumbers(lineNumbers.stream().toList())
                    .build());
        }

        return fileChanges;
    }

    /**
     * Determines the change type based on added and removed lines.
     */
    private String determineChangeType(int addedLines, int removedLines) {
        if (addedLines > 0 && removedLines == 0) {
            return "ADDED";
        } else if (addedLines == 0 && removedLines > 0) {
            return "DELETED";
        } else if (addedLines > 0 && removedLines > 0) {
            return "MODIFIED";
        } else {
            return "UNCHANGED";
        }
    }

    @Override
    public ScmMergeRequests.PullRequestStats parsePRDiffToFileChanges(String diffContent) {
        if (diffContent == null || diffContent.trim().isEmpty()) {
            return new ScmMergeRequests.PullRequestStats(0, 0, 0);
        }

        String[] lines = diffContent.split("\n");
        int addedLines = 0;
        int removedLines = 0;
        int changedFiles = 0;

        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                changedFiles++;
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                addedLines++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removedLines++;
            }
        }

        return new ScmMergeRequests.PullRequestStats(addedLines, removedLines, changedFiles);
    }
}
