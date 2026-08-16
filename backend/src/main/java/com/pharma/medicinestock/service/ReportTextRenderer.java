package com.pharma.medicinestock.service;

import com.pharma.medicinestock.entity.Medicine;
import com.pharma.medicinestock.entity.MedicineStock;
import com.pharma.medicinestock.entity.MedicineStockAdjustment;
import com.pharma.medicinestock.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders report data into the plain-text lines/sections {@link ReportService} assembles into
 * a full report body. Pure functions of their arguments — no repository access, no report
 * assembly logic, just "given this data, what does the text look like."
 */
final class ReportTextRenderer {

    private ReportTextRenderer() {}

    static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Fixed display order for daily report — short names, no pharma prefix
    static final List<String[]> DAILY_SPEC_ORDER = List.of(
        new String[]{"VIAL",   "10.0", "Vial 10 ml"},
        new String[]{"VIAL",   "5.0",  "Vial 5 ml"},
        new String[]{"TABLET", "50.0", "Tablet 50 mg (10 Tablets)"},
        new String[]{"TABLET", "25.0", "Tablet 25 mg (10 Tablets)"},
        new String[]{"TABLET", "12.0", "Tablet 12 mg (10 Tablets)"}
    );

    static String nowIST() {
        return ZonedDateTime.now(IST_ZONE).format(DT_FMT) + " IST";
    }

    static LocalDate todayIST() {
        return ZonedDateTime.now(IST_ZONE).toLocalDate();
    }

    static String vialConc(Medicine m) {
        Double c = m.getConcentrationMgPerMl();
        if (c == null) return "20";
        return c % 1 == 0 ? String.valueOf(c.intValue()) : String.valueOf(c);
    }

    /** Returns the type|spec key used to match DAILY_SPEC_ORDER entries. */
    static String specKey(Medicine m) {
        return m.getType().name() + "|" + m.getSpecification();
    }

