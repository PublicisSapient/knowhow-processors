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

package com.publicissapient.kpidashboard.job.kpibenchmarkcalculation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.client.customapi.KnowHOWClient;

/**
 * Implementation of KnowHowCacheEvictorService for managing cache eviction. Uses KnowHOW client to
 * communicate with the main application for cache management.
 *
 * @author kunkambl
 */
@Service
@RequiredArgsConstructor
public class KnowHowCacheEvictorService {

	private final KnowHOWClient knowHOWClient;

    /**
     * Evicts the specified cache from the KnowHOW application. Used to ensure fresh data is loaded
     * after benchmark calculations.
     *
     * @param cacheName the name of the cache to evict
     */
	public void evictCache(String cacheName) {
		knowHOWClient.evictKnowHowCache(cacheName);
	}
}
