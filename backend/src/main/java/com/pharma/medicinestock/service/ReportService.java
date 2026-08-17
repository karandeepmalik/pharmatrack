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

import com.pharma.medicinestock.dto.SalesGraphResponse;
import java.math.BigDecimal;
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

        // Derive pharma name from whichever list is non-empty
        String pharmaName = regularRecords.stream()
                .map(i -> i.getMedicine().getPharmaCompany().getName())
                .findFirst()
                .orElseGet(() -> adminStockRecords.stream()
                        .map(i -> i.getMedicine().getPharmaCompany().getName())
                        .findFirst()
                        .orElse("Shield FX"));

        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT MEDICINE STOCK PER USER\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        // ── REGULAR MEDICINE STOCK section ──────────────────────────────
        sb.append("REGULAR MEDICINE STOCK\n");
        sb.append("-".repeat(22)).append("\n");
        sb.append(pharmaName).append("\n");
        sb.append("-".repeat(pharmaName.length())).append("\n");
        appendMedicineStockByUserSection(sb, regularRecords, inTransitMap);

        // ── ADMIN MEDICINE STOCK section ────────────────────────────────
        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("ADMIN MEDICINE STOCK\n");
        sb.append("-".repeat(20)).append("\n");
        if (!adminStockRecords.isEmpty()) {
            String adminPharma = adminStockRecords.get(0).getMedicine().getPharmaCompany().getName();
            sb.append(adminPharma).append("\n");
            sb.append("-".repeat(adminPharma.length())).append("\n");
        }
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

        for (Map.Entry<Long, String> pharmaEntry : pharmaNames.entrySet()) {
            Long pharmaId = pharmaEntry.getKey();
            String pharmaName = pharmaEntry.getValue();

            sb.append(pharmaName).append("\n");
            sb.append("-".repeat(pharmaName.length())).append("\n");

            Map<String, List<MedicineStock>> specRecs  = pharmaSpecRecords.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Integer>         specPrice = pharmaSpecPrice.getOrDefault(pharmaId, Collections.emptyMap());

            for (String[] spec : DAILY_SPEC_ORDER) {
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

        for (Map.Entry<Long, String> pharmaEntry : pharmaNames.entrySet()) {
            Long pharmaId = pharmaEntry.getKey();
            String pharmaName = pharmaEntry.getValue();
            sb.append(pharmaName).append("\n");
            sb.append("-".repeat(pharmaName.length())).append("\n");

            Map<String, List<UserQty>>    specUserQty  = pharmaSpecUserQty.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Medicine>          specMed      = pharmaSpecMedicine.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Map<Long, String>> specUsernames = pharmaSpecUsernames.getOrDefault(pharmaId, Collections.emptyMap());

            for (String[] spec : DAILY_SPEC_ORDER) {
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

                sb.append("  ").append(txUsername).append("  ").append(tx.getQuantity().toPlainString()).append(" x ").append(specLabel);
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
        sb.append("Generated: ").append(nowIST()).append("\n");
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
