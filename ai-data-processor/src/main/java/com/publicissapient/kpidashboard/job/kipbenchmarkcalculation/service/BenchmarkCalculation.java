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

package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.service;

import java.util.Collections;
import java.util.List;

public class BenchmarkCalculation {
	public static double percentile(List<Double> values, double percentile) {

		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("Values list cannot be null or empty");
		}

		Collections.sort(values);
		int n = values.size();
		int rank = (int) Math.ceil((percentile / 100.0) * n);
		rank = Math.min(rank, n) - 1;

		return values.get(rank);
	}
}
