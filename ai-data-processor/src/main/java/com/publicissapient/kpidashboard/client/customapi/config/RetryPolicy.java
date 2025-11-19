package com.publicissapient.kpidashboard.client.customapi.config;

import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
public class RetryPolicy {
    private int maxAttempts;
    private int minBackoffDuration;
    private TimeUnit minBackoffTimeUnit;
}
