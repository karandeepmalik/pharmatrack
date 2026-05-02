package com.pharma.inventory.service;
import com.pharma.inventory.dto.*;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.exception.ResourceNotFoundException;
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
    public InventoryResponse addInventory(InventoryRequest req){
        User user=userRepository.findById(req.getUserId()).orElseThrow(()->new ResourceNotFoundException("User",req.getUserId()));
        Medicine medicine=medicineRepository.findById(req.getMedicineId()).orElseThrow(()->new ResourceNotFoundException("Medicine",req.getMedicineId()));
        Inventory inv=inventoryRepository.findByUserIdAndMedicineId(user.getId(),medicine.getId())
            .orElse(Inventory.builder().user(user).medicine(medicine).quantity(0).build());
        inv.setQuantity(inv.getQuantity()+req.getQuantity());
        return toResponse(inventoryRepository.save(inv));
    }
    @Transactional(readOnly=true)
    public List<InventoryResponse> getAvailableForUser(Long userId){ return inventoryRepository.findAvailableByUserId(userId).stream().map(this::toResponse).toList(); }
    @Transactional(readOnly=true)
    public List<InventoryResponse> getAllForUser(Long userId){ return inventoryRepository.findByUserId(userId).stream().map(this::toResponse).toList(); }
    @Transactional(readOnly=true)
    public List<InventoryResponse> getAll(){ return inventoryRepository.findAll().stream().map(this::toResponse).toList(); }
    private InventoryResponse toResponse(Inventory i){
        InventoryResponse r=new InventoryResponse();
        r.setId(i.getId()); r.setUserId(i.getUser().getId()); r.setUsername(i.getUser().getUsername());
        r.setMedicineId(i.getMedicine().getId()); r.setMedicineName(i.getMedicine().getName());
        r.setMedicineType(i.getMedicine().getType().name()); r.setSpecification(i.getMedicine().getSpecification());
        r.setPharmaId(i.getMedicine().getPharmaCompany().getId()); r.setPharmaName(i.getMedicine().getPharmaCompany().getName());
        r.setQuantity(i.getQuantity()); return r;
    }
}
