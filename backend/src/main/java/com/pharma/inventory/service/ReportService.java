package com.pharma.inventory.service;

import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.entity.Inventory;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.Transaction;
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

    // Fixed display order for daily report: type|specification -> display entry
    private static final List<String[]> DAILY_SPEC_ORDER = List.of(
        new String[]{"VIAL",   "10.0", "Shield FX Vial 10 ml"},
        new String[]{"VIAL",   "5.0",  "Shield FX Vial 5 ml"},
        new String[]{"TABLET", "50.0", "Shield FX Tablet 50 mg (10 Tablets)"},
        new String[]{"TABLET", "25.0", "Shield FX Tablet 25 mg (10 Tablets)"},
        new String[]{"TABLET", "12.0", "Shield FX Tablet 12 mg (10 Tablets)"}
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

    @Transactional(readOnly = true)
    public ReportResponse inventoryByUser() {
        List<Inventory> records = inventoryRepository.findAllNonZeroOrderByMedicineAndUser();

        // Group by medicine — tablets use name only, vials append concentration
        LinkedHashMap<String, List<Inventory>> bySpec = new LinkedHashMap<>();
        for (Inventory inv : records) {
            Medicine m = inv.getMedicine();
            String key = m.getType() == Medicine.MedicineType.VIAL
                    ? m.getName() + " | " + vialConc(m) + " mg/ml"
                    : m.getName();
            bySpec.computeIfAbsent(key, k -> new ArrayList<>()).add(inv);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT INVENTORY LEVEL BY USER\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        for (Map.Entry<String, List<Inventory>> entry : bySpec.entrySet()) {
            sb.append(entry.getKey()).append("\n");
            sb.append("-".repeat(35)).append("\n");
            int total = 0;
            for (Inventory inv : entry.getValue()) {
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

        // Group by medicine
        LinkedHashMap<String, List<Inventory>> byMed = new LinkedHashMap<>();
        for (Inventory inv : records) {
            String key = inv.getMedicine().getId() + "|" + inv.getMedicine().getName();
            byMed.computeIfAbsent(key, k -> new ArrayList<>()).add(inv);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT INVENTORY VALUATION\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        long grandTotal = 0;

        for (Map.Entry<String, List<Inventory>> entry : byMed.entrySet()) {
            Inventory first = entry.getValue().get(0);
            Medicine med = first.getMedicine();
            int totalQty = entry.getValue().stream().mapToInt(Inventory::getQuantity).sum();
            long valuation = (long) totalQty * med.getPrice();
            grandTotal += valuation;

            String header = med.getType() == Medicine.MedicineType.VIAL
                    ? med.getName() + " | " + vialConc(med) + " mg/ml"
                    : med.getName();
            sb.append(header).append("\n");
            sb.append("  Qty: ").append(totalQty)
              .append("  |  Price: Rs ").append(String.format("%,d", med.getPrice()))
              .append("  |  Value: Rs ").append(String.format("%,d", valuation)).append("\n\n");
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

        // Group by user
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
                sb.append("  ").append(med.getName())
                  .append("\n    Qty: ").append(tx.getQuantity())
                  .append("  Amount: Rs ").append(String.format("%,d", amount)).append("\n");
                if (tx.getNotes() != null && !tx.getNotes().isBlank()) {
                    sb.append("    Note: ").append(tx.getNotes()).append("\n");
                }
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

        // Group inventory by type|specification key
        Map<String, List<Inventory>> bySpec = new HashMap<>();
        Map<String, Medicine> specToMedicine = new HashMap<>();
        for (Inventory inv : inventoryRecords) {
            Medicine m = inv.getMedicine();
            String key = m.getType().name() + "|" + m.getSpecification();
            bySpec.computeIfAbsent(key, k -> new ArrayList<>()).add(inv);
            specToMedicine.putIfAbsent(key, m);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("DAILY REPORT - ").append(today.format(DATE_FMT)).append("\n");
        sb.append("Generated: ").append(nowIST()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");
        sb.append("INVENTORY COUNTS\n");
        sb.append("-".repeat(16)).append("\n");

        for (String[] spec : DAILY_SPEC_ORDER) {
            String type    = spec[0];
            String specVal = spec[1];
            String baseName = spec[2];
            String key = type + "|" + specVal;

            String header = "VIAL".equals(type)
                    ? baseName + " | " + (specToMedicine.containsKey(key)
                            ? vialConc(specToMedicine.get(key)) : "20") + " mg/ml"
                    : baseName;

            sb.append("\n").append(header).append("\n");

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
                sb.append(tx.getQuantity()).append(" x ").append(specLabel);
                if (tx.getNotes() != null && !tx.getNotes().isBlank()) {
                    sb.append("  ").append(tx.getNotes());
                }
                sb.append("\n");
            }
        }

        return new ReportResponse("DAILY_REPORT", nowIST(), sb.toString());
    }
}
