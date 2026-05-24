package com.pharma.inventory.scheduler;

import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InventoryAdjustmentScheduler {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;

    /** Runs every hour; flips inTransit → false for adjustments older than 2 days. */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireInTransitAdjustments() {
        inventoryAdjustmentRepository.expireOldInTransitAdjustments(LocalDateTime.now().minusDays(2));
    }
}
