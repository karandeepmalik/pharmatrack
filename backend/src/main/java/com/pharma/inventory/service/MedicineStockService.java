package com.pharma.inventory.service;
import com.pharma.inventory.dto.*;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.exception.*;
import com.pharma.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class MedicineStockService {
    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final CurrentStockCalculator currentStockCalculator;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Transactional
    public InventoryResponse adjustInventory(AdjustInventoryRequest req, String adjustedByUsername) {
        User user = userRepository.findById(req.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", req.getUserId()));
        if (user.getRole() == User.Role.ADMIN) {
            throw new IllegalArgumentException("Admin user cannot hold inventory");
        }
        Medicine medicine = medicineRepository.findById(req.getMedicineId())
            .orElseThrow(() -> new ResourceNotFoundException("Medicine", req.getMedicineId()));

        Inventory.InventoryType invType;
        try {
            invType = Inventory.InventoryType.valueOf(req.getInventoryType());
        } catch (IllegalArgumentException e) {
            invType = Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
        }

        Inventory inv = inventoryRepository
            .findByUserIdAndMedicineIdAndInventoryType(user.getId(), medicine.getId(), invType)
            .orElse(Inventory.builder()
                .user(user).medicine(medicine).quantity(0)
                .inventoryType(invType).build());

        if ("REDUCE".equals(req.getAdjustmentType())) {
            if (inv.getQuantity() < req.getQuantity())
                throw new InsufficientInventoryException(inv.getQuantity(), req.getQuantity());
            inv.setQuantity(inv.getQuantity() - req.getQuantity());
        } else {
            inv.setQuantity(inv.getQuantity() + req.getQuantity());
        }
        inv.setLastNote(req.getNote());
        InventoryResponse response = toResponse(inventoryRepository.save(inv));

        LocalDateTime adjustedAt = req.getAdjustmentDate() != null
            ? req.getAdjustmentDate().atStartOfDay()
            : LocalDateTime.now(IST);

        User adjustedBy = userRepository.findByUsername(adjustedByUsername).orElse(null);
        inventoryAdjustmentRepository.save(InventoryAdjustment.builder()
            .user(user)
            .medicine(medicine)
            .quantity(req.getQuantity())
            .adjustmentType(req.getAdjustmentType())
            .note(req.getNote())
            .internalMovement(req.isInternalMovement())
            .inTransit(req.isInTransit())
            .wasInTransit(req.isInTransit())
            .transitDays(req.isInTransit() ? req.getTransitDays() : 2)
            .inventoryType(invType)
            .adjustedAt(adjustedAt)
            .adjustedBy(adjustedBy)
            .build());

        return response;
    }

    private static final DateTimeFormatter ADJ_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustments(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end   = to.plusDays(1).atStartOfDay();
        return inventoryAdjustmentRepository.findWithDetailsBetween(start, end)
                .stream().map(this::toAdjResponse).toList();
    }

    @Transactional
    public void deleteAdjustment(Long id) {
        InventoryAdjustment adj = inventoryAdjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryAdjustment", id));

        // Reverse the inventory effect of this adjustment
        inventoryRepository.findByUserIdAndMedicineIdAndInventoryType(
                adj.getUser().getId(), adj.getMedicine().getId(), adj.getInventoryType())
                .ifPresent(inv -> {
                    if ("ADD".equals(adj.getAdjustmentType())) {
                        inv.setQuantity(Math.max(0, inv.getQuantity() - adj.getQuantity()));
                    } else {
                        inv.setQuantity(inv.getQuantity() + adj.getQuantity());
                    }
                    inventoryRepository.save(inv);
                });

        inventoryAdjustmentRepository.deleteById(id);
    }

    private InventoryAdjustmentResponse toAdjResponse(InventoryAdjustment a) {
        return InventoryAdjustmentResponse.builder()
                .id(a.getId())
                .userId(a.getUser().getId())
                .username(a.getUser().getUsername())
                .userFullName(a.getUser().getFullName())
                .medicineId(a.getMedicine().getId())
                .medicineName(a.getMedicine().getName())
                .medicineType(a.getMedicine().getType().name())
                .specification(a.getMedicine().getSpecification())
                .quantity(a.getQuantity())
                .adjustmentType(a.getAdjustmentType())
                .note(a.getNote())
                .adjustedAt(a.getAdjustedAt() != null ? a.getAdjustedAt().format(ADJ_FMT) : null)
                .adjustedByUsername(a.getAdjustedBy() != null ? a.getAdjustedBy().getUsername() : null)
                .inTransit(a.isInTransit())
                .transitDays(a.getTransitDays())
                .internalMovement(a.isInternalMovement())
                .inventoryType(a.getInventoryType() != null ? a.getInventoryType().name() : "REGULAR_MEDICINE_STOCK")
                .build();
    }

    @Transactional(readOnly=true)
    public List<InventoryResponse> getAvailableForUser(Long userId) {
        // Return both REGULAR and ADMIN_STOCK so the user can choose which bucket to draw from
        List<Inventory> regular = inventoryRepository
            .findAvailableByUserIdAndType(userId, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        List<Inventory> adminStock = inventoryRepository
            .findAvailableByUserIdAndType(userId, Inventory.InventoryType.ADMIN_MEDICINE_STOCK);

        // Same forward-reconstruction ReportService uses for historical reports — so what's
        // shown as "available" can never disagree with what the daily report shows as on hand.
        Map<String, Integer> settledByBucket = currentStockCalculator.settledQuantitiesForUser(userId);

        List<InventoryResponse> result = new java.util.ArrayList<>();
        regular.stream().map(i -> toDispatchableResponse(i, settledByBucket)).forEach(result::add);
        adminStock.stream().map(i -> toDispatchableResponse(i, settledByBucket)).forEach(result::add);
        return result;
    }

    private InventoryResponse toDispatchableResponse(Inventory i, Map<String, Integer> settledByBucket) {
        InventoryResponse r = toResponse(i);
        String key = i.getMedicine().getId() + "|" + i.getInventoryType().name();
        r.setQuantity(settledByBucket.getOrDefault(key, 0));
        return r;
    }

    @Transactional(readOnly=true)
    public List<InventoryResponse> getAll() {
        return inventoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    private InventoryResponse toResponse(Inventory i) {
        InventoryResponse r = new InventoryResponse();
        r.setId(i.getId()); r.setUserId(i.getUser().getId()); r.setUsername(i.getUser().getUsername());
        r.setMedicineId(i.getMedicine().getId()); r.setMedicineName(i.getMedicine().getName());
        r.setMedicineType(i.getMedicine().getType().name());
        r.setSpecification(i.getMedicine().getSpecification());
        r.setConcentrationMgPerMl(i.getMedicine().getConcentrationMgPerMl());
        r.setSpecUnit(i.getMedicine().getType() == Medicine.MedicineType.VIAL ? "ml" : "mg (10 Tablets)");
        r.setPharmaId(i.getMedicine().getPharmaCompany().getId());
        r.setPharmaName(i.getMedicine().getPharmaCompany().getName());
        r.setQuantity(i.getQuantity());
        r.setPrice(i.getMedicine().getPrice());
        r.setLastNote(i.getLastNote());
        r.setInventoryType(i.getInventoryType() != null ? i.getInventoryType().name() : "REGULAR_MEDICINE_STOCK");
        return r;
    }
}
