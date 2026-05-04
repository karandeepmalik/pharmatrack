package com.pharma.inventory.service;

import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.entity.Inventory;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.User;
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

    @Transactional(readOnly = true)
    public ReportResponse inventoryByUser() {
        List<Inventory> records = inventoryRepository.findAllNonZeroOrderByMedicineAndUser();

        // Build maps: specKey → list of inventory records, specKey → full medicine name header
        Map<String, List<Inventory>> bySpec = new LinkedHashMap<>();
        Map<String, String> specKeyToHeader = new LinkedHashMap<>();
        for (Inventory inv : records) {
            Medicine m = inv.getMedicine();
            String key = specKey(m);
            String header = m.getType() == Medicine.MedicineType.VIAL
                    ? m.getName() + " | " + vialConc(m) + " mg/ml"
                    : m.getName();
            bySpec.computeIfAbsent(key, k -> new ArrayList<>()).add(inv);
            specKeyToHeader.putIfAbsent(key, header);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT INVENTORY LEVEL BY USER\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        // Iterate in fixed spec order, skip specs with no data
        for (String[] spec : DAILY_SPEC_ORDER) {
            String key = spec[0] + "|" + spec[1];
            List<Inventory> entries = bySpec.get(key);
            if (entries == null || entries.isEmpty()) continue;

            String header = specKeyToHeader.get(key);
            sb.append(header).append("\n");
            sb.append("-".repeat(35)).append("\n");
            int total = 0;
            for (Inventory inv : entries) {
                sb.append("  ").append(inv.getUser().getUsername())
                  .append(": ").append(inv.getQuantity()).append("\n");
                total += inv.getQuantity();
            }
            sb.append("  TOTAL: ").append(total).append("\n\n");
        }

        return new ReportResponse("INVENTORY_BY_USER", nowIST(), sb.toString());
    }

    @Transactional(readOnly = true)
    public ReportResponse inventoryValuation() {
        List<Inventory> records = inventoryRepository.findAllNonZeroForValuation();

        // Group by pharmaId → then by specKey within each pharma
        // Also track pharma name and spec totals
        LinkedHashMap<Long, String> pharmaNames = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, Integer>> pharmaSpecQty = new LinkedHashMap<>();
        LinkedHashMap<Long, Map<String, Integer>> pharmaSpecPrice = new LinkedHashMap<>();

        for (Inventory inv : records) {
            Medicine med = inv.getMedicine();
            Long pharmaId = med.getPharmaCompany().getId();
            String pharmaName = med.getPharmaCompany().getName();
            String key = specKey(med);

            pharmaNames.putIfAbsent(pharmaId, pharmaName);
            pharmaSpecQty.computeIfAbsent(pharmaId, k -> new LinkedHashMap<>())
                         .merge(key, inv.getQuantity(), Integer::sum);
            pharmaSpecPrice.computeIfAbsent(pharmaId, k -> new LinkedHashMap<>())
                           .putIfAbsent(key, med.getPrice());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT INVENTORY VALUATION\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        long grandTotal = 0;

        for (Map.Entry<Long, String> pharmaEntry : pharmaNames.entrySet()) {
            Long pharmaId = pharmaEntry.getKey();
            String pharmaName = pharmaEntry.getValue();

            sb.append(pharmaName).append("\n");
            sb.append("-".repeat(pharmaName.length())).append("\n");

            Map<String, Integer> specQty = pharmaSpecQty.getOrDefault(pharmaId, Collections.emptyMap());
            Map<String, Integer> specPrice = pharmaSpecPrice.getOrDefault(pharmaId, Collections.emptyMap());

            for (String[] spec : DAILY_SPEC_ORDER) {
                String key = spec[0] + "|" + spec[1];
                Integer qty = specQty.get(key);
                if (qty == null || qty == 0) continue;

                int price = specPrice.getOrDefault(key, 0);
                long valuation = (long) qty * price;
                grandTotal += valuation;

                String shortName = spec[2]; // e.g. "Vial 10 ml"
                sb.append(shortName).append("\n");
                sb.append("  Qty: ").append(qty)
                  .append("  |  Price: Rs ").append(String.format("%,d", price))
                  .append("  |  Value: Rs ").append(String.format("%,d", valuation)).append("\n\n");
            }
        }

        sb.append("-".repeat(40)).append("\n");
        sb.append("TOTAL VALUATION: Rs ").append(String.format("%,d", grandTotal)).append("\n");

        return new ReportResponse("INVENTORY_VALUATION", nowIST(), sb.toString());
    }

    @Transactional(readOnly = true)
    public ReportResponse todaySales() {
        LocalDate today = todayIST();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        List<Transaction> txList = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end);

        StringBuilder sb = new StringBuilder();
        sb.append("TODAY'S SALES - ").append(today.format(DATE_FMT)).append("\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        if (txList.isEmpty()) {
            sb.append("No sales recorded today.\n");
            return new ReportResponse("TODAY_SALES", nowIST(), sb.toString());
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
                long amount = (long) tx.getQuantity() * med.getPrice();
                userTotal += amount;

                // Build short spec label: "10 ml" or "50 mg"
                int specInt = med.getSpecification().intValue();
                String specLabel = med.getType() == Medicine.MedicineType.VIAL
                        ? specInt + " ml"
                        : specInt + " mg";

                String username = tx.getSubmittedBy().getUsername();
                String notes = (tx.getNotes() != null && !tx.getNotes().isBlank())
                        ? tx.getNotes() : "";

                // Format: "  username  specLabel  notes"
                sb.append("  ").append(username).append("  ").append(specLabel);
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

        return new ReportResponse("TODAY_SALES", nowIST(), sb.toString());
    }

    @Transactional(readOnly = true)
    public ReportResponse dailyReport() {
        LocalDate today = todayIST();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        List<Inventory> inventoryRecords = inventoryRepository.findAllNonZeroOrderByMedicineAndUser();
        List<Transaction> txList = transactionRepository.findApprovedBetween(
                Transaction.TransactionStatus.APPROVED, start, end);
        List<Inventory> adminMovements = inventoryRepository.findAdminModificationsToday(start, end);

        // Group inventory by type|specification key
        Map<String, List<Inventory>> bySpec = new HashMap<>();
        Map<String, Medicine> specToMedicine = new HashMap<>();
        for (Inventory inv : inventoryRecords) {
            Medicine m = inv.getMedicine();
            String key = specKey(m);
            bySpec.computeIfAbsent(key, k -> new ArrayList<>()).add(inv);
            specToMedicine.putIfAbsent(key, m);
        }

        // Use pharma company name as section heading
        String pharmaName = specToMedicine.values().stream()
                .map(m -> m.getPharmaCompany().getName())
                .findFirst()
                .orElse("Shield FX");

        StringBuilder sb = new StringBuilder();
        sb.append("DAILY REPORT - ").append(today.format(DATE_FMT)).append("\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");
        sb.append(pharmaName).append("\n");
        sb.append("-".repeat(pharmaName.length())).append("\n");

        for (String[] spec : DAILY_SPEC_ORDER) {
            String specVal  = spec[1];
            String baseName = spec[2];
            String key = spec[0] + "|" + specVal;

            sb.append("\n").append(baseName).append("\n");

            List<Inventory> entries = bySpec.getOrDefault(key, Collections.emptyList());
            if (entries.isEmpty()) {
                sb.append("  (none)\n");
                sb.append("  TOTAL: 0\n");
            } else {
                int total = 0;
                for (Inventory inv : entries) {
                    sb.append("  ").append(inv.getUser().getUsername())
                      .append(": ").append(inv.getQuantity()).append("\n");
                    total += inv.getQuantity();
                }
                sb.append("  TOTAL: ").append(total).append("\n");
            }
        }

        // Admin inventory — quantities per spec for admin users
        Map<String, Integer> adminBySpec = new HashMap<>();
        for (Inventory inv : inventoryRecords) {
            if (inv.getUser().getRole() == User.Role.ADMIN) {
                String key = specKey(inv.getMedicine());
                adminBySpec.merge(key, inv.getQuantity(), Integer::sum);
            }
        }

        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("ADMIN INVENTORY\n");
        sb.append("-".repeat(15)).append("\n");
        for (String[] spec : DAILY_SPEC_ORDER) {
            String key = spec[0] + "|" + spec[1];
            int qty = adminBySpec.getOrDefault(key, 0);
            sb.append(spec[2]).append(": ").append(qty).append("\n");
        }

        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("TODAY'S TRANSACTIONS\n");
        sb.append("-".repeat(20)).append("\n");

        if (txList.isEmpty()) {
            sb.append("(no transactions today)\n");
        } else {
            for (Transaction tx : txList) {
                Medicine m = tx.getMedicine();
                int specInt = m.getSpecification().intValue();
                String specLabel = m.getType() == Medicine.MedicineType.VIAL
                        ? specInt + " ml"
                        : specInt + " mg";
                // Format: "username  N x specLabel  notes"
                sb.append(tx.getSubmittedBy().getUsername())
                  .append("  ")
                  .append(tx.getQuantity()).append(" x ").append(specLabel);
                if (tx.getNotes() != null && !tx.getNotes().isBlank()) {
                    sb.append("  ").append(tx.getNotes());
                }
                sb.append("\n");
            }
        }

        sb.append("\n").append("=".repeat(40)).append("\n");
        sb.append("INTERNAL MOVEMENT\n");
        sb.append("---------------\n");

        if (adminMovements.isEmpty()) {
            sb.append("(no internal movements today)\n");
        } else {
            for (Inventory inv : adminMovements) {
                sb.append(inv.getUser().getUsername())
                  .append("  ").append(inv.getLastNote()).append("\n");
            }
        }

        return new ReportResponse("DAILY_REPORT", nowIST(), sb.toString());
    }
}
