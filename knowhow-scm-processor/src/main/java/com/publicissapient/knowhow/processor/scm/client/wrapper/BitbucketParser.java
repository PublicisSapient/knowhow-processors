package com.publicissapient.knowhow.processor.scm.client.wrapper;

import java.util.List;

import com.publicissapient.kpidashboard.common.model.scm.CommitDetails;
import com.publicissapient.kpidashboard.common.model.scm.MergeRequests;

public interface BitbucketParser {
     List<CommitDetails.FileChange> parseDiffToFileChanges(String diffContent);

     MergeRequests.PullRequestStats parsePRDiffToFileChanges(String diffContent);
}
