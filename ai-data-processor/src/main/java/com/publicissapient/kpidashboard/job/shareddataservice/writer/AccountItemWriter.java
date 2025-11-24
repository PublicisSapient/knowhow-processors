package com.publicissapient.kpidashboard.job.shareddataservice.writer;

import com.publicissapient.kpidashboard.job.shareddataservice.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.job.shareddataservice.service.AIUsageStatisticsService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.List;

@Slf4j
@AllArgsConstructor
public class AccountItemWriter implements ItemWriter<AIUsageStatistics> {
    private final AIUsageStatisticsService aiUsageStatisticsService;

    @Override
    public void write(@NonNull Chunk<? extends AIUsageStatistics> chunk) {
        log.info("Received ai usage statistics chunk items for inserting  into database with size: {}", chunk.size());
        aiUsageStatisticsService.saveAll((List.copyOf(chunk.getItems())));
    }
}
