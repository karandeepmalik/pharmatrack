package com.pharma.inventory.service;

import com.pharma.inventory.entity.InventoryAdjustment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Computes settled (actually dispatchable) stock by excluding quantity from ADD adjustments
 * that are still in transit and haven't arrived yet as of a given point in time.
 */
final class InTransitStock {

    private InTransitStock() {}

    static int settled(int rawQuantity, List<InventoryAdjustment> activeInTransit, LocalDateTime asOf) {
        int transit = activeInTransit.stream()
                .filter(a -> a.getAdjustedAt().isBefore(asOf)
                        && a.getAdjustedAt().plusDays(a.getTransitDays()).isAfter(asOf))
                .mapToInt(InventoryAdjustment::getQuantity)
                .sum();
        return Math.max(0, rawQuantity - transit);
    }
}
