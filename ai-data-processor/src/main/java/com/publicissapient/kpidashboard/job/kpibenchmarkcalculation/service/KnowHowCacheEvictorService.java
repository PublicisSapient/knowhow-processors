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

/**
 * Service interface for managing KnowHOW application cache eviction. Provides functionality to
 * clear specific caches when benchmark data is updated.
 *
 * @author kunkambl
 */
public interface KnowHowCacheEvictorService {

	/**
	 * Evicts the specified cache from the KnowHOW application. Used to ensure fresh data is loaded
	 * after benchmark calculations.
	 *
	 * @param cacheName the name of the cache to evict
	 */
	void evictCache(String cacheName);
}
