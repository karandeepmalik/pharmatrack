package com.pharma.medicinestock.service;

import com.pharma.medicinestock.entity.MedicineStock;
import com.pharma.medicinestock.entity.MedicineStockAdjustment;
import com.pharma.medicinestock.entity.Transaction;
import com.pharma.medicinestock.repository.MedicineStockAdjustmentRepository;
import com.pharma.medicinestock.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes current dispatchable ("settled") stock per (medicine, medicineStockType) bucket for a
 * user via the same forward-reconstruction ReportService uses for historical reports — summing
 * real MedicineStockAdjustment and non-rejected Transaction records — rather than trusting the
 * cached MedicineStock.quantity field.
 *
 * MedicineStock.quantity is still the system of record for storage/mutation (submit()/adjustMedicineStock()
 * update it directly), but it can silently drift from what the adjustment/transaction ledger
 * actually reconstructs to — e.g. stock entered directly into the MedicineStock table without a
 * matching MedicineStockAdjustment record. Deriving "how much can be dispatched right now" from the
 * ledger instead means it can never disagree with what the daily report shows, and it
 * self-corrects on every call since it's always recomputed from full history rather than an
 * incrementally-mutated cache.
 */
@Component
@RequiredArgsConstructor
public class CurrentStockCalculator {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MedicineStockAdjustmentRepository medicineStockAdjustmentRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public BigDecimal settledQuantity(Long userId, Long medicineId, MedicineStock.MedicineStockType type) {
        return settledQuantitiesForUser(userId).getOrDefault(bucketKey(medicineId, type), BigDecimal.ZERO);
    }

    /** Settled quantity for every (medicineId, medicineStockType) bucket the given user currently holds. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> settledQuantitiesForUser(Long userId) {
        LocalDateTime now = LocalDateTime.now(IST);

        List<MedicineStockAdjustment> adjustments = medicineStockAdjustmentRepository.findAllUpToForUser(userId, now);
        List<Transaction> transactions = transactionRepository.findNonRejectedSubmittedUpToForUser(
                userId, Transaction.TransactionStatus.REJECTED, now);

        Map<String, BigDecimal> netQty = new HashMap<>();
        Map<String, BigDecimal> inTransit = new HashMap<>();

        for (MedicineStockAdjustment adj : adjustments) {
            String key = bucketKey(adj.getMedicine().getId(), adj.getMedicineStockType());
            BigDecimal delta = "ADD".equals(adj.getAdjustmentType()) ? adj.getQuantity() : adj.getQuantity().negate();
            netQty.merge(key, delta, BigDecimal::add);

            if ("ADD".equals(adj.getAdjustmentType()) && adj.isInTransit()
                    && adj.getAdjustedAt().isBefore(now)
                    && adj.getAdjustedAt().plusDays(adj.getTransitDays()).isAfter(now)) {
                inTransit.merge(key, adj.getQuantity(), BigDecimal::add);
            }
        }

        for (Transaction tx : transactions) {
            MedicineStock.MedicineStockType type = tx.getMedicineStockType() != null
                    ? tx.getMedicineStockType() : MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
            String key = bucketKey(tx.getMedicine().getId(), type);
            netQty.merge(key, tx.getQuantity().negate(), BigDecimal::add);
        }

        Map<String, BigDecimal> settled = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : netQty.entrySet()) {
            BigDecimal transitQty = inTransit.getOrDefault(e.getKey(), BigDecimal.ZERO);
            settled.put(e.getKey(), e.getValue().subtract(transitQty).max(BigDecimal.ZERO));
        }
        return settled;
    }

    private static String bucketKey(Long medicineId, MedicineStock.MedicineStockType type) {
        return medicineId + "|" + (type != null ? type.name() : MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK.name());
    }
}