    static String specDisplayName(Medicine m) {
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

    /**
     * Writes one user quantity line, using in-transit format when applicable.
     * Format: "  username: settled + transit (in transit)" or "  username: qty"
     */
    static void appendUserQtyLine(StringBuilder sb, String username, Long userId, Long medicineId,
                                   MedicineStock.MedicineStockType type, BigDecimal qty,
                                   Map<String, BigDecimal> inTransitMap) {
        String key = userId + "|" + medicineId + "|"
                + (type != null ? type.name() : "REGULAR_MEDICINE_STOCK");
        BigDecimal transit = inTransitMap.getOrDefault(key, BigDecimal.ZERO);
        sb.append("  ").append(username).append(": ");
        if (transit.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(qty.subtract(transit).toPlainString()).append(" + ")
              .append(transit.toPlainString()).append(" (in transit)");
        } else {
            sb.append(qty.toPlainString());
        }
        sb.append("\n");
    }

    /**
     * Appends per-spec, per-user medicine stock lines for the medicine-stock-by-user report.
     * Uses full medicine name as header; skips specs with no data.
     */
    static void appendMedicineStockByUserSection(StringBuilder sb, List<MedicineStock> records,
                                                  Map<String, BigDecimal> inTransitMap) {
        Map<String, List<MedicineStock>> bySpec = new LinkedHashMap<>();
        for (MedicineStock inv : records) {
            bySpec.computeIfAbsent(specKey(inv.getMedicine()), k -> new ArrayList<>()).add(inv);
        }

        for (String[] spec : DAILY_SPEC_ORDER) {
            String key = spec[0] + "|" + spec[1];
            List<MedicineStock> entries = bySpec.get(key);
            if (entries == null || entries.isEmpty()) continue;

            // Short format: "Vial 10 ml | 20 mg/ml" or "Tablet 50 mg (10 Tablets)"
            String header = "VIAL".equals(spec[0])
                    ? spec[2] + " | " + vialConc(entries.get(0).getMedicine()) + " mg/ml"
                    : spec[2];

            sb.append(header).append("\n");
            sb.append("-".repeat(35)).append("\n");
            BigDecimal total = BigDecimal.ZERO;
            for (MedicineStock inv : entries) {
                appendUserQtyLine(sb, inv.getUser().getUsername(), inv.getUser().getId(),
                        inv.getMedicine().getId(), inv.getMedicineStockType(), inv.getQuantity(), inTransitMap);
                total = total.add(inv.getQuantity());
            }
            sb.append("  TOTAL: ").append(total.toPlainString()).append("\n\n");
        }
    }

    /**
     * Appends per-pharma, per-spec, per-user medicine stock lines.
     * Groups by pharma company; only emits specs with non-zero total
     * (specs with no medicine stock are skipped).
     */
    static void appendMedicineStockSection(StringBuilder sb, List<MedicineStock> records,
                                            Map<String, BigDecimal> inTransitMap) {
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, List<MedicineStock>>> pharmaSpecMap = new LinkedHashMap<>();
        for (MedicineStock inv : records) {
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
            Map<String, List<MedicineStock>> bySpec = pharmaSpecMap.get(pe.getKey());
            for (String[] spec : DAILY_SPEC_ORDER) {
                String key = spec[0] + "|" + spec[1];
                List<MedicineStock> entries = bySpec.getOrDefault(key, Collections.emptyList());
                if (entries.isEmpty()) continue;
                BigDecimal total = BigDecimal.ZERO;
                sb.append("\n").append(spec[2]).append("\n");
                for (MedicineStock inv : entries) {
                    appendUserQtyLine(sb, inv.getUser().getUsername(), inv.getUser().getId(),
                            inv.getMedicine().getId(), inv.getMedicineStockType(), inv.getQuantity(), inTransitMap);
                    total = total.add(inv.getQuantity());
                }
                sb.append("  TOTAL: ").append(total.toPlainString()).append("\n");
            }
        }
    }

    /**
     * Appends per-pharma, per-spec, per-user admin medicine stock lines.
     * Groups by pharma company; skips specs with no data (no (none)/TOTAL: 0).
     * Used for the ADMIN MEDICINE STOCK section in the daily report.
     */
    static void appendMedicineStockAdminSection(StringBuilder sb, List<MedicineStock> records,
                                                 Map<String, BigDecimal> inTransitMap) {
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, List<MedicineStock>>> pharmaSpecMap = new LinkedHashMap<>();
        for (MedicineStock inv : records) {
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
            Map<String, List<MedicineStock>> bySpec = pharmaSpecMap.get(pe.getKey());
            for (String[] spec : DAILY_SPEC_ORDER) {
                String key = spec[0] + "|" + spec[1];
                List<MedicineStock> entries = bySpec.getOrDefault(key, Collections.emptyList());
                if (entries.isEmpty()) continue;
                String header = "VIAL".equals(spec[0])
                        ? spec[2] + " | " + vialConc(entries.get(0).getMedicine()) + " mg/ml"
                        : spec[2];
                sb.append("\n").append(header).append("\n");
                BigDecimal total = BigDecimal.ZERO;
                for (MedicineStock inv : entries) {
                    appendUserQtyLine(sb, inv.getUser().getUsername(), inv.getUser().getId(),
                            inv.getMedicine().getId(), inv.getMedicineStockType(), inv.getQuantity(), inTransitMap);
                    total = total.add(inv.getQuantity());
                }
                sb.append("  TOTAL: ").append(total.toPlainString()).append("\n");
            }
        }
    }

    static void appendTransactionLine(StringBuilder sb, Transaction tx) {
        Medicine m = tx.getMedicine();
        int specInt = m.getSpecification().intValue();
        String specLabel = m.getType() == Medicine.MedicineType.VIAL
                ? specInt + " ml" : specInt + " mg";
        sb.append(tx.getSubmittedBy().getUsername())
          .append("  ")
          .append(tx.getQuantity().toPlainString()).append(" x ").append(specLabel);
        if (tx.getNotes() != null && !tx.getNotes().isBlank()) {
            sb.append("  ").append(tx.getNotes());
        }
        sb.append("\n");
    }

    static void appendAdjustmentLine(StringBuilder sb, MedicineStockAdjustment a) {
        Medicine m = a.getMedicine();
        int specInt = m.getSpecification().intValue();
        String specLabel = m.getType() == Medicine.MedicineType.VIAL
                ? specInt + " ml" : specInt + " mg";
        String sign = "ADD".equals(a.getAdjustmentType()) ? "+" : "-";
        sb.append(a.getUser().getUsername())
          .append("  ")
          .append(sign).append(a.getQuantity().toPlainString()).append(" x ").append(specLabel);
        if (a.getNote() != null && !a.getNote().isBlank()) {
            sb.append("  ").append(a.getNote());
        }
        sb.append("\n");
    }
}
