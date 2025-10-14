package com.publicissapient.knowhow.processor.scm.factory.impl;

import com.publicissapient.knowhow.processor.scm.adapter.ScmToolAdapter;
import com.publicissapient.knowhow.processor.scm.factory.ScmToolFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GitHubFactory extends ScmToolFactory {
    @Override
    public ScmToolAdapter createScmAdapter() {
        return null;
    }
}
