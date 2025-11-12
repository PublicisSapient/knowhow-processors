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

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class GitLabCommonHelper {

    private static final String MODIFIED_STATUS = "MODIFIED";
    private static final String[] BINARY_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".ico", ".svg",
            ".pdf", ".zip", ".tar", ".gz", ".rar", ".7z", ".exe", ".dll", ".so", ".dylib", ".class", ".jar"};
    private static final Pattern HUNK_PATTERN = Pattern.compile("@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

    public User createUser(String username, String email, String displayName) {
        return User.builder()
                .username(username)
                .email(email != null ? email : displayName)
                .displayName(displayName)
                .build();
    }

    public String mapGitLabStatus(String status) {
        if (status == null) return MODIFIED_STATUS;
        return switch (status.toLowerCase()) {
            case "new" -> "ADDED";
            case "deleted" -> "DELETED";
            case "renamed" -> "RENAMED";
            default -> MODIFIED_STATUS;
        };
    }

    public boolean isBinaryFile(String fileName) {
        if (fileName == null) return false;
        String lowerFileName = fileName.toLowerCase();
        for (String ext : BINARY_EXTENSIONS) {
            if (lowerFileName.endsWith(ext)) return true;
        }
        return false;
    }

    public List<Integer> extractLineNumbers(String diff) {
        List<Integer> lineNumbers = new ArrayList<>();
        if (diff == null || diff.isEmpty()) return lineNumbers;

        try {
            String[] lines = diff.split("\n");
            int currentLineNumber = 0;

            for (String line : lines) {
                Matcher matcher = HUNK_PATTERN.matcher(line);
                if (matcher.find()) {
                    currentLineNumber = Integer.parseInt(matcher.group(1));
                } else if (isChangeLine(line)) {
                    lineNumbers.add(currentLineNumber);
                    if (line.startsWith("+")) currentLineNumber++;
                } else if (isContentLine(line)) {
                    currentLineNumber++;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract line numbers from diff: {}", e.getMessage());
        }
        return lineNumbers;
    }

    public DiffStats parseDiffContent(String diffContent) {
        if (diffContent == null || diffContent.isEmpty()) return new DiffStats(0, 0);

        int addedLines = 0;
        int removedLines = 0;
        String[] lines = diffContent.split("\n");
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                addedLines++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removedLines++;
            }
        }
        return new DiffStats(addedLines, removedLines);
    }

    public void setMergeRequestState(ScmMergeRequests.ScmMergeRequestsBuilder builder,
                                     org.gitlab4j.api.models.MergeRequest gitlabMr) {
        if (gitlabMr.getClosedAt() != null) {
            builder.isClosed(true);
        } else {
            builder.isOpen(true);
        }
    }

    public void setMergeRequestTimestamps(ScmMergeRequests.ScmMergeRequestsBuilder builder,
                                          org.gitlab4j.api.models.MergeRequest gitlabMr) {
        if (gitlabMr.getMergedAt() != null) {
            builder.mergedAt(gitlabMr.getMergedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (gitlabMr.getClosedAt() != null) {
            builder.closedDate(gitlabMr.getClosedAt().toInstant().toEpochMilli());
        }
    }

    public void setMergeRequestAuthor(ScmMergeRequests.ScmMergeRequestsBuilder builder,
                                      org.gitlab4j.api.models.MergeRequest gitlabMr) {
        if (gitlabMr.getAuthor() != null) {
            builder.authorUserId(gitlabMr.getAuthor().getUsername());
            User user = createUser(
                    gitlabMr.getAuthor().getUsername(),
                    gitlabMr.getAuthor().getEmail(),
                    gitlabMr.getAuthor().getName()
            );
            builder.authorId(user);
        }
    }

    public ScmCommits.FileChange convertDiffToFileChange(Diff diff) {
        if (diff == null) return null;

        String filePath = diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
        if (filePath == null) return null;

        DiffStats stats = parseDiffContent(diff.getDiff());

        return ScmCommits.FileChange.builder()
                .filePath(filePath)
                .changeType(mapGitLabStatus(determineChangeType(diff)))
                .addedLines(stats.getAddedLines())
                .removedLines(stats.getRemovedLines())
                .changedLines(stats.getAddedLines() + stats.getRemovedLines())
                .isBinary(isBinaryFile(filePath))
                .changedLineNumbers(extractLineNumbers(diff.getDiff()))
                .build();
    }

    private String determineChangeType(Diff diff) {
        if (Boolean.TRUE.equals(diff.getNewFile())) return "new";
        if (Boolean.TRUE.equals(diff.getDeletedFile())) return "deleted";
        if (Boolean.TRUE.equals(diff.getRenamedFile())) return "renamed";
        return "modified";
    }

    private boolean isChangeLine(String line) {
        return (line.startsWith("+") || line.startsWith("-")) && !line.startsWith("+++") && !line.startsWith("---");
    }

    private boolean isContentLine(String line) {
        return !line.startsWith("\\") && !line.startsWith("@@") && !line.startsWith("diff");
    }

    @Getter
    public static class DiffStats {
        private final int addedLines;
        private final int removedLines;

        public DiffStats(int addedLines, int removedLines) {
            this.addedLines = addedLines;
            this.removedLines = removedLines;
        }
    }
}
