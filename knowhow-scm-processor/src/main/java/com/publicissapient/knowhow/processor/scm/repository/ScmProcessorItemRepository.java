package com.publicissapient.knowhow.processor.scm.repository;

import com.publicissapient.knowhow.processor.scm.domain.model.ScmProcessorItem;
import com.publicissapient.kpidashboard.common.repository.generic.ProcessorItemRepository;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ScmProcessorItemRepository extends ProcessorItemRepository<ScmProcessorItem> {

    /**
     * Represents a function that accepts one input arguments and returns list of
     * GitHubProcessorItem.
     *
     * @param processorId
     *          the processor id
     * @return GitHubProcessorItem list of GitHubProcessorItem
     */
    @Query("{ 'processorId' : ?0, 'isActive': true}")
    List<ScmProcessorItem> findActiveRepos(ObjectId processorId);

    /**
     * @param processorId
     *          the processor id
     * @param toolConfigId
     *          the toolConfig id
     * @return list of GitHubProcessorItem
     */
    List<ScmProcessorItem> findByProcessorIdAndToolConfigId(ObjectId processorId, ObjectId toolConfigId);
}
