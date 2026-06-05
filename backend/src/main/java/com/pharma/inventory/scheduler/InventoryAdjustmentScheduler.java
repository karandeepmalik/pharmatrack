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

    /** Runs every hour; flips inTransit → false for each adjustment whose transitDays have elapsed. */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireInTransitAdjustments() {
        LocalDateTime now = LocalDateTime.now();
        var expired = inventoryAdjustmentRepository.findAllActiveInTransit().stream()
                .filter(a -> a.getAdjustedAt().plusDays(a.getTransitDays()).isBefore(now))
                .peek(a -> a.setInTransit(false))
                .toList();
        if (!expired.isEmpty()) {
            inventoryAdjustmentRepository.saveAll(expired);
        }
    }
}
