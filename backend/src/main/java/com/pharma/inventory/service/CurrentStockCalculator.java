package com.pharma.inventory.service;

import com.pharma.inventory.entity.Inventory;
import com.pharma.inventory.entity.InventoryAdjustment;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import com.pharma.inventory.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes current dispatchable ("settled") stock per (medicine, inventoryType) bucket for a
 * user via the same forward-reconstruction ReportService uses for historical reports — summing
 * real InventoryAdjustment and non-rejected Transaction records — rather than trusting the
 * cached Inventory.quantity field.
 *
 * Inventory.quantity is still the system of record for storage/mutation (submit()/adjustInventory()
 * update it directly), but it can silently drift from what the adjustment/transaction ledger
 * actually reconstructs to — e.g. stock entered directly into the Inventory table without a
 * matching InventoryAdjustment record. Deriving "how much can be dispatched right now" from the
 * ledger instead means it can never disagree with what the daily report shows, and it
 * self-corrects on every call since it's always recomputed from full history rather than an
 * incrementally-mutated cache.
 */
@Component
@RequiredArgsConstructor
public class CurrentStockCalculator {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public int settledQuantity(Long userId, Long medicineId, Inventory.InventoryType type) {
        return settledQuantitiesForUser(userId).getOrDefault(bucketKey(medicineId, type), 0);
    }

    /** Settled quantity for every (medicineId, inventoryType) bucket the given user currently holds. */
    @Transactional(readOnly = true)
    public Map<String, Integer> settledQuantitiesForUser(Long userId) {
        LocalDateTime now = LocalDateTime.now(IST);

        List<InventoryAdjustment> adjustments = inventoryAdjustmentRepository.findAllUpToForUser(userId, now);
        List<Transaction> transactions = transactionRepository.findNonRejectedSubmittedUpToForUser(
                userId, Transaction.TransactionStatus.REJECTED, now);

        Map<String, Integer> netQty = new HashMap<>();
        Map<String, Integer> inTransit = new HashMap<>();

        for (InventoryAdjustment adj : adjustments) {
            String key = bucketKey(adj.getMedicine().getId(), adj.getInventoryType());
            int delta = "ADD".equals(adj.getAdjustmentType()) ? adj.getQuantity() : -adj.getQuantity();
            netQty.merge(key, delta, Integer::sum);

            if ("ADD".equals(adj.getAdjustmentType()) && adj.isInTransit()
                    && adj.getAdjustedAt().isBefore(now)
                    && adj.getAdjustedAt().plusDays(adj.getTransitDays()).isAfter(now)) {
                inTransit.merge(key, adj.getQuantity(), Integer::sum);
            }
        }

        for (Transaction tx : transactions) {
            Inventory.InventoryType type = tx.getInventoryType() != null
                    ? tx.getInventoryType() : Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
            String key = bucketKey(tx.getMedicine().getId(), type);
            netQty.merge(key, -tx.getQuantity(), Integer::sum);
        }

        Map<String, Integer> settled = new HashMap<>();
        for (Map.Entry<String, Integer> e : netQty.entrySet()) {
            int transitQty = inTransit.getOrDefault(e.getKey(), 0);
            settled.put(e.getKey(), Math.max(0, e.getValue() - transitQty));
        }
        return settled;
    }

    private static String bucketKey(Long medicineId, Inventory.InventoryType type) {
        return medicineId + "|" + (type != null ? type.name() : Inventory.InventoryType.REGULAR_MEDICINE_STOCK.name());
    }
}
