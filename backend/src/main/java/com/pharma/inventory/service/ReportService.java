package com.pharma.inventory.service;

import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.entity.Inventory;
import com.pharma.inventory.entity.InventoryAdjustment;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import com.pharma.inventory.repository.InventoryRepository;
import com.pharma.inventory.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final InventoryRepository inventoryRepository;
    private final TransactionRepository transactionRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Fixed display order for daily report — short names, no pharma prefix
    private static final List<String[]> DAILY_SPEC_ORDER = List.of(
        new String[]{"VIAL",   "10.0", "Vial 10 ml"},
        new String[]{"VIAL",   "5.0",  "Vial 5 ml"},
        new String[]{"TABLET", "50.0", "Tablet 50 mg (10 Tablets)"},
        new String[]{"TABLET", "25.0", "Tablet 25 mg (10 Tablets)"},
        new String[]{"TABLET", "12.0", "Tablet 12 mg (10 Tablets)"}
    );

    private String nowIST() {
        return ZonedDateTime.now(IST_ZONE).format(DT_FMT) + " IST";
    }

    private LocalDate todayIST() {
        return ZonedDateTime.now(IST_ZONE).toLocalDate();
    }

    private String vialConc(Medicine m) {
        Double c = m.getConcentrationMgPerMl();
        if (c == null) return "20";
        return c % 1 == 0 ? String.valueOf(c.intValue()) : String.valueOf(c);
    }

    /** Returns the type|spec key used to match DAILY_SPEC_ORDER entries. */
    private static String specKey(Medicine m) {
        return m.getType().name() + "|" + m.getSpecification();
    }

    /** Builds a map of userId|medicineId|inventoryType → total in-transit ADD quantity (≤ 2 days old). */
    private Map<String, Integer> buildInTransitMap() {
        LocalDateTime cutoff = LocalDateTime.now(IST_ZONE).minusDays(2);
        List<InventoryAdjustment> active = inventoryAdjustmentRepository.findActiveInTransitAdjustments(cutoff);
        Map<String, Integer> map = new java.util.HashMap<>();
        for (InventoryAdjustment a : active) {
            if ("ADD".equals(a.getAdjustmentType())) {
                String key = a.getUser().getId() + "|" + a.getMedicine().getId() + "|" + a.getInventoryType().name();
                map.merge(key, a.getQuantity(), Integer::sum);
            }
        }
        return map;
    }

    @Transactional(readOnly = true)
    public ReportResponse inventoryByUser() {
        List<Inventory> regularRecords   = inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        List<Inventory> adminStockRecords = inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
        Map<String, Integer> inTransitMap = buildInTransitMap();

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
        appendInventoryByUserSection(sb, regularRecords, inTransitMap);

        // ── ADMIN MEDICINE STOCK section ────────────────────────────────
        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("ADMIN MEDICINE STOCK\n");
        sb.append("-".repeat(20)).append("\n");
        if (!adminStockRecords.isEmpty()) {
            String adminPharma = adminStockRecords.get(0).getMedicine().getPharmaCompany().getName();
            sb.append(adminPharma).append("\n");
            sb.append("-".repeat(adminPharma.length())).append("\n");
        }
        appendInventoryByUserSection(sb, adminStockRecords, inTransitMap);

        return new ReportResponse("INVENTORY_BY_USER", nowIST(), sb.toString());
    }

    /**
     * Writes one user quantity line, using in-transit format when applicable.
     * Format: "  username: settled + transit (in transit)" or "  username: qty"
     */
    private void appendUserQtyLine(StringBuilder sb, Inventory inv, Map<String, Integer> inTransitMap) {
        String key = inv.getUser().getId() + "|" + inv.getMedicine().getId() + "|"
                + (inv.getInventoryType() != null ? inv.getInventoryType().name() : "REGULAR_MEDICINE_STOCK");
        int transit = inTransitMap.getOrDefault(key, 0);
        sb.append("  ").append(inv.getUser().getUsername()).append(": ");
        if (transit > 0) {
            sb.append(inv.getQuantity() - transit).append(" + ").append(transit).append(" (in transit)");
        } else {
            sb.append(inv.getQuantity());
        }
        sb.append("\n");
    }

    /**
     * Appends per-spec, per-user inventory lines for the inventory-by-user report.
     * Uses full medicine name as header; skips specs with no data.
     */
    private void appendInventoryByUserSection(StringBuilder sb, List<Inventory> records,
                                              Map<String, Integer> inTransitMap) {
        Map<String, List<Inventory>> bySpec = new LinkedHashMap<>();
        for (Inventory inv : records) {
            bySpec.computeIfAbsent(specKey(inv.getMedicine()), k -> new ArrayList<>()).add(inv);
        }

        for (String[] spec : DAILY_SPEC_ORDER) {
            String key = spec[0] + "|" + spec[1];
            List<Inventory> entries = bySpec.get(key);
            if (entries == null || entries.isEmpty()) continue;

            // Short format: "Vial 10 ml | 20 mg/ml" or "Tablet 50 mg (10 Tablets)"
            String header = "VIAL".equals(spec[0])
                    ? spec[2] + " | " + vialConc(entries.get(0).getMedicine()) + " mg/ml"
                    : spec[2];

            sb.append(header).append("\n");
            sb.append("-".repeat(35)).append("\n");
            int total = 0;
            for (Inventory inv : entries) {
                appendUserQtyLine(sb, inv, inTransitMap);
                total += inv.getQuantity();
            }
            sb.append("  TOTAL: ").append(total).append("\n\n");
        }
    }

    @Transactional(readOnly = true)
    public ReportResponse inventoryValuation() {
        List<Inventory> records = inventoryRepository.findAllNonZeroForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        Map<String, Integer> inTransitMap = buildInTransitMap();

        // Group by pharmaId → specKey → individual records (preserves per-user detail)
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, List<Inventory>>> pharmaSpecRecords = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, Integer>> pharmaSpecPrice = new LinkedHashMap<>();

        for (Inventory inv : records) {
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
        sb.append("CURRENT MEDICINE STOCK VALUATION\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        long grandTotal = 0;

        for (Map.Entry<Long, String> pharmaEntry : pharmaNames.entrySet()) {
            Long pharmaId = pharmaEntry.getKey();
            String pharmaName = pharmaEntry.getValue();

            sb.append(pharmaName).append("\n");
            sb.append("-".repeat(pharmaName.length())).append("\n");

            Map<String, List<Inventory>> specRecs  = pharmaSpecRecords.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Integer>         specPrice = pharmaSpecPrice.getOrDefault(pharmaId, Collections.emptyMap());

            for (String[] spec : DAILY_SPEC_ORDER) {
                String key = spec[0] + "|" + spec[1];
                List<Inventory> entries = specRecs.getOrDefault(key, Collections.emptyList());
                if (entries.isEmpty()) continue;

                int totalQty = entries.stream().mapToInt(Inventory::getQuantity).sum();
                int price = specPrice.getOrDefault(key, 0);
                long valuation = (long) totalQty * price;
                grandTotal += valuation;

                sb.append(spec[2]).append("\n");
                for (Inventory inv : entries) {
                    appendUserQtyLine(sb, inv, inTransitMap);
                }
                sb.append("  TOTAL: ").append(totalQty).append("\n");
                sb.append("  Price: Rs ").append(String.format("%,d", price))
                  .append("  |  Value: Rs ").append(String.format("%,d", valuation)).append("\n\n");
            }
        }

        sb.append("-".repeat(40)).append("\n");
        sb.append("TOTAL VALUATION: Rs ").append(String.format("%,d", grandTotal)).append("\n");

        return new ReportResponse("INVENTORY_VALUATION", nowIST(), sb.toString());
    }

    /** Convenience overload — defaults to today only. */
    public ReportResponse todaySales() { return todaySales(todayIST(), todayIST()); }

    @Transactional(readOnly = true)
    public ReportResponse todaySales(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : todayIST();
        LocalDate effectiveTo   = to   != null ? to   : todayIST();

        LocalDateTime start = effectiveFrom.atStartOfDay();
        LocalDateTime end   = effectiveTo.plusDays(1).atStartOfDay();

        List<Transaction> txList = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end)
                .stream()
                .filter(tx -> tx.getInventoryType() != Inventory.InventoryType.ADMIN_MEDICINE_STOCK)
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
                long amount = (long) tx.getQuantity() * price;
                userTotal += amount;

                int specInt = med.getSpecification().intValue();
                String specLabel = med.getType() == Medicine.MedicineType.VIAL
                        ? specInt + " ml"
                        : specInt + " mg";

                String username = tx.getSubmittedBy().getUsername();
                String notes = (tx.getNotes() != null && !tx.getNotes().isBlank())
                        ? tx.getNotes() : "";

                sb.append("  ").append(username).append("  ").append(tx.getQuantity()).append(" x ").append(specLabel);
                if (!notes.isBlank()) {
                    sb.append("  ").append(notes);
                }
                sb.append("\n");
                sb.append("    Amount: Rs ").append(String.format("%,d", amount)).append("\n");
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
     *   INVENTORY section        — REGULAR inventory per spec per user
     *   ADMIN MEDICINE STOCK section  — ADMIN_STOCK inventory per spec per user
     *   DAILY TRANSACTION SUMMARY — approved transactions on that date
     */
    @Transactional(readOnly = true)
    public ReportResponse dailyReport(LocalDate date) {
        LocalDate reportDate = (date != null) ? date : todayIST();
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end   = reportDate.plusDays(1).atStartOfDay();

        List<Inventory> regularRecords  = inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        List<Inventory> adminStockRecords = inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
        List<Transaction> txList = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end);
        List<InventoryAdjustment> adjustments = inventoryAdjustmentRepository.findByDateRange(start, end);
        Map<String, Integer> inTransitMap = buildInTransitMap();

        StringBuilder sb = new StringBuilder();
        sb.append("DAILY REPORT - ").append(reportDate.format(DATE_FMT)).append("\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        // ── REGULAR MEDICINE STOCK section ────────────────────────────
        sb.append("REGULAR MEDICINE STOCK\n");
        sb.append("-".repeat(22)).append("\n");
        appendInventorySection(sb, regularRecords, inTransitMap);

        // ── ADMIN MEDICINE STOCK section ───────────────────────────────
        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("ADMIN MEDICINE STOCK\n");
        sb.append("-".repeat(20)).append("\n");
        appendInventoryAdminSection(sb, adminStockRecords, inTransitMap);

        // ── DAILY TRANSACTION SUMMARY ─────────────────────────────────
        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("DAILY TRANSACTION SUMMARY\n");
        sb.append("-".repeat(25)).append("\n");

        List<Transaction> regularTx = txList.stream()
                .filter(tx -> tx.getInventoryType() != Inventory.InventoryType.ADMIN_MEDICINE_STOCK)
                .toList();
        List<Transaction> adminTx = txList.stream()
                .filter(tx -> tx.getInventoryType() == Inventory.InventoryType.ADMIN_MEDICINE_STOCK)
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

        // ── Inventory adjustments ─────────────────────────────────────
        List<InventoryAdjustment> regular = adjustments.stream()
                .filter(a -> !a.isInternalMovement()).toList();
        List<InventoryAdjustment> internal = adjustments.stream()
                .filter(InventoryAdjustment::isInternalMovement).toList();

        if (!regular.isEmpty()) {
            sb.append("\n");
            for (InventoryAdjustment a : regular) {
                appendAdjustmentLine(sb, a);
            }
        }

        if (!internal.isEmpty()) {
            sb.append("\nInternal Movement\n");
            sb.append("-".repeat(17)).append("\n");
            for (InventoryAdjustment a : internal) {
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

    /**
     * Appends per-pharma, per-spec, per-user inventory lines.
     * Groups by pharma company; only emits specs with non-zero total
     * (specs with no inventory are skipped).
     */
    private void appendInventorySection(StringBuilder sb, List<Inventory> records,
                                        Map<String, Integer> inTransitMap) {
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, List<Inventory>>> pharmaSpecMap = new LinkedHashMap<>();
        for (Inventory inv : records) {
            Long pid = inv.getMedicine().getPharmaCompany().getId();
            pharmaNames.putIfAbsent(pid, inv.getMedicine().getPharmaCompany().getName());
            pharmaSpecMap.computeIfAbsent(pid, k -> new LinkedHashMap<>())
                         .computeIfAbsent(specKey(inv.getMedicine()), k -> new ArrayList<>())
                         .add(inv);
        }

        for (Map.Entry<Long, String> pe : pharmaNames.entrySet()) {
            String pharmaName = pe.getValue();
            sb.append(pharmaName).append("\n");
            sb.append("-".repeat(pharmaName.length())).append("\n");
            Map<String, List<Inventory>> bySpec = pharmaSpecMap.get(pe.getKey());
            for (String[] spec : DAILY_SPEC_ORDER) {
                String key = spec[0] + "|" + spec[1];
                List<Inventory> entries = bySpec.getOrDefault(key, Collections.emptyList());
                if (entries.isEmpty()) continue;
                int total = 0;
                sb.append("\n").append(spec[2]).append("\n");
                for (Inventory inv : entries) {
                    appendUserQtyLine(sb, inv, inTransitMap);
                    total += inv.getQuantity();
                }
                sb.append("  TOTAL: ").append(total).append("\n");
            }
        }
    }

    /**
     * Appends per-pharma, per-spec, per-user admin inventory lines.
     * Groups by pharma company; skips specs with no data (no (none)/TOTAL: 0).
     * Used for the ADMIN MEDICINE STOCK section in the daily report.
     */
    private void appendInventoryAdminSection(StringBuilder sb, List<Inventory> records,
                                             Map<String, Integer> inTransitMap) {
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, List<Inventory>>> pharmaSpecMap = new LinkedHashMap<>();
        for (Inventory inv : records) {
            Long pid = inv.getMedicine().getPharmaCompany().getId();
            pharmaNames.putIfAbsent(pid, inv.getMedicine().getPharmaCompany().getName());
            pharmaSpecMap.computeIfAbsent(pid, k -> new LinkedHashMap<>())
                         .computeIfAbsent(specKey(inv.getMedicine()), k -> new ArrayList<>())
                         .add(inv);
        }

        for (Map.Entry<Long, String> pe : pharmaNames.entrySet()) {
            String pharmaName = pe.getValue();
            sb.append(pharmaName).append("\n");
            sb.append("-".repeat(pharmaName.length())).append("\n");
            Map<String, List<Inventory>> bySpec = pharmaSpecMap.get(pe.getKey());
            for (String[] spec : DAILY_SPEC_ORDER) {
                String key = spec[0] + "|" + spec[1];
                List<Inventory> entries = bySpec.getOrDefault(key, Collections.emptyList());
                if (entries.isEmpty()) continue;
                String header = "VIAL".equals(spec[0])
                        ? spec[2] + " | " + vialConc(entries.get(0).getMedicine()) + " mg/ml"
                        : spec[2];
                sb.append("\n").append(header).append("\n");
                int total = 0;
                for (Inventory inv : entries) {
                    appendUserQtyLine(sb, inv, inTransitMap);
                    total += inv.getQuantity();
                }
                sb.append("  TOTAL: ").append(total).append("\n");
            }
        }
    }

    private void appendTransactionLine(StringBuilder sb, Transaction tx) {
        Medicine m = tx.getMedicine();
        int specInt = m.getSpecification().intValue();
        String specLabel = m.getType() == Medicine.MedicineType.VIAL
                ? specInt + " ml" : specInt + " mg";
        sb.append(tx.getSubmittedBy().getUsername())
          .append("  ")
          .append(tx.getQuantity()).append(" x ").append(specLabel);
        if (tx.getNotes() != null && !tx.getNotes().isBlank()) {
            sb.append("  ").append(tx.getNotes());
        }
        sb.append("\n");
    }

    private void appendAdjustmentLine(StringBuilder sb, InventoryAdjustment a) {
        Medicine m = a.getMedicine();
        int specInt = m.getSpecification().intValue();
        String specLabel = m.getType() == Medicine.MedicineType.VIAL
                ? specInt + " ml" : specInt + " mg";
        String sign = "ADD".equals(a.getAdjustmentType()) ? "+" : "-";
        sb.append(a.getUser().getUsername())
          .append("  ")
          .append(sign).append(a.getQuantity()).append(" x ").append(specLabel);
        if (a.getNote() != null && !a.getNote().isBlank()) {
            sb.append("  ").append(a.getNote());
        }
        sb.append("\n");
    }
}
