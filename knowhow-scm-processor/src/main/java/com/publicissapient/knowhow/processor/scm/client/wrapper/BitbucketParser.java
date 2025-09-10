package com.publicissapient.knowhow.processor.scm.client.wrapper;

import java.util.List;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

public interface BitbucketParser {
     List<ScmCommits.FileChange> parseDiffToFileChanges(String diffContent);

     ScmMergeRequests.PullRequestStats parsePRDiffToFileChanges(String diffContent);
}
