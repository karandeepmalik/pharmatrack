package com.pharma.medicinestock.service;

import com.pharma.medicinestock.dto.ReportResponse;
import com.pharma.medicinestock.entity.MedicineStock;
import com.pharma.medicinestock.entity.MedicineStockAdjustment;
import com.pharma.medicinestock.entity.Medicine;
import com.pharma.medicinestock.entity.Transaction;
import com.pharma.medicinestock.entity.User;
import com.pharma.medicinestock.repository.MedicineStockAdjustmentRepository;
import com.pharma.medicinestock.repository.MedicineStockRepository;
import com.pharma.medicinestock.repository.TransactionRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharma.medicinestock.dto.InventoryProjectionResponse;
import com.pharma.medicinestock.dto.SalesGraphResponse;
import com.pharma.medicinestock.dto.StockRunwayResponse;
import com.pharma.medicinestock.util.QuantityUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.pharma.medicinestock.service.ReportTextRenderer.*;
import static com.pharma.medicinestock.util.MoneyMath.amount;

/**
 * Assembles report data by reconstructing stock/sales figures from the adjustment and
 * transaction history, then delegates to {@link ReportTextRenderer} for how each figure
 * renders as text and to {@link com.pharma.medicinestock.util.MoneyMath} for how money is
 * rounded. This class owns "what the report shows," not "how a line looks" or "how a
 * rupee amount is computed."
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final MedicineStockRepository medicineStockRepository;
    private final TransactionRepository transactionRepository;
    private final MedicineStockAdjustmentRepository medicineStockAdjustmentRepository;

    /**
     * Builds a map of userId|medicineId|medicineStockType → total in-transit ADD quantity.
     * Only includes adjustments whose adjustedAt ≤ asOf AND whose transit window hasn't expired by asOf.
     * Passing LocalDateTime.now() gives "currently in transit"; passing reportDate+1.atStartOfDay() gives
     * "in transit as of the end of reportDate" for historical daily reports.
     */
    private Map<String, BigDecimal> buildInTransitMap(LocalDateTime asOf) {
        List<MedicineStockAdjustment> active = medicineStockAdjustmentRepository.findAllActiveInTransit();
        Map<String, BigDecimal> map = new java.util.HashMap<>();
        for (MedicineStockAdjustment a : active) {
            if ("ADD".equals(a.getAdjustmentType())
                    && a.getAdjustedAt().isBefore(asOf)
                    && a.getAdjustedAt().plusDays(a.getTransitDays()).isAfter(asOf)) {
                String key = a.getUser().getId() + "|" + a.getMedicine().getId() + "|" + a.getMedicineStockType().name();
                map.merge(key, a.getQuantity(), BigDecimal::add);
            }
        }
        return map;
    }

    @Timed(value = "pharmatrack.report.medicine_stock_by_user", description = "Time to build medicine-stock-by-user report")
    @Transactional(readOnly = true)
    public ReportResponse medicineStockByUser() {
        List<MedicineStock> regularRecords   = medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
        List<MedicineStock> adminStockRecords = medicineStockRepository.findAllNonZeroOrderByMedicineAndUser(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
        Map<String, BigDecimal> inTransitMap = buildInTransitMap(LocalDateTime.now(IST_ZONE));

        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT MEDICINE STOCK PER USER\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        // ── REGULAR MEDICINE STOCK section ──────────────────────────────
        sb.append("REGULAR MEDICINE STOCK\n");
        sb.append("-".repeat(22)).append("\n");
        appendMedicineStockByUserSection(sb, regularRecords, inTransitMap);

        // ── ADMIN MEDICINE STOCK section ────────────────────────────────
        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("ADMIN MEDICINE STOCK\n");
        sb.append("-".repeat(20)).append("\n");
        appendMedicineStockByUserSection(sb, adminStockRecords, inTransitMap);

        return new ReportResponse("MEDICINE_STOCK_BY_USER", nowIST(), sb.toString());
    }

    @Transactional(readOnly = true)
    public ReportResponse medicineStockValuation(LocalDate date) {
        return date == null ? medicineStockValuationCurrent() : medicineStockValuationHistorical(date);
    }

    public ReportResponse medicineStockValuation() {
        return medicineStockValuation(null);
    }

    @Transactional(readOnly = true)
    private ReportResponse medicineStockValuationCurrent() {
        List<MedicineStock> records = medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
        Map<String, BigDecimal> inTransitMap = buildInTransitMap(LocalDateTime.now(IST_ZONE));

        // Group by pharmaId → specKey → individual records (preserves per-user detail)
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, List<MedicineStock>>> pharmaSpecRecords = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, Integer>> pharmaSpecPrice = new LinkedHashMap<>();

        for (MedicineStock inv : records) {
            Medicine med = inv.getMedicine();
            Long pharmaId = med.getPharmaCompany().getId();
            pharmaNames.putIfAbsent(pharmaId, med.getPharmaCompany().getName());
            pharmaSpecRecords.computeIfAbsent(pharmaId, k -> new LinkedHashMap<>())
                             .computeIfAbsent(specKey(med), k -> new ArrayList<>())
                             .add(inv);
            pharmaSpecPrice.computeIfAbsent(pharmaId, k -> new LinkedHashMap<>())
                           .putIfAbsent(specKey(med), med.getPrice());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("MEDICINE STOCK VALUATION\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        long grandTotal = 0;

        boolean first = true;
        for (Map.Entry<Long, String> pharmaEntry : pharmaNames.entrySet()) {
            Long pharmaId = pharmaEntry.getKey();
            String pharmaName = pharmaEntry.getValue();

            appendPharmaHeading(sb, pharmaName, first);
            first = false;

            Map<String, List<MedicineStock>> specRecs  = pharmaSpecRecords.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Integer>         specPrice = pharmaSpecPrice.getOrDefault(pharmaId, Collections.emptyMap());

            for (String[] spec : specOrderFor(specRecs.keySet(), k -> specRecs.get(k).get(0).getMedicine())) {
                String key = spec[0] + "|" + spec[1];
                List<MedicineStock> entries = specRecs.getOrDefault(key, Collections.emptyList());
                if (entries.isEmpty()) continue;

                BigDecimal totalQty = entries.stream().map(MedicineStock::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                int price = specPrice.getOrDefault(key, 0);
                long valuation = amount(price, totalQty);
                grandTotal += valuation;

                sb.append(spec[2]).append("\n");
                for (MedicineStock inv : entries) {
                    appendUserQtyLine(sb, inv.getUser().getUsername(), inv.getUser().getId(),
                            inv.getMedicine().getId(), inv.getMedicineStockType(), inv.getQuantity(), inTransitMap);
                }
                sb.append("  Valuation: ").append(totalQty.toPlainString()).append(" units x Rs ")
                  .append(String.format("%,d", price)).append(" = Rs ")
                  .append(String.format("%,d", valuation)).append("\n\n");
            }
        }

        sb.append("-".repeat(40)).append("\n");
        sb.append("TOTAL VALUATION: Rs ").append(String.format("%,d", grandTotal)).append("\n");

        return new ReportResponse("MEDICINE_STOCK_VALUATION", nowIST(), sb.toString());
    }

    /**
     * Forward reconstruction: computes stock as of the target date by accumulating all
     * ADD adjustments, subtracting REMOVE adjustments, and subtracting approved transactions,
     * all up to and including the target date. Stock naturally varies per date.
     */
    @Transactional(readOnly = true)
    private ReportResponse medicineStockValuationHistorical(LocalDate date) {
        LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();

        List<MedicineStockAdjustment> adjUpTo = medicineStockAdjustmentRepository.findAllUpTo(endExclusive);
        List<Transaction> txUpTo = transactionRepository.findNonRejectedSubmittedUpTo(
                Transaction.TransactionStatus.REJECTED, endExclusive);

        record MedUserKey(Long userId, Long medicineId) {}
        record MedUserMeta(User user, Medicine medicine) {}
        record UserQty(Long userId, BigDecimal qty) {}

        Map<MedUserKey, BigDecimal> qtyMap = new LinkedHashMap<>();
        Map<MedUserKey, MedUserMeta> metaMap = new LinkedHashMap<>();

        // Apply REGULAR adjustments up to target date: ADD increases stock, REMOVE decreases it
        for (MedicineStockAdjustment adj : adjUpTo) {
            MedicineStock.MedicineStockType t = adj.getMedicineStockType();
            if (t != null && t != MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK) continue;
            MedUserKey k = new MedUserKey(adj.getUser().getId(), adj.getMedicine().getId());
            BigDecimal delta = "ADD".equals(adj.getAdjustmentType()) ? adj.getQuantity() : adj.getQuantity().negate();
            qtyMap.merge(k, delta, BigDecimal::add);
            metaMap.putIfAbsent(k, new MedUserMeta(adj.getUser(), adj.getMedicine()));
        }

        // Subtract REGULAR approved transactions (dispatches) up to target date
        for (Transaction tx : txUpTo) {
            MedicineStock.MedicineStockType t = tx.getMedicineStockType() != null
                    ? tx.getMedicineStockType() : MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
            if (t != MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK) continue;
            MedUserKey k = new MedUserKey(tx.getSubmittedBy().getId(), tx.getMedicine().getId());
            qtyMap.merge(k, tx.getQuantity().negate(), BigDecimal::add);
            metaMap.putIfAbsent(k, new MedUserMeta(tx.getSubmittedBy(), tx.getMedicine()));
        }

        // In-transit map: ADD adjustments still within their transit window on the target date
        Map<String, BigDecimal> inTransitMap = new java.util.HashMap<>();
        for (MedicineStockAdjustment adj : adjUpTo) {
            if ("ADD".equals(adj.getAdjustmentType()) && (adj.isWasInTransit() || adj.isInTransit())
                    && adj.getAdjustedAt().plusDays(adj.getTransitDays()).isAfter(endExclusive)) {
                MedicineStock.MedicineStockType t = adj.getMedicineStockType();
                String typeStr = t != null ? t.name() : "REGULAR_MEDICINE_STOCK";
                String key = adj.getUser().getId() + "|" + adj.getMedicine().getId() + "|" + typeStr;
                inTransitMap.merge(key, adj.getQuantity(), BigDecimal::add);
            }
        }

        // Group by pharma → specKey
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, List<UserQty>>> pharmaSpecUserQty = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, Medicine>> pharmaSpecMedicine = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, Map<Long, String>>> pharmaSpecUsernames = new LinkedHashMap<>();

        for (Map.Entry<MedUserKey, BigDecimal> entry : qtyMap.entrySet()) {
            MedUserKey k = entry.getKey();
            BigDecimal qty = entry.getValue();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            MedUserMeta meta = metaMap.get(k);
            if (meta == null) continue;
            Medicine med = meta.medicine();
            Long pharmaId = med.getPharmaCompany().getId();
            String sk = specKey(med);

            pharmaNames.putIfAbsent(pharmaId, med.getPharmaCompany().getName());
            pharmaSpecMedicine.computeIfAbsent(pharmaId, x -> new LinkedHashMap<>()).putIfAbsent(sk, med);
            pharmaSpecUsernames.computeIfAbsent(pharmaId, x -> new LinkedHashMap<>())
                               .computeIfAbsent(sk, x -> new LinkedHashMap<>())
                               .put(k.userId(), meta.user().getUsername());
            pharmaSpecUserQty.computeIfAbsent(pharmaId, x -> new LinkedHashMap<>())
                             .computeIfAbsent(sk, x -> new java.util.ArrayList<>())
                             .add(new UserQty(k.userId(), qty));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("MEDICINE STOCK VALUATION\n");
        sb.append("As of: ").append(date.format(DATE_FMT)).append("\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        long grandTotal = 0;

        boolean first = true;
        for (Map.Entry<Long, String> pharmaEntry : pharmaNames.entrySet()) {
            Long pharmaId = pharmaEntry.getKey();
            String pharmaName = pharmaEntry.getValue();
            appendPharmaHeading(sb, pharmaName, first);
            first = false;

            Map<String, List<UserQty>>    specUserQty  = pharmaSpecUserQty.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Medicine>          specMed      = pharmaSpecMedicine.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Map<Long, String>> specUsernames = pharmaSpecUsernames.getOrDefault(pharmaId, Collections.emptyMap());

            for (String[] spec : specOrderFor(specUserQty.keySet(), specMed::get)) {
                String sk = spec[0] + "|" + spec[1];
                List<UserQty> userQtys = specUserQty.getOrDefault(sk, Collections.emptyList());
                if (userQtys.isEmpty()) continue;
                Medicine med = specMed.get(sk);
                Map<Long, String> usernames = specUsernames.getOrDefault(sk, Collections.emptyMap());

                sb.append(spec[2]).append("\n");
                BigDecimal totalQty = BigDecimal.ZERO;
                for (UserQty uq : userQtys) {
                    Long uid = uq.userId();
                    BigDecimal qty = uq.qty();
                    totalQty = totalQty.add(qty);
                    String uname = usernames.getOrDefault(uid, "unknown");
                    String itKey = uid + "|" + med.getId() + "|REGULAR_MEDICINE_STOCK";
                    BigDecimal transit = inTransitMap.getOrDefault(itKey, BigDecimal.ZERO);
                    sb.append("  ").append(uname).append(": ");
                    if (transit.compareTo(BigDecimal.ZERO) > 0) {
                        sb.append(qty.subtract(transit).toPlainString()).append(" + ")
                          .append(transit.toPlainString()).append(" (in transit)");
                    } else {
                        sb.append(qty.toPlainString());
                    }
                    sb.append("\n");
                }
                int price = med.getPrice();
                long valuation = amount(price, totalQty);
                grandTotal += valuation;
                sb.append("  Valuation: ").append(totalQty.toPlainString()).append(" units x Rs ")
                  .append(String.format("%,d", price)).append(" = Rs ")
                  .append(String.format("%,d", valuation)).append("\n\n");
            }
        }

        sb.append("-".repeat(40)).append("\n");
        sb.append("TOTAL VALUATION: Rs ").append(String.format("%,d", grandTotal)).append("\n");

        return new ReportResponse("MEDICINE_STOCK_VALUATION", nowIST(), sb.toString());
    }

    /** Convenience overload — defaults to today only, no filters. */
    public ReportResponse todaySales() { return todaySales(todayIST(), todayIST(), null, null); }

    /**
     * @param username   optional exact submittedBy username filter (null/blank/"ALL" = no filter)
     * @param medicineId optional exact medicine filter
     */
    @Timed(value = "pharmatrack.report.today_sales", description = "Time to build sales report")
    @Transactional(readOnly = true)
    public ReportResponse todaySales(LocalDate from, LocalDate to, String username, Long medicineId) {
        LocalDate effectiveFrom = from != null ? from : todayIST();
        LocalDate effectiveTo   = to   != null ? to   : todayIST();

        LocalDateTime start = effectiveFrom.atStartOfDay();
        LocalDateTime end   = effectiveTo.plusDays(1).atStartOfDay();

        boolean hasUsernameFilter = username != null && !username.isBlank() && !"ALL".equalsIgnoreCase(username);

        List<Transaction> txList = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end)
                .stream()
                .filter(tx -> tx.getMedicineStockType() != MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                .filter(tx -> !hasUsernameFilter || tx.getSubmittedBy().getUsername().equals(username))
                .filter(tx -> medicineId == null || tx.getMedicine().getId().equals(medicineId))
                .toList();

        StringBuilder sb = new StringBuilder();
        if (effectiveFrom.equals(effectiveTo)) {
            sb.append("SALES REPORT - ").append(effectiveFrom.format(DATE_FMT)).append("\n");
        } else {
            sb.append("SALES REPORT - ").append(effectiveFrom.format(DATE_FMT))
              .append(" to ").append(effectiveTo.format(DATE_FMT)).append("\n");
        }
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        if (txList.isEmpty()) {
            sb.append(effectiveFrom.equals(effectiveTo) ? "No sales recorded today.\n" : "No sales recorded in this period.\n");
            return new ReportResponse("SALES_REPORT", nowIST(), sb.toString());
        }

        // Group by user full name
        LinkedHashMap<String, List<Transaction>> byUser = new LinkedHashMap<>();
        for (Transaction tx : txList) {
            byUser.computeIfAbsent(tx.getSubmittedBy().getFullName(), k -> new ArrayList<>()).add(tx);
        }

        long grandTotal = 0;

        for (Map.Entry<String, List<Transaction>> entry : byUser.entrySet()) {
            sb.append(entry.getKey()).append(":\n");
            long userTotal = 0;
            for (Transaction tx : entry.getValue()) {
                Medicine med = tx.getMedicine();
                int price = tx.getPricePerUnit() != null ? tx.getPricePerUnit() : med.getPrice();
                long lineAmount = amount(price, tx.getQuantity());
                userTotal += lineAmount;

                int specInt = med.getSpecification().intValue();
                String specLabel = med.getType() == Medicine.MedicineType.VIAL
                        ? specInt + " ml"
                        : specInt + " mg";

                String txUsername = tx.getSubmittedBy().getUsername();
                String notes = (tx.getNotes() != null && !tx.getNotes().isBlank())
                        ? tx.getNotes() : "";

                sb.append("  ").append(tx.getSubmittedAt().toLocalDate().format(DATE_FMT)).append("  ")
                  .append(txUsername).append("  ").append(tx.getQuantity().toPlainString()).append(" x ").append(specLabel);
                if (!notes.isBlank()) {
                    sb.append("  ").append(notes);
                }
                sb.append("\n");
                sb.append("    Amount: Rs ").append(String.format("%,d", lineAmount)).append("\n");
            }
            sb.append("  Subtotal: Rs ").append(String.format("%,d", userTotal)).append("\n\n");
            grandTotal += userTotal;
        }

        sb.append("-".repeat(40)).append("\n");
        sb.append("TOTAL: Rs ").append(String.format("%,d", grandTotal)).append("\n");

        return new ReportResponse("SALES_REPORT", nowIST(), sb.toString());
    }

    /**
     * Generates the daily report for the given date (or today if null).
     *
     * Structure:
     *   MEDICINE STOCK section        — REGULAR medicine stock per spec per user (forward reconstruction)
     *   ADMIN MEDICINE STOCK section  — ADMIN_STOCK medicine stock per spec per user (forward reconstruction)
     *   DAILY TRANSACTION SUMMARY — approved transactions on that date
     */
    @Transactional(readOnly = true)
    public ReportResponse dailyReport(LocalDate date) {
        LocalDate reportDate = (date != null) ? date : todayIST();
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end   = reportDate.plusDays(1).atStartOfDay();

        // Forward reconstruction: adjustments + all non-rejected txs by submittedAt.
        // Uses submittedAt (not approvedAt) because the MedicineStock table deducts stock at submission time.
        List<MedicineStockAdjustment> adjUpTo = medicineStockAdjustmentRepository.findAllUpTo(end);
        List<Transaction> txApprovedUpTo = transactionRepository.findNonRejectedSubmittedUpTo(
                Transaction.TransactionStatus.REJECTED, end);

        // Daily summary sections (transactions and adjustments ON the report date)
        List<Transaction> txList = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end);
        List<MedicineStockAdjustment> adjustments = medicineStockAdjustmentRepository.findByDateRange(start, end);

        // Build stock maps keyed by "userId|medicineId"
        Map<String, BigDecimal> regularQty = new LinkedHashMap<>();
        Map<String, BigDecimal> adminQty   = new LinkedHashMap<>();
        Map<String, User>     userByKey = new LinkedHashMap<>();
        Map<String, Medicine> medByKey  = new LinkedHashMap<>();

        for (MedicineStockAdjustment adj : adjUpTo) {
            MedicineStock.MedicineStockType t = adj.getMedicineStockType() != null
                    ? adj.getMedicineStockType() : MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
            String key = adj.getUser().getId() + "|" + adj.getMedicine().getId();
            BigDecimal delta = "ADD".equals(adj.getAdjustmentType()) ? adj.getQuantity() : adj.getQuantity().negate();
            if (t == MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK) {
                regularQty.merge(key, delta, BigDecimal::add);
            } else if (t == MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK) {
                adminQty.merge(key, delta, BigDecimal::add);
            }
            userByKey.putIfAbsent(key, adj.getUser());
            medByKey.putIfAbsent(key, adj.getMedicine());
        }

        for (Transaction tx : txApprovedUpTo) {
            MedicineStock.MedicineStockType t = tx.getMedicineStockType() != null
                    ? tx.getMedicineStockType() : MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
            String key = tx.getSubmittedBy().getId() + "|" + tx.getMedicine().getId();
            if (t == MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK) {
                regularQty.merge(key, tx.getQuantity().negate(), BigDecimal::add);
            } else if (t == MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK) {
                adminQty.merge(key, tx.getQuantity().negate(), BigDecimal::add);
            }
            userByKey.putIfAbsent(key, tx.getSubmittedBy());
            medByKey.putIfAbsent(key, tx.getMedicine());
        }

        List<MedicineStock> regularRecords   = buildMedicineStockList(regularQty, userByKey, medByKey,
                MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
        List<MedicineStock> adminStockRecords = buildMedicineStockList(adminQty, userByKey, medByKey,
                MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);

        // In-transit: ADD adjustments still within their transit window on the report date
        Map<String, BigDecimal> inTransitMap = new java.util.HashMap<>();
        for (MedicineStockAdjustment adj : adjUpTo) {
            if ("ADD".equals(adj.getAdjustmentType()) && (adj.isWasInTransit() || adj.isInTransit())
                    && adj.getAdjustedAt().plusDays(adj.getTransitDays()).isAfter(end)) {
                MedicineStock.MedicineStockType t = adj.getMedicineStockType();
                String typeStr = t != null ? t.name() : "REGULAR_MEDICINE_STOCK";
                String key = adj.getUser().getId() + "|" + adj.getMedicine().getId() + "|" + typeStr;
                inTransitMap.merge(key, adj.getQuantity(), BigDecimal::add);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("DAILY REPORT - ").append(reportDate.format(DATE_FMT)).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        // ── REGULAR MEDICINE STOCK section ────────────────────────────
        sb.append("REGULAR MEDICINE STOCK\n");
        sb.append("-".repeat(22)).append("\n");
        appendMedicineStockSection(sb, regularRecords, inTransitMap);

        // ── ADMIN MEDICINE STOCK section ───────────────────────────────
        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("ADMIN MEDICINE STOCK\n");
        sb.append("-".repeat(20)).append("\n");
        appendMedicineStockAdminSection(sb, adminStockRecords, inTransitMap);

        // ── DAILY TRANSACTION SUMMARY ─────────────────────────────────
        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("DAILY TRANSACTION SUMMARY\n");
        sb.append("-".repeat(25)).append("\n");

        List<Transaction> regularTx = txList.stream()
                .filter(tx -> tx.getMedicineStockType() != MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                .toList();
        List<Transaction> adminTx = txList.stream()
                .filter(tx -> tx.getMedicineStockType() == MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                .toList();

        if (txList.isEmpty()) {
            sb.append("(no transactions today)\n");
        } else {
            if (!regularTx.isEmpty()) {
                sb.append("\nRegular Stock Transactions\n");
                sb.append("-".repeat(26)).append("\n");
                for (Transaction tx : regularTx) {
                    appendTransactionLine(sb, tx);
                }
            }
            if (!adminTx.isEmpty()) {
                sb.append("\nAdmin Stock Transactions\n");
                sb.append("-".repeat(24)).append("\n");
                for (Transaction tx : adminTx) {
                    appendTransactionLine(sb, tx);
                }
            }
        }

        // ── MedicineStock adjustments ─────────────────────────────────────
        List<MedicineStockAdjustment> regular = adjustments.stream()
                .filter(a -> !a.isInternalMovement()).toList();
        List<MedicineStockAdjustment> internal = adjustments.stream()
                .filter(MedicineStockAdjustment::isInternalMovement).toList();

        if (!regular.isEmpty()) {
            sb.append("\n");
            for (MedicineStockAdjustment a : regular) {
                appendAdjustmentLine(sb, a);
            }
        }

        if (!internal.isEmpty()) {
            sb.append("\nInternal Movement\n");
            sb.append("-".repeat(17)).append("\n");
            for (MedicineStockAdjustment a : internal) {
                appendAdjustmentLine(sb, a);
            }
        }

        return new ReportResponse("DAILY_REPORT", nowIST(), sb.toString());
    }

    /** Convenience overload — defaults to today. */
    public ReportResponse dailyReport() {
        return dailyReport(null);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private List<MedicineStock> buildMedicineStockList(Map<String, BigDecimal> qtyMap,
                                               Map<String, User> userByKey,
                                               Map<String, Medicine> medByKey,
                                               MedicineStock.MedicineStockType type) {
        List<MedicineStock> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : qtyMap.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ZERO) <= 0) continue;
            User user = userByKey.get(e.getKey());
            Medicine med = medByKey.get(e.getKey());
            if (user == null || med == null) continue;
            MedicineStock inv = new MedicineStock();
            inv.setUser(user);
            inv.setMedicine(med);
            inv.setQuantity(e.getValue());
            inv.setMedicineStockType(type);
            result.add(inv);
        }
        return result;
    }

    @Timed(value = "pharmatrack.report.sales_graph", description = "Time to build the sales graph response")
    @Transactional(readOnly = true)
    public SalesGraphResponse salesGraph(String period, LocalDate from, LocalDate to, String medicineStockType) {
        if (from == null) from = todayIST().minusDays(29);
        if (to == null) to = todayIST();
        if (period == null || period.isBlank()) period = "daily";

        boolean hasStockTypeFilter = medicineStockType != null && !medicineStockType.isBlank()
                && !"ALL".equalsIgnoreCase(medicineStockType);
        MedicineStock.MedicineStockType stockTypeFilter = hasStockTypeFilter
                ? MedicineStock.MedicineStockType.valueOf(medicineStockType) : null;

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Transaction> txns = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end)
                .stream()
                .filter(tx -> stockTypeFilter == null || tx.getMedicineStockType() == stockTypeFilter)
                .toList();

        // Spec order: DAILY_SPEC_ORDER first (consistent colours), unknown specs appended
        LinkedHashSet<String> specOrder = new LinkedHashSet<>();
        for (String[] spec : DAILY_SPEC_ORDER) specOrder.add(spec[2]);

        // Map<periodKey, Map<specName, Agg{qty, val}>>
        Map<String, Map<String, SalesAgg>> grouped = new LinkedHashMap<>();
        for (Transaction t : txns) {
            String key = groupKey(t.getSubmittedAt().toLocalDate(), period);
            String specName = specDisplayName(t.getMedicine());
            specOrder.add(specName);
            SalesAgg agg = grouped.computeIfAbsent(key, k -> new LinkedHashMap<>())
                   .computeIfAbsent(specName, s -> new SalesAgg());
            int price = t.getPricePerUnit() != null ? t.getPricePerUnit() : t.getMedicine().getPrice();
            agg.qty = agg.qty.add(t.getQuantity());
            agg.value += amount(price, t.getQuantity());
        }

        List<String> orderedSpecs = new ArrayList<>(specOrder);

        List<SalesGraphResponse.DataPoint> dataPoints = new ArrayList<>();
        for (String key : allPeriodKeys(period, from, to)) {
            Map<String, SalesAgg> specMap = grouped.getOrDefault(key, Collections.emptyMap());
            BigDecimal totalQty = BigDecimal.ZERO;
            long totalVal = 0;
            List<SalesGraphResponse.SpecBreakdown> specs = new ArrayList<>();
            for (String specName : orderedSpecs) {
                SalesAgg agg = specMap.getOrDefault(specName, new SalesAgg());
                specs.add(new SalesGraphResponse.SpecBreakdown(specName, agg.qty, agg.value));
                totalQty = totalQty.add(agg.qty);
                totalVal += agg.value;
            }
            dataPoints.add(new SalesGraphResponse.DataPoint(
                    keyToLabel(key, period), totalQty, totalVal, specs));
        }

        return new SalesGraphResponse(period, dataPoints);
    }

    /** How many past weeks of history feed the regression and appear on the chart alongside the forecast. */
    private static final int PROJECTION_HISTORY_WEEKS = 26;

    /** Tablet strips are 10 tablets each — see {@link #projectionMgEquivalent}. */
    private static final int TABLETS_PER_STRIP = 10;

    /** Fallback vial concentration when a medicine record has none set, matching {@link ReportTextRenderer#vialConc}. */
    private static final double DEFAULT_VIAL_CONCENTRATION_MG_PER_ML = 20.0;

    /**
     * Trend-based projection of future weekly demand (and the revenue that implies at
     * historical average price — never called "profit": this app has no cost/purchase-price
     * data, see {@link InventoryProjectionResponse}). Fits exactly two trends — "Total
     * Oral" (every tablet, across every strength and every pharma company) and "Total
     * Injectable" (every vial, across every spec and every pharma company) — against the last
     * {@link #PROJECTION_HISTORY_WEEKS} weeks of approved, non-admin-stock sales, via {@link
     * #linearRegression} (Holt's linear trend under the hood — see its Javadoc). Brand/pharma
     * company is deliberately not a grouping dimension: the business question is "how much oral
     * vs injectable product do we need," not a per-brand breakdown. The trend is fit on mg only, never on a raw native
     * count: a merged line mixes specs with different native-unit-to-mg ratios (a 10ml and a
     * 5ml vial are both "1 vial" but not the same mg), so there's no single meaningful "count"
     * to fit against. The frontend derives an equivalent strip/vial count
     * for display by dividing the mg series by whichever reference spec (e.g. "30mg strips" or
     * "8ml vials") the admin has chosen — a simple scalar transform of the one real series,
     * not a second independently-fit one.
     *
     * @param weeksAhead how many future weeks to project, clamped to [1, 26]
     * @param cities     optional city filter, matched case-insensitively against the submitting
     *                   user's {@code location}; null/empty means every city ("Overall"). This is
     *                   a best-effort proxy — real routing sometimes crosses cities (e.g. Bengaluru
     *                   covering Mumbai), which this attribution can't capture from current data.
     */
    @Timed(value = "pharmatrack.report.inventory_projection", description = "Time to build the inventory projection response")
    @Transactional(readOnly = true)
    public InventoryProjectionResponse inventoryProjection(Integer weeksAhead, List<String> cities) {
        int horizon = weeksAhead == null ? 8 : Math.max(1, Math.min(weeksAhead, 26));

        LocalDate thisWeekMonday = todayIST().with(DayOfWeek.MONDAY);
        LocalDate firstWeekMonday = thisWeekMonday.minusWeeks(PROJECTION_HISTORY_WEEKS - 1);
        LocalDateTime start = firstWeekMonday.atStartOfDay();
        LocalDateTime end = todayIST().plusDays(1).atStartOfDay();

        Set<String> cityFilter = (cities == null || cities.isEmpty()) ? Set.of()
                : cities.stream().map(c -> c.trim().toLowerCase()).filter(c -> !c.isBlank())
                        .collect(java.util.stream.Collectors.toSet());

        List<Transaction> txList = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end)
                .stream()
                .filter(tx -> tx.getMedicineStockType() != MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                .filter(tx -> cityFilter.isEmpty() || cityFilter.contains(
                        Optional.ofNullable(tx.getSubmittedBy().getLocation()).orElse("").trim().toLowerCase()))
                .toList();

        List<String> weekKeys = new ArrayList<>();
        for (int i = 0; i < PROJECTION_HISTORY_WEEKS; i++) weekKeys.add(firstWeekMonday.plusWeeks(i).toString());

        // lineKey -> weekKey -> mg quantity; lineKey -> {totalMg, totalRevenue} for the line's
        // avg price per mg; lineKey -> dosage form, so the frontend knows which reference-spec
        // options apply (tablet strengths vs vial volumes).
        Map<String, Map<String, BigDecimal>> byLineWeekMg = new TreeMap<>();
        Map<String, BigDecimal> lineTotalMg = new TreeMap<>();
        Map<String, Long> lineTotalRevenue = new TreeMap<>();
        Map<String, String> lineDosageForm = new TreeMap<>();

        for (Transaction tx : txList) {
            Medicine medicine = tx.getMedicine();
            String line = projectionLineKey(medicine);
            String weekKey = tx.getSubmittedAt().toLocalDate().with(DayOfWeek.MONDAY).toString();
            int price = tx.getPricePerUnit() != null ? tx.getPricePerUnit() : medicine.getPrice();
            long lineAmount = amount(price, tx.getQuantity());
            BigDecimal mgQty = projectionMgEquivalent(medicine, tx.getQuantity());

            byLineWeekMg.computeIfAbsent(line, k -> new HashMap<>())
                    .merge(weekKey, mgQty, BigDecimal::add);
            lineTotalMg.merge(line, mgQty, BigDecimal::add);
            lineTotalRevenue.merge(line, lineAmount, Long::sum);
            lineDosageForm.putIfAbsent(line, medicine.getType().name());
        }

        List<String> forecastLabels = new ArrayList<>();
        for (int i = 1; i <= horizon; i++) {
            forecastLabels.add(thisWeekMonday.plusWeeks(i).format(DateTimeFormatter.ofPattern("d MMM")));
        }
        List<String> historicalLabels = weekKeys.stream()
                .map(k -> LocalDate.parse(k).format(DateTimeFormatter.ofPattern("d MMM")))
                .toList();

        List<InventoryProjectionResponse.LineProjection> lines = new ArrayList<>();
        long totalForecastRevenue = 0;

        for (String line : byLineWeekMg.keySet()) {
            Map<String, BigDecimal> weeklyMg = byLineWeekMg.get(line);
            List<BigDecimal> historicalQuantityMg = weekKeys.stream()
                    .map(k -> weeklyMg.getOrDefault(k, BigDecimal.ZERO))
                    .toList();

            double[] coeffsMg = linearRegression(historicalQuantityMg);
            double slopeMg = coeffsMg[0], interceptMg = coeffsMg[1];

            BigDecimal totalMg = lineTotalMg.getOrDefault(line, BigDecimal.ZERO);
            long totalRevenue = lineTotalRevenue.getOrDefault(line, 0L);
            BigDecimal avgPricePerMg = totalMg.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.valueOf(totalRevenue).divide(totalMg, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            List<BigDecimal> forecastQuantityMg = new ArrayList<>();
            List<Long> forecastRevenue = new ArrayList<>();
            long lineForecastRevenue = 0;
            for (int i = 0; i < horizon; i++) {
                int x = PROJECTION_HISTORY_WEEKS + i;
                BigDecimal qtyMg = QuantityUtil.round(BigDecimal.valueOf(Math.max(interceptMg + slopeMg * x, 0)));
                long revenue = avgPricePerMg.multiply(qtyMg).setScale(0, RoundingMode.HALF_UP).longValue();
                forecastQuantityMg.add(qtyMg);
                forecastRevenue.add(revenue);
                lineForecastRevenue += revenue;
            }
            totalForecastRevenue += lineForecastRevenue;

            lines.add(new InventoryProjectionResponse.LineProjection(
                    line, lineDosageForm.get(line), historicalQuantityMg, forecastQuantityMg,
                    forecastRevenue, lineForecastRevenue, slopeMg, interceptMg));
        }

        String forecastStartDate = thisWeekMonday.plusWeeks(1).toString();
        return new InventoryProjectionResponse(
                historicalLabels, forecastLabels, forecastStartDate, PROJECTION_HISTORY_WEEKS,
                lines, totalForecastRevenue);
    }

    /**
     * Groups medicines into forecast "lines" at the dosage-form level only — every tablet
     * strength from every pharma company combines into "Total Oral," every vial spec from
     * every pharma company into "Total Injectable." Brand isn't a substitution boundary here
     * (tablet strengths are interchangeable for orders; injections and tablets are not), so it
     * isn't a grouping dimension either — the business question is total oral vs. injectable
     * demand, not a per-brand split.
     */
    private String projectionLineKey(Medicine m) {
        return switch (m.getType()) {
            case TABLET -> "Total Oral";
            case VIAL -> "Total Injectable";
            default -> specDisplayName(m);
        };
    }

    /**
     * Converts a native-unit quantity to its mg-equivalent. A vial's mg content is its volume
     * (the medicine's {@code specification}, in ml) times its concentration (defaulting to
     * {@value #DEFAULT_VIAL_CONCENTRATION_MG_PER_ML} mg/ml when unset, matching {@link ReportTextRenderer#vialConc}).
     * A tablet strip is {@value #TABLETS_PER_STRIP} tablets, so its mg content is the per-tablet
     * strength (the medicine's {@code specification}, in mg) times {@value #TABLETS_PER_STRIP}.
     */
    private BigDecimal projectionMgEquivalent(Medicine m, BigDecimal nativeQty) {
        if (m.getType() == Medicine.MedicineType.VIAL) {
            double concentration = m.getConcentrationMgPerMl() != null
                    ? m.getConcentrationMgPerMl() : DEFAULT_VIAL_CONCENTRATION_MG_PER_ML;
            double mgPerVial = m.getSpecification() * concentration;
            return nativeQty.multiply(BigDecimal.valueOf(mgPerVial));
        }
        if (m.getType() == Medicine.MedicineType.TABLET) {
            double mgPerStrip = m.getSpecification() * TABLETS_PER_STRIP;
            return nativeQty.multiply(BigDecimal.valueOf(mgPerStrip));
        }
        return nativeQty.multiply(BigDecimal.valueOf(m.getSpecification()));
    }

    /** How far ahead a stock-out simulation runs before giving up and reporting "never runs out". */
    private static final int RUNWAY_CAP_DAYS = 3650; // 10 years

    /** How many days a never-depletes chart shows before being cut off, purely for a readable chart. */
    private static final int RUNWAY_NEVER_DEPLETES_CHART_DAYS = 365;

    /** Upper bound on points returned per line's chart series — downsampled evenly either way. */
    private static final int RUNWAY_CHART_POINTS = 40;

    /**
     * "Given I currently have N left, when does it run out" — for a single city (or every city
     * combined), against the same Total Oral / Total Injectable mg-regressions
     * {@link #inventoryProjection} fits. A dosage form the admin left blank is simply absent
     * from the response; one with no sales history for the selection gets
     * {@code insufficientData=true} rather than a silently-implied zero-consumption trend.
     *
     * @param city       optional single-city filter (same {@code location} matching as
     *                   {@link #inventoryProjection}); null/blank means every city combined
     * @param oralStock  current oral stock on hand, in {@code oralUnit}; null/≤0 omits oral entirely
     * @param oralUnit   {@code "MG"} or {@code "STRIPS"} (default STRIPS) — which unit {@code oralStock} is in
     * @param oralRefMg  reference tablet strength in mg (20/30/50, default 30) used to convert
     *                   strips to mg; irrelevant when {@code oralUnit} is MG
     * @param vialStock  current vial count on hand; null/≤0 omits injectable entirely
     * @param vialRefMl  reference vial volume in ml (8/10, default 8) used to convert the vial
     *                   count to mg at the fixed 20mg/ml concentration
     */
    @Timed(value = "pharmatrack.report.stock_runway", description = "Time to build the stock runway response")
    @Transactional(readOnly = true)
    public StockRunwayResponse stockRunway(String city, BigDecimal oralStock, String oralUnit, Integer oralRefMg,
                                            BigDecimal vialStock, Integer vialRefMl) {
        List<String> cityFilter = (city == null || city.isBlank()) ? null : List.of(city);
        InventoryProjectionResponse projection = inventoryProjection(1, cityFilter);

        InventoryProjectionResponse.LineProjection oralLine = null;
        InventoryProjectionResponse.LineProjection vialLine = null;
        for (InventoryProjectionResponse.LineProjection line : projection.getLines()) {
            if ("TABLET".equals(line.getDosageForm())) oralLine = line;
            else if ("VIAL".equals(line.getDosageForm())) vialLine = line;
        }

        LocalDate forecastStart = LocalDate.parse(projection.getForecastStartDate());
        LocalDate thisWeekMonday = forecastStart.minusWeeks(1);
        LocalDate today = todayIST();
        double weekIndexToday = (projection.getHistoryWeeks() - 1)
                + java.time.temporal.ChronoUnit.DAYS.between(thisWeekMonday, today) / 7.0;

        List<StockRunwayResponse.RunwayLine> lines = new ArrayList<>();

        if (oralStock != null && oralStock.compareTo(BigDecimal.ZERO) > 0) {
            int refMg = oralRefMg != null ? oralRefMg : 30;
            boolean isMg = "MG".equalsIgnoreCase(oralUnit);
            double mgPerUnit = isMg ? 1.0 : refMg * TABLETS_PER_STRIP;
            String unitLabel = isMg ? "mg" : "strips (" + refMg + "mg)";
            double slope = oralLine != null ? oralLine.getRegressionSlopeMg() : 0;
            double intercept = oralLine != null ? oralLine.getRegressionInterceptMg() : 0;
            lines.add(simulateRunway("Total Oral", unitLabel, oralStock, mgPerUnit,
                    slope, intercept, today, weekIndexToday, oralLine == null));
        }

        if (vialStock != null && vialStock.compareTo(BigDecimal.ZERO) > 0) {
            int refMl = vialRefMl != null ? vialRefMl : 8;
            double mgPerUnit = refMl * DEFAULT_VIAL_CONCENTRATION_MG_PER_ML;
            String unitLabel = "vials (" + refMl + "ml)";
            double slope = vialLine != null ? vialLine.getRegressionSlopeMg() : 0;
            double intercept = vialLine != null ? vialLine.getRegressionInterceptMg() : 0;
            lines.add(simulateRunway("Total Injectable", unitLabel, vialStock, mgPerUnit,
                    slope, intercept, today, weekIndexToday, vialLine == null));
        }

        return new StockRunwayResponse(lines);
    }

    /**
     * Simulates day-by-day depletion of {@code currentStockInUnit} against the weekly mg rate
     * {@code max(interceptMg + slopeMg * weekIndex, 0)} (the same floored linear model
     * {@link #inventoryProjection} forecasts with), where {@code weekIndex} continues forward
     * from {@code weekIndexToday} by {@code daysElapsed/7}. Finds the exact stock-out date (if
     * any) within {@link #RUNWAY_CAP_DAYS}, then builds a downsampled chart series covering
     * either the real depletion or a {@link #RUNWAY_NEVER_DEPLETES_CHART_DAYS}-day window when
     * it doesn't deplete within the cap.
     */
    private StockRunwayResponse.RunwayLine simulateRunway(String name, String unitLabel, BigDecimal currentStockInUnit,
            double mgPerUnit, double slopeMg, double interceptMg, LocalDate today, double weekIndexToday,
            boolean insufficientData) {
        double currentStockMg = currentStockInUnit.doubleValue() * mgPerUnit;

        LocalDate stockOutDate = null;
        double probe = currentStockMg;
        for (int d = 1; d <= RUNWAY_CAP_DAYS; d++) {
            double rate = Math.max(interceptMg + slopeMg * (weekIndexToday + d / 7.0), 0);
            probe = Math.max(probe - rate / 7.0, 0);
            if (probe <= 0) {
                stockOutDate = today.plusDays(d);
                break;
            }
        }

        int chartDays = stockOutDate != null
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(today, stockOutDate)
                : RUNWAY_NEVER_DEPLETES_CHART_DAYS;
        int step = Math.max(1, (chartDays + 1) / RUNWAY_CHART_POINTS);

        List<String> dateLabels = new ArrayList<>();
        List<BigDecimal> remainingQuantity = new ArrayList<>();
        double remaining = currentStockMg;
        dateLabels.add(today.format(DateTimeFormatter.ofPattern("d MMM")));
        remainingQuantity.add(QuantityUtil.round(BigDecimal.valueOf(remaining / mgPerUnit)));
        for (int d = 1; d <= chartDays; d++) {
            double rate = Math.max(interceptMg + slopeMg * (weekIndexToday + d / 7.0), 0);
            remaining = Math.max(remaining - rate / 7.0, 0);
            if (d % step == 0 || d == chartDays) {
                dateLabels.add(today.plusDays(d).format(DateTimeFormatter.ofPattern("d MMM")));
                remainingQuantity.add(QuantityUtil.round(BigDecimal.valueOf(remaining / mgPerUnit)));
            }
        }

        return new StockRunwayResponse.RunwayLine(
                name, unitLabel, QuantityUtil.round(currentStockInUnit), insufficientData,
                stockOutDate != null ? stockOutDate.toString() : null, dateLabels, remainingQuantity);
    }

    /**
     * Fits Holt's linear trend (double exponential smoothing) over x = 0..n-1 and returns it as
     * an equivalent global {slope, intercept} pair for y = intercept + slope*x, so every caller
     * (forecast loop, continuous day-stepping stock-out simulation) keeps evaluating a plain
     * line without knowing the fit underneath changed.
     *
     * <p>Unlike ordinary least squares — which weighs all 26 weeks equally — Holt's method
     * maintains a level and trend that it updates recursively as each week arrives, so recent
     * weeks dominate the fit and a demand shift a few weeks ago is reflected quickly rather than
     * being diluted by the full history. {@code alpha} (level smoothing) and {@code beta} (trend
     * smoothing) are grid-searched over {0.1..0.9} to minimize one-step-ahead forecast error on
     * the series itself, rather than fixed at arbitrary constants.
     *
     * <p>The level/trend state at the end of the series (index {@code n-1}) is {@code (level,
     * trend)}; forecasting {@code h} steps ahead is {@code level + trend*h}. Reparametrizing
     * {@code h = x - (n-1)} gives {@code intercept = level - (n-1)*trend}, {@code slope = trend}
     * — an exact algebraic restatement, not an approximation.
     */
    private static double[] linearRegression(List<BigDecimal> ySeries) {
        int n = ySeries.size();
        if (n == 0) return new double[]{0.0, 0.0};
        if (n == 1) return new double[]{0.0, ySeries.get(0).doubleValue()};

        double[] y = new double[n];
        for (int i = 0; i < n; i++) y[i] = ySeries.get(i).doubleValue();

        double bestSse = Double.MAX_VALUE;
        double bestLevel = y[0], bestTrend = y[1] - y[0];
        for (int ai = 1; ai <= 9; ai++) {
            double alpha = ai / 10.0;
            for (int bi = 1; bi <= 9; bi++) {
                double beta = bi / 10.0;
                double level = y[0];
                double trend = y[1] - y[0];
                double sse = 0;
                for (int t = 1; t < n; t++) {
                    double forecast = level + trend;
                    double err = y[t] - forecast;
                    sse += err * err;
                    double newLevel = alpha * y[t] + (1 - alpha) * (level + trend);
                    double newTrend = beta * (newLevel - level) + (1 - beta) * trend;
                    level = newLevel;
                    trend = newTrend;
                }
                if (sse < bestSse) {
                    bestSse = sse;
                    bestLevel = level;
                    bestTrend = trend;
                }
            }
        }

        double slope = bestTrend;
        double intercept = bestLevel - (n - 1) * bestTrend;
        return new double[]{slope, intercept};
    }

    /** Mutable per-(period,spec) accumulator for salesGraph() — quantity is decimal, value (money) stays whole. */
    private static final class SalesAgg {
        BigDecimal qty = BigDecimal.ZERO;
        long value = 0;
    }

    private String groupKey(LocalDate date, String period) {
        if ("monthly".equals(period)) return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        if ("weekly".equals(period)) return date.with(DayOfWeek.MONDAY).toString();
        return date.toString();
    }

    private String keyToLabel(String key, String period) {
        if ("monthly".equals(period)) {
            return LocalDate.parse(key + "-01").format(DateTimeFormatter.ofPattern("MMM yy"));
        }
        return LocalDate.parse(key).format(DateTimeFormatter.ofPattern("d MMM"));
    }

    private List<String> allPeriodKeys(String period, LocalDate from, LocalDate to) {
        List<String> keys = new ArrayList<>();
        if ("monthly".equals(period)) {
            LocalDate cur = from.withDayOfMonth(1);
            LocalDate last = to.withDayOfMonth(1);
            while (!cur.isAfter(last)) {
                keys.add(cur.format(DateTimeFormatter.ofPattern("yyyy-MM")));
                cur = cur.plusMonths(1);
            }
        } else if ("weekly".equals(period)) {
            LocalDate cur = from.with(DayOfWeek.MONDAY);
            LocalDate last = to.with(DayOfWeek.MONDAY);
            while (!cur.isAfter(last)) {
                keys.add(cur.toString());
                cur = cur.plusWeeks(1);
            }
        } else {
            LocalDate cur = from;
            while (!cur.isAfter(to)) {
                keys.add(cur.toString());
                cur = cur.plusDays(1);
            }
        }
        return keys;
    }
}
