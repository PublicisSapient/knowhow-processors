package com.publicissapient.knowhow.processor.scm.adapter;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.kpidashboard.common.model.scm.Repository;

import java.util.List;

public interface ScmToolAdapter {
    List<Repository> fetchRepositories(ScanRequest scanRequest);
    void setActiveBranches(List<Repository> repositories);
}
