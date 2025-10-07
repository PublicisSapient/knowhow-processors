package com.publicissapient.knowhow.processor.scm.domain.model;

import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.constant.ProcessorType;
import com.publicissapient.kpidashboard.common.model.generic.Processor;
import com.publicissapient.kpidashboard.common.model.generic.ProcessorError;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.util.List;

@NoArgsConstructor
public class ScmProcessor extends Processor {
    /**
     * The constructor.
     *
     * @param processorName
     *          processorName
     * @param processorType
     *          processorType
     * @param enabled
     *          enabled
     * @param online
     *          online
     * @param errors
     *          errors
     * @param lastExecuted
     *          lastExecuted
     * @param objectId
     *          objectId
     * @param isLastSuccess
     *          isLastSuccess
     */
    @Builder(builderMethodName = "processorBuilder")
    public ScmProcessor(String processorName, ProcessorType processorType, boolean enabled, boolean online,
                           List<ProcessorError> errors, long lastExecuted, ObjectId objectId, boolean isLastSuccess) {
        super(processorName, processorType, enabled, online, errors, lastExecuted, objectId, isLastSuccess);
    }

    /**
     * This method return githubprocessor object
     *
     * @return GitHubProcessor
     */
    public static ScmProcessor prototype() {
        return ScmProcessor.processorBuilder().processorName(ProcessorConstants.SCM).online(true).enabled(true)
                .processorType(ProcessorType.SCM).lastExecuted(System.currentTimeMillis()).isLastSuccess(false).build();
    }
}
