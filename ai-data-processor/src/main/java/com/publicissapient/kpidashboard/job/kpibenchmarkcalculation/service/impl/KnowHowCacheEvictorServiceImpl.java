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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.impl;

import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;
import com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service.KnowHowCacheEvictorService;

/**
 * Implementation of KnowHowCacheEvictorService for managing cache eviction. Uses KnowHOW client to
 * communicate with the main application for cache management.
 *
 * @author kunkambl
 */
@Service
public class KnowHowCacheEvictorServiceImpl implements KnowHowCacheEvictorService {
	private final KnowHOWClient knowHOWClient;

	/**
	 * Constructs the cache evictor service with KnowHOW client dependency.
	 *
	 * @param knowHOWClient client for communicating with KnowHOW application
	 */
	public KnowHowCacheEvictorServiceImpl(KnowHOWClient knowHOWClient) {
		this.knowHOWClient = knowHOWClient;
	}

	/** {@inheritDoc} */
	@Override
	public void evictCache(String cacheName) {
		knowHOWClient.evictKnowHowCache(cacheName);
	}
}
