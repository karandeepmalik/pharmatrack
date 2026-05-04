package com.pharma.inventory.service;

import com.pharma.inventory.dto.ApprovalRequest;
import com.pharma.inventory.dto.TransactionRequest;
import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.entity.Inventory;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.Transaction.TransactionStatus;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.exception.InsufficientInventoryException;
import com.pharma.inventory.exception.InvalidStateTransitionException;
import com.pharma.inventory.exception.ResourceNotFoundException;
import com.pharma.inventory.mapper.TransactionMapper;
import com.pharma.inventory.repository.InventoryRepository;
import com.pharma.inventory.repository.MedicineRepository;
import com.pharma.inventory.repository.TransactionRepository;
import com.pharma.inventory.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final InventoryRepository inventoryRepository;
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository,
                              UserRepository userRepository,
                              MedicineRepository medicineRepository,
                              InventoryRepository inventoryRepository,
                              TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.medicineRepository = medicineRepository;
        this.inventoryRepository = inventoryRepository;
        this.transactionMapper = transactionMapper;
    }

    @Transactional
    public TransactionResponse submit(TransactionRequest req, String username) {
        validateNotes(req.getNotes());
        User user = findUserByUsername(username);
        Medicine medicine = findMedicineById(req.getMedicineId());
        Inventory inventory = findInventory(user.getId(), medicine.getId());

        if (inventory.getQuantity() < req.getQuantity()) {
            throw new InsufficientInventoryException(inventory.getQuantity(), req.getQuantity());
        }
        inventory.setQuantity(inventory.getQuantity() - req.getQuantity());
        inventoryRepository.save(inventory);

        Transaction saved = transactionRepository.save(Transaction.builder()
                .submittedBy(user).medicine(medicine).quantity(req.getQuantity())
                .status(TransactionStatus.PENDING).notes(req.getNotes())
                .paymentScreenshot(req.getPaymentScreenshot())
                .paymentScreenshotType(req.getPaymentScreenshotType())
                .pricePerUnit(req.getPricePerUnit())
                .build());
        return transactionMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAll() {
        return transactionRepository.findAllByOrderBySubmittedAtDesc()
                .stream().map(transactionMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getByUser(String username) {
        User user = findUserByUsername(username);
        return transactionRepository.findBySubmittedByOrderBySubmittedAtDesc(user)
                .stream().map(transactionMapper::toResponse).toList();
    }

    @Transactional
    public TransactionResponse approve(Long id, ApprovalRequest req, String adminUsername) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    tx.getStatus().name(), req.isApproved() ? "approve" : "reject");
        }
        User admin = findUserByUsername(adminUsername);
        if (req.isApproved()) {
            tx.setStatus(TransactionStatus.APPROVED);
            if (req.getNewPrice() != null) {
                Medicine m = tx.getMedicine();
                m.setPrice(req.getNewPrice());
                medicineRepository.save(m);
            }
        } else {
            tx.setStatus(TransactionStatus.REJECTED);
            Inventory inv = findInventory(tx.getSubmittedBy().getId(), tx.getMedicine().getId());
            inv.setQuantity(inv.getQuantity() + tx.getQuantity());
            inventoryRepository.save(inv);
        }
        tx.setApprovedBy(admin);
        tx.setApprovedAt(LocalDateTime.now());
        return transactionMapper.toResponse(transactionRepository.save(tx));
    }

    private void validateNotes(String notes) {
        if (notes == null || notes.isBlank())
            throw new IllegalArgumentException("An adjustment note is required");
        String t = notes.trim();
        if (t.length() < 5 || t.length() > 500)
            throw new IllegalArgumentException("Note must be between 5 and 500 characters");
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }
    private Medicine findMedicineById(Long id) {
        return medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", id));
    }
    private Inventory findInventory(Long userId, Long medicineId) {
        return inventoryRepository.findByUserIdAndMedicineId(userId, medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory",
                        "userId=" + userId + ",medicineId=" + medicineId));
    }
}
