package com.pharma.inventory.service;

import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.entity.Inventory;
import com.pharma.inventory.entity.InventoryAdjustment;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import com.pharma.inventory.repository.InventoryRepository;
import com.pharma.inventory.repository.TransactionRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharma.inventory.dto.SalesGraphResponse;
import java.time.DayOfWeek;
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

    /**
     * Builds a map of userId|medicineId|inventoryType → total in-transit ADD quantity.
     * Only includes adjustments whose adjustedAt ≤ asOf AND whose transit window hasn't expired by asOf.
     * Passing LocalDateTime.now() gives "currently in transit"; passing reportDate+1.atStartOfDay() gives
     * "in transit as of the end of reportDate" for historical daily reports.
     */
    private Map<String, Integer> buildInTransitMap(LocalDateTime asOf) {
        List<InventoryAdjustment> active = inventoryAdjustmentRepository.findAllActiveInTransit();
        Map<String, Integer> map = new java.util.HashMap<>();
        for (InventoryAdjustment a : active) {
            if ("ADD".equals(a.getAdjustmentType())
                    && a.getAdjustedAt().isBefore(asOf)
                    && a.getAdjustedAt().plusDays(a.getTransitDays()).isAfter(asOf)) {
                String key = a.getUser().getId() + "|" + a.getMedicine().getId() + "|" + a.getInventoryType().name();
                map.merge(key, a.getQuantity(), Integer::sum);
            }
        }
        return map;
    }

    @Timed(value = "pharmatrack.report.inventory_by_user", description = "Time to build inventory-by-user report")
    @Transactional(readOnly = true)
    public ReportResponse inventoryByUser() {
        List<Inventory> regularRecords   = inventoryRepository.findAllNonZeroRegularOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        List<Inventory> adminStockRecords = inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
        Map<String, Integer> inTransitMap = buildInTransitMap(LocalDateTime.now(IST_ZONE));

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
    private void appendUserQtyLine(StringBuilder sb, String username, Long userId, Long medicineId,
                                    Inventory.InventoryType type, int qty,
                                    Map<String, Integer> inTransitMap) {
        String key = userId + "|" + medicineId + "|"
                + (type != null ? type.name() : "REGULAR_MEDICINE_STOCK");
        int transit = inTransitMap.getOrDefault(key, 0);
        sb.append("  ").append(username).append(": ");
        if (transit > 0) {
            sb.append(qty - transit).append(" + ").append(transit).append(" (in transit)");
        } else {
            sb.append(qty);
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
                appendUserQtyLine(sb, inv.getUser().getUsername(), inv.getUser().getId(),
                        inv.getMedicine().getId(), inv.getInventoryType(), inv.getQuantity(), inTransitMap);
                total += inv.getQuantity();
            }
            sb.append("  TOTAL: ").append(total).append("\n\n");
        }
    }

    @Transactional(readOnly = true)
    public ReportResponse inventoryValuation(LocalDate date) {
        return date == null ? inventoryValuationCurrent() : inventoryValuationHistorical(date);
    }

    public ReportResponse inventoryValuation() {
        return inventoryValuation(null);
    }

    @Transactional(readOnly = true)
    private ReportResponse inventoryValuationCurrent() {
        List<Inventory> records = inventoryRepository.findAllNonZeroRegularForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        Map<String, Integer> inTransitMap = buildInTransitMap(LocalDateTime.now(IST_ZONE));

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
        sb.append("MEDICINE STOCK VALUATION\n");
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
                    appendUserQtyLine(sb, inv.getUser().getUsername(), inv.getUser().getId(),
                            inv.getMedicine().getId(), inv.getInventoryType(), inv.getQuantity(), inTransitMap);
                }
                sb.append("  Valuation: ").append(totalQty).append(" units x Rs ")
                  .append(String.format("%,d", price)).append(" = Rs ")
                  .append(String.format("%,d", valuation)).append("\n\n");
            }
        }

        sb.append("-".repeat(40)).append("\n");
        sb.append("TOTAL VALUATION: Rs ").append(String.format("%,d", grandTotal)).append("\n");

        return new ReportResponse("INVENTORY_VALUATION", nowIST(), sb.toString());
    }

    /**
     * Forward reconstruction: computes stock as of the target date by accumulating all
     * ADD adjustments, subtracting REMOVE adjustments, and subtracting approved transactions,
     * all up to and including the target date. Stock naturally varies per date.
     */
    @Transactional(readOnly = true)
    private ReportResponse inventoryValuationHistorical(LocalDate date) {
        LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();

        List<InventoryAdjustment> adjUpTo = inventoryAdjustmentRepository.findAllUpTo(endExclusive);
        List<Transaction> txUpTo = transactionRepository.findApprovedUpTo(
                Transaction.TransactionStatus.APPROVED, endExclusive);

        record MedUserKey(Long userId, Long medicineId) {}
        record MedUserMeta(User user, Medicine medicine) {}

        Map<MedUserKey, Integer> qtyMap = new LinkedHashMap<>();
        Map<MedUserKey, MedUserMeta> metaMap = new LinkedHashMap<>();

        // Apply REGULAR adjustments up to target date: ADD increases stock, REMOVE decreases it
        for (InventoryAdjustment adj : adjUpTo) {
            Inventory.InventoryType t = adj.getInventoryType();
            if (t != null && t != Inventory.InventoryType.REGULAR_MEDICINE_STOCK) continue;
            MedUserKey k = new MedUserKey(adj.getUser().getId(), adj.getMedicine().getId());
            int delta = "ADD".equals(adj.getAdjustmentType()) ? adj.getQuantity() : -adj.getQuantity();
            qtyMap.merge(k, delta, Integer::sum);
            metaMap.putIfAbsent(k, new MedUserMeta(adj.getUser(), adj.getMedicine()));
        }

        // Subtract REGULAR approved transactions (dispatches) up to target date
        for (Transaction tx : txUpTo) {
            Inventory.InventoryType t = tx.getInventoryType() != null
                    ? tx.getInventoryType() : Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
            if (t != Inventory.InventoryType.REGULAR_MEDICINE_STOCK) continue;
            MedUserKey k = new MedUserKey(tx.getSubmittedBy().getId(), tx.getMedicine().getId());
            qtyMap.merge(k, -tx.getQuantity(), Integer::sum);
            metaMap.putIfAbsent(k, new MedUserMeta(tx.getSubmittedBy(), tx.getMedicine()));
        }

        // In-transit map: ADD adjustments still within their transit window on the target date
        Map<String, Integer> inTransitMap = new java.util.HashMap<>();
        for (InventoryAdjustment adj : adjUpTo) {
            if ("ADD".equals(adj.getAdjustmentType()) && (adj.isWasInTransit() || adj.isInTransit())
                    && adj.getAdjustedAt().plusDays(adj.getTransitDays()).isAfter(endExclusive)) {
                Inventory.InventoryType t = adj.getInventoryType();
                String typeStr = t != null ? t.name() : "REGULAR_MEDICINE_STOCK";
                String key = adj.getUser().getId() + "|" + adj.getMedicine().getId() + "|" + typeStr;
                inTransitMap.merge(key, adj.getQuantity(), Integer::sum);
            }
        }

        // Group by pharma → specKey
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, List<long[]>>> pharmaSpecUserQty = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, Medicine>> pharmaSpecMedicine = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, Map<Long, String>>> pharmaSpecUsernames = new LinkedHashMap<>();

        for (Map.Entry<MedUserKey, Integer> entry : qtyMap.entrySet()) {
            MedUserKey k = entry.getKey();
            int qty = entry.getValue();
            if (qty <= 0) continue;

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
                             .add(new long[]{ k.userId(), qty });
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

            Map<String, List<long[]>>      specUserQty  = pharmaSpecUserQty.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Medicine>          specMed      = pharmaSpecMedicine.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Map<Long, String>> specUsernames = pharmaSpecUsernames.getOrDefault(pharmaId, Collections.emptyMap());

            for (String[] spec : DAILY_SPEC_ORDER) {
                String sk = spec[0] + "|" + spec[1];
                List<long[]> userQtys = specUserQty.getOrDefault(sk, Collections.emptyList());
                if (userQtys.isEmpty()) continue;
                Medicine med = specMed.get(sk);
                Map<Long, String> usernames = specUsernames.getOrDefault(sk, Collections.emptyMap());

                sb.append(spec[2]).append("\n");
                int totalQty = 0;
                for (long[] uq : userQtys) {
                    long uid = uq[0];
                    int qty = (int) uq[1];
                    totalQty += qty;
                    String uname = usernames.getOrDefault(uid, "unknown");
                    String itKey = uid + "|" + med.getId() + "|REGULAR_MEDICINE_STOCK";
                    int transit = inTransitMap.getOrDefault(itKey, 0);
                    sb.append("  ").append(uname).append(": ");
                    if (transit > 0) {
                        sb.append(qty - transit).append(" + ").append(transit).append(" (in transit)");
                    } else {
                        sb.append(qty);
                    }
                    sb.append("\n");
                }
                int price = med.getPrice();
                long valuation = (long) totalQty * price;
                grandTotal += valuation;
                sb.append("  Valuation: ").append(totalQty).append(" units x Rs ")
                  .append(String.format("%,d", price)).append(" = Rs ")
                  .append(String.format("%,d", valuation)).append("\n\n");
            }
        }

        sb.append("-".repeat(40)).append("\n");
        sb.append("TOTAL VALUATION: Rs ").append(String.format("%,d", grandTotal)).append("\n");

        return new ReportResponse("INVENTORY_VALUATION", nowIST(), sb.toString());
    }

    /** Convenience overload — defaults to today only. */
    public ReportResponse todaySales() { return todaySales(todayIST(), todayIST()); }

    @Timed(value = "pharmatrack.report.today_sales", description = "Time to build sales report")
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
     *   INVENTORY section        — REGULAR inventory per spec per user (forward reconstruction)
     *   ADMIN MEDICINE STOCK section  — ADMIN_STOCK inventory per spec per user (forward reconstruction)
     *   DAILY TRANSACTION SUMMARY — approved transactions on that date
     */
    @Transactional(readOnly = true)
    public ReportResponse dailyReport(LocalDate date) {
        LocalDate reportDate = (date != null) ? date : todayIST();
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end   = reportDate.plusDays(1).atStartOfDay();

        // Forward reconstruction: all adjustments and approved transactions up to end of report date
        List<InventoryAdjustment> adjUpTo = inventoryAdjustmentRepository.findAllUpTo(end);
        List<Transaction> txApprovedUpTo = transactionRepository.findApprovedUpTo(
                Transaction.TransactionStatus.APPROVED, end);

        // Daily summary sections (transactions and adjustments ON the report date)
        List<Transaction> txList = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end);
        List<InventoryAdjustment> adjustments = inventoryAdjustmentRepository.findByDateRange(start, end);

        // Build stock maps keyed by "userId|medicineId"
        Map<String, Integer> regularQty = new LinkedHashMap<>();
        Map<String, Integer> adminQty   = new LinkedHashMap<>();
        Map<String, User>     userByKey = new LinkedHashMap<>();
        Map<String, Medicine> medByKey  = new LinkedHashMap<>();

        for (InventoryAdjustment adj : adjUpTo) {
            Inventory.InventoryType t = adj.getInventoryType() != null
                    ? adj.getInventoryType() : Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
            String key = adj.getUser().getId() + "|" + adj.getMedicine().getId();
            int delta = "ADD".equals(adj.getAdjustmentType()) ? adj.getQuantity() : -adj.getQuantity();
            if (t == Inventory.InventoryType.REGULAR_MEDICINE_STOCK) {
                regularQty.merge(key, delta, Integer::sum);
            } else if (t == Inventory.InventoryType.ADMIN_MEDICINE_STOCK) {
                adminQty.merge(key, delta, Integer::sum);
            }
            userByKey.putIfAbsent(key, adj.getUser());
            medByKey.putIfAbsent(key, adj.getMedicine());
        }

        for (Transaction tx : txApprovedUpTo) {
            Inventory.InventoryType t = tx.getInventoryType() != null
                    ? tx.getInventoryType() : Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
            String key = tx.getSubmittedBy().getId() + "|" + tx.getMedicine().getId();
            if (t == Inventory.InventoryType.REGULAR_MEDICINE_STOCK) {
                regularQty.merge(key, -tx.getQuantity(), Integer::sum);
            } else if (t == Inventory.InventoryType.ADMIN_MEDICINE_STOCK) {
                adminQty.merge(key, -tx.getQuantity(), Integer::sum);
            }
            userByKey.putIfAbsent(key, tx.getSubmittedBy());
            medByKey.putIfAbsent(key, tx.getMedicine());
        }

        List<Inventory> regularRecords   = buildInventoryList(regularQty, userByKey, medByKey,
                Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        List<Inventory> adminStockRecords = buildInventoryList(adminQty, userByKey, medByKey,
                Inventory.InventoryType.ADMIN_MEDICINE_STOCK);

        // In-transit: ADD adjustments still within their transit window on the report date
        Map<String, Integer> inTransitMap = new java.util.HashMap<>();
        for (InventoryAdjustment adj : adjUpTo) {
            if ("ADD".equals(adj.getAdjustmentType()) && (adj.isWasInTransit() || adj.isInTransit())
                    && adj.getAdjustedAt().plusDays(adj.getTransitDays()).isAfter(end)) {
                Inventory.InventoryType t = adj.getInventoryType();
                String typeStr = t != null ? t.name() : "REGULAR_MEDICINE_STOCK";
                String key = adj.getUser().getId() + "|" + adj.getMedicine().getId() + "|" + typeStr;
                inTransitMap.merge(key, adj.getQuantity(), Integer::sum);
            }
        }

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

    private List<Inventory> buildInventoryList(Map<String, Integer> qtyMap,
                                               Map<String, User> userByKey,
                                               Map<String, Medicine> medByKey,
                                               Inventory.InventoryType type) {
        List<Inventory> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : qtyMap.entrySet()) {
            if (e.getValue() <= 0) continue;
            User user = userByKey.get(e.getKey());
            Medicine med = medByKey.get(e.getKey());
            if (user == null || med == null) continue;
            Inventory inv = new Inventory();
            inv.setUser(user);
            inv.setMedicine(med);
            inv.setQuantity(e.getValue());
            inv.setInventoryType(type);
            result.add(inv);
        }
        return result;
    }

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
                    appendUserQtyLine(sb, inv.getUser().getUsername(), inv.getUser().getId(),
                            inv.getMedicine().getId(), inv.getInventoryType(), inv.getQuantity(), inTransitMap);
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
                    appendUserQtyLine(sb, inv.getUser().getUsername(), inv.getUser().getId(),
                            inv.getMedicine().getId(), inv.getInventoryType(), inv.getQuantity(), inTransitMap);
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

    @Timed(value = "pharmatrack.report.sales_graph", description = "Time to build the sales graph response")
    @Transactional(readOnly = true)
    public SalesGraphResponse salesGraph(String period, LocalDate from, LocalDate to) {
        if (from == null) from = todayIST().minusDays(29);
        if (to == null) to = todayIST();
        if (period == null || period.isBlank()) period = "daily";

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Transaction> txns = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end);

        // Spec order: DAILY_SPEC_ORDER first (consistent colours), unknown specs appended
        LinkedHashSet<String> specOrder = new LinkedHashSet<>();
        for (String[] spec : DAILY_SPEC_ORDER) specOrder.add(spec[2]);

        // Map<periodKey, Map<specName, long[]{qty, val}>>
        Map<String, Map<String, long[]>> grouped = new LinkedHashMap<>();
        for (Transaction t : txns) {
            String key = groupKey(t.getSubmittedAt().toLocalDate(), period);
            String specName = specDisplayName(t.getMedicine());
            specOrder.add(specName);
            grouped.computeIfAbsent(key, k -> new LinkedHashMap<>())
                   .computeIfAbsent(specName, s -> new long[]{0, 0});
            int price = t.getPricePerUnit() != null ? t.getPricePerUnit() : t.getMedicine().getPrice();
            long[] agg = grouped.get(key).get(specName);
            agg[0] += t.getQuantity();
            agg[1] += (long) t.getQuantity() * price;
        }

        List<String> orderedSpecs = new ArrayList<>(specOrder);

        List<SalesGraphResponse.DataPoint> dataPoints = new ArrayList<>();
        for (String key : allPeriodKeys(period, from, to)) {
            Map<String, long[]> specMap = grouped.getOrDefault(key, Collections.emptyMap());
            long totalQty = 0, totalVal = 0;
            List<SalesGraphResponse.SpecBreakdown> specs = new ArrayList<>();
            for (String specName : orderedSpecs) {
                long[] agg = specMap.getOrDefault(specName, new long[]{0, 0});
                specs.add(new SalesGraphResponse.SpecBreakdown(specName, (int) agg[0], agg[1]));
                totalQty += agg[0];
                totalVal += agg[1];
            }
            dataPoints.add(new SalesGraphResponse.DataPoint(
                    keyToLabel(key, period), (int) totalQty, totalVal, specs));
        }

        return new SalesGraphResponse(period, dataPoints);
    }

    private String specDisplayName(Medicine m) {
        String key = specKey(m);
        for (String[] spec : DAILY_SPEC_ORDER) {
            if ((spec[0] + "|" + spec[1]).equals(key)) return spec[2];
        }
        int s = m.getSpecification().intValue();
        return switch (m.getType()) {
            case VIAL    -> "Vial " + s + " ml";
            case TABLET  -> "Tablet " + s + " mg";
            case CAPSULE -> "Capsule " + s + " mg";
            case SYRUP   -> "Syrup " + s + " ml";
        };
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
