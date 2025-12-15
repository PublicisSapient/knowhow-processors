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
