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
    static final String SYSTEM_USER = "lostinventory";
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;

    @Transactional
    public InventoryResponse addSystemInventory(SystemInventoryRequest req) {
        User system = userRepository.findByUsername(SYSTEM_USER)
            .orElseThrow(() -> new ResourceNotFoundException("System user", SYSTEM_USER));
        Medicine medicine = medicineRepository.findById(req.getMedicineId())
            .orElseThrow(() -> new ResourceNotFoundException("Medicine", req.getMedicineId()));
        Inventory inv = inventoryRepository.findByUserIdAndMedicineId(system.getId(), medicine.getId())
            .orElse(Inventory.builder().user(system).medicine(medicine).quantity(0).build());
        inv.setQuantity(inv.getQuantity() + req.getQuantity());
        return toResponse(inventoryRepository.save(inv));
    }

    @Transactional
    public InventoryResponse allocateToUser(InventoryRequest req) {
        User system = userRepository.findByUsername(SYSTEM_USER)
            .orElseThrow(() -> new ResourceNotFoundException("System user", SYSTEM_USER));
        User user = userRepository.findById(req.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", req.getUserId()));
        Medicine medicine = medicineRepository.findById(req.getMedicineId())
            .orElseThrow(() -> new ResourceNotFoundException("Medicine", req.getMedicineId()));
        Inventory sysInv = inventoryRepository.findByUserIdAndMedicineId(system.getId(), medicine.getId())
            .orElseThrow(() -> new InsufficientInventoryException(0, req.getQuantity()));
        if (sysInv.getQuantity() < req.getQuantity())
            throw new InsufficientInventoryException(sysInv.getQuantity(), req.getQuantity());
        sysInv.setQuantity(sysInv.getQuantity() - req.getQuantity());
        inventoryRepository.save(sysInv);
        Inventory userInv = inventoryRepository.findByUserIdAndMedicineId(user.getId(), medicine.getId())
            .orElse(Inventory.builder().user(user).medicine(medicine).quantity(0).build());
        userInv.setQuantity(userInv.getQuantity() + req.getQuantity());
        return toResponse(inventoryRepository.save(userInv));
    }

    @Transactional(readOnly=true)
    public List<InventoryResponse> getSystemInventory() {
        User system = userRepository.findByUsername(SYSTEM_USER)
            .orElseThrow(() -> new ResourceNotFoundException("System user", SYSTEM_USER));
        return inventoryRepository.findByUserId(system.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly=true)
    public List<InventoryResponse> getAvailableForUser(Long userId) {
        return inventoryRepository.findAvailableByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly=true)
    public List<InventoryResponse> getAllForUser(Long userId) {
        return inventoryRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly=true)
    public List<InventoryResponse> getAll() {
        return inventoryRepository.findAll().stream()
            .filter(i -> !SYSTEM_USER.equals(i.getUser().getUsername()))
            .map(this::toResponse).toList();
    }

    private InventoryResponse toResponse(Inventory i) {
        InventoryResponse r = new InventoryResponse();
        r.setId(i.getId()); r.setUserId(i.getUser().getId()); r.setUsername(i.getUser().getUsername());
        r.setMedicineId(i.getMedicine().getId()); r.setMedicineName(i.getMedicine().getName());
        r.setMedicineType(i.getMedicine().getType().name());
        r.setSpecification(i.getMedicine().getSpecification());
        r.setSpecUnit(i.getMedicine().getType() == Medicine.MedicineType.VIAL ? "mg/ml" : "mg");
        r.setPharmaId(i.getMedicine().getPharmaCompany().getId());
        r.setPharmaName(i.getMedicine().getPharmaCompany().getName());
        r.setQuantity(i.getQuantity()); return r;
    }
}
