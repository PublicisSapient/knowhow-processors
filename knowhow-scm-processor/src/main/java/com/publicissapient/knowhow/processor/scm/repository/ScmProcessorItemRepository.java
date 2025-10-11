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
	 *            the processor id
	 * @return GitHubProcessorItem list of GitHubProcessorItem
	 */
	@Query("{ 'processorId' : ?0, 'isActive': true}")
	List<ScmProcessorItem> findActiveRepos(ObjectId processorId);

	/**
	 * @param processorId
	 *            the processor id
	 * @param toolConfigId
	 *            the toolConfig id
	 * @return list of GitHubProcessorItem
	 */
	List<ScmProcessorItem> findByProcessorIdAndToolConfigId(ObjectId processorId, ObjectId toolConfigId);
}
