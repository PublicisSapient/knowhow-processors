package com.publicissapient.knowhow.processor.scm.client.wrapper.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.client.wrapper.BitbucketParser;
import com.publicissapient.kpidashboard.common.model.scm.CommitDetails;
import com.publicissapient.kpidashboard.common.model.scm.MergeRequests;

public class ServerBitbucketParser implements BitbucketParser {

    public List<CommitDetails.FileChange> parseDiffToFileChanges(String diffContent) {
        List<CommitDetails.FileChange> fileChanges = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(diffContent);

            JsonNode diffs = rootNode.get("diffs");
            if (diffs != null && diffs.isArray()) {
                for (JsonNode diff : diffs) {
                    int totalAddedLines = 0;
                    int totalRemovedLines = 0;
                    Set<Integer> changedLines = new HashSet<>();
                    CommitDetails.FileChange fileChange = new CommitDetails.FileChange();

                    // Handle null case for fileName
                    JsonNode sourceNode = diff.get("source");
                    String fileName = (sourceNode != null && sourceNode.get("toString") != null)
                            ? sourceNode.get("toString").asText()
                            : "Unknown File";
                    fileChange.setFilePath(fileName);

                    JsonNode hunks = diff.get("hunks");
                    if (hunks != null && hunks.isArray()) {
                        for (JsonNode hunk : hunks) {
                            JsonNode segments = hunk.get("segments");
                            if (segments != null && segments.isArray()) {
                                for (JsonNode segment : segments) {
                                    String type = segment.get("type").asText();
                                    JsonNode lines = segment.get("lines");
                                    if (type.equalsIgnoreCase("context")) {
                                        continue;
                                    }
                                    if (lines != null && lines.isArray()) {
                                        for (JsonNode line : lines) {
                                            if ("ADDED".equals(type)) {
                                                int lineNumber = line.get("destination") != null
                                                        ? line.get("destination").asInt()
                                                        : line.get("source").asInt(); // Default value for missing line number
                                                totalAddedLines++;
                                                if (lineNumber != -1) {
                                                    changedLines.add(lineNumber);
                                                }
                                            } else if ("REMOVED".equals(type)) {
                                                int lineNumber = line.get("source") != null
                                                        ? line.get("source").asInt()
                                                        : line.get("destination").asInt(); // Default value for missing line number
                                                totalRemovedLines++;
                                                if (lineNumber != -1) {
                                                    changedLines.add(lineNumber);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    fileChange.setAddedLines(totalAddedLines);
                    fileChange.setRemovedLines(totalRemovedLines);
                    fileChange.setChangedLineNumbers(changedLines.stream().toList());
                    fileChanges.add(fileChange);
                }
            }

        } catch (Exception e) {
            e.printStackTrace(); // Log the exception instead of throwing it
        }
        return fileChanges;
    }

    @Override
    public MergeRequests.PullRequestStats parsePRDiffToFileChanges(String diffContent) {
        MergeRequests.PullRequestStats pullRequestStats = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(diffContent);
            JsonNode diffs = rootNode.get("diffs");
            int totalAddedLines = 0;
            int totalRemovedLines = 0;
            int changedFiles = 0;
            if (diffs != null && diffs.isArray()) {
                for (JsonNode diff : diffs) {
                    changedFiles++;
                    JsonNode hunks = diff.get("hunks");
                    if (hunks != null && hunks.isArray()) {
                        for (JsonNode hunk : hunks) {
                            JsonNode segments = hunk.get("segments");
                            if (segments != null && segments.isArray()) {
                                for (JsonNode segment : segments) {
                                    String type = segment.get("type").asText();
                                    JsonNode lines = segment.get("lines");
                                    if (type.equalsIgnoreCase("context")) {
                                        continue;
                                    }
                                    if (lines != null && lines.isArray()) {
                                        if ("ADDED".equals(type)) {
                                            totalAddedLines += lines.size();
                                        } else if ("REMOVED".equals(type)) {
                                            totalRemovedLines += lines.size();
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            }
            pullRequestStats = new MergeRequests.PullRequestStats(totalAddedLines, totalRemovedLines, changedFiles);

        } catch (Exception e) {
            e.printStackTrace(); // Log the exception instead of throwing it
        }
        return pullRequestStats;
    }

}
