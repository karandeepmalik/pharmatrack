package com.pharma.inventory.service;
import com.pharma.inventory.dto.*;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.exception.*;
import com.pharma.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;

    @Transactional
    public InventoryResponse adjustInventory(AdjustInventoryRequest req) {
        User user = userRepository.findById(req.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", req.getUserId()));
        Medicine medicine = medicineRepository.findById(req.getMedicineId())
            .orElseThrow(() -> new ResourceNotFoundException("Medicine", req.getMedicineId()));
        Inventory inv = inventoryRepository.findByUserIdAndMedicineId(user.getId(), medicine.getId())
            .orElse(Inventory.builder().user(user).medicine(medicine).quantity(0).build());
        if ("REDUCE".equals(req.getAdjustmentType())) {
            if (inv.getQuantity() < req.getQuantity())
                throw new InsufficientInventoryException(inv.getQuantity(), req.getQuantity());
            inv.setQuantity(inv.getQuantity() - req.getQuantity());
        } else {
            inv.setQuantity(inv.getQuantity() + req.getQuantity());
        }
        inv.setLastNote(req.getNote());
        return toResponse(inventoryRepository.save(inv));
    }

    @Transactional(readOnly=true)
    public List<InventoryResponse> getAvailableForUser(Long userId) {
        return inventoryRepository.findAvailableByUserId(userId).stream().map(this::toResponse).toList();
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
        r.setSpecUnit(i.getMedicine().getType() == Medicine.MedicineType.VIAL ? "mg/ml" : "mg (10 Tablets)");
        r.setPharmaId(i.getMedicine().getPharmaCompany().getId());
        r.setPharmaName(i.getMedicine().getPharmaCompany().getName());
        r.setQuantity(i.getQuantity());
        r.setPrice(i.getMedicine().getPrice());
        r.setLastNote(i.getLastNote());
        return r;
    }
}
