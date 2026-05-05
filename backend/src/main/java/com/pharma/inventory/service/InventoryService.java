package com.pharma.inventory.service;
import com.pharma.inventory.dto.*;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.exception.*;
import com.pharma.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;

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
            invType = Inventory.InventoryType.REGULAR;
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

        User adjustedBy = userRepository.findByUsername(adjustedByUsername).orElse(null);
        inventoryAdjustmentRepository.save(InventoryAdjustment.builder()
            .user(user)
            .medicine(medicine)
            .quantity(req.getQuantity())
            .adjustmentType(req.getAdjustmentType())
            .note(req.getNote())
            .internalMovement(req.isInternalMovement())
            .inventoryType(invType)
            .adjustedAt(LocalDateTime.now())
            .adjustedBy(adjustedBy)
            .build());

        return response;
    }

    @Transactional(readOnly=true)
    public List<InventoryResponse> getAvailableForUser(Long userId) {
        // Return both REGULAR and ADMIN_STOCK so the user can choose which bucket to draw from
        List<Inventory> regular = inventoryRepository
            .findAvailableByUserIdAndType(userId, Inventory.InventoryType.REGULAR);
        List<Inventory> adminStock = inventoryRepository
            .findAvailableByUserIdAndType(userId, Inventory.InventoryType.ADMIN_STOCK);
        List<InventoryResponse> result = new java.util.ArrayList<>();
        regular.stream().map(this::toResponse).forEach(result::add);
        adminStock.stream().map(this::toResponse).forEach(result::add);
        return result;
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
        r.setInventoryType(i.getInventoryType() != null ? i.getInventoryType().name() : "REGULAR");
        return r;
    }
}
