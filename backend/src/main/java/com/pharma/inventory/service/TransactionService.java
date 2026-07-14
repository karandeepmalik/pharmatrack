package com.pharma.inventory.service;

import com.pharma.inventory.dto.ApprovalRequest;
import com.pharma.inventory.dto.TransactionRequest;
import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.dto.UpdateTransactionRequest;
import com.pharma.inventory.entity.Inventory;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.Transaction.TransactionStatus;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.exception.InsufficientInventoryException;
import com.pharma.inventory.exception.InvalidStateTransitionException;
import com.pharma.inventory.exception.ResourceNotFoundException;
import com.pharma.inventory.entity.TransactionScreenshot;
import com.pharma.inventory.mapper.TransactionMapper;
import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import com.pharma.inventory.repository.InventoryRepository;
import com.pharma.inventory.repository.MedicineRepository;
import com.pharma.inventory.repository.TransactionRepository;
import com.pharma.inventory.repository.TransactionScreenshotRepository;
import com.pharma.inventory.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionScreenshotRepository screenshotRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final InventoryRepository inventoryRepository;
    private final CurrentStockCalculator currentStockCalculator;
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionScreenshotRepository screenshotRepository,
                              UserRepository userRepository,
                              MedicineRepository medicineRepository,
                              InventoryRepository inventoryRepository,
                              CurrentStockCalculator currentStockCalculator,
                              TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.screenshotRepository = screenshotRepository;
        this.userRepository = userRepository;
        this.medicineRepository = medicineRepository;
        this.inventoryRepository = inventoryRepository;
        this.currentStockCalculator = currentStockCalculator;
        this.transactionMapper = transactionMapper;
    }

    @Transactional
    public TransactionResponse submit(TransactionRequest req, String username) {
        validateNotes(req.getNotes());
        validateSubmittedDate(req.getSubmittedDate());
        User user = findUserByUsername(username);
        Medicine medicine = findMedicineById(req.getMedicineId());

        Inventory.InventoryType invType = resolveInventoryType(req.getInventoryType());
        Inventory inventory = findInventoryByType(user.getId(), medicine.getId(), invType);

        // Validate against forward-reconstructed settled stock (same algorithm ReportService uses
        // for historical reports) rather than the raw inventory.quantity field, which can drift
        // from the true adjustment/transaction ledger and include not-yet-arrived in-transit stock.
        int settledQty = currentStockCalculator.settledQuantity(user.getId(), medicine.getId(), invType);
        if (settledQty < req.getQuantity()) {
            throw new InsufficientInventoryException(settledQty, req.getQuantity());
        }
        inventory.setQuantity(inventory.getQuantity() - req.getQuantity());
        inventoryRepository.save(inventory);

        LocalDateTime submittedAt = req.getSubmittedDate() != null
                ? req.getSubmittedDate().atStartOfDay()
                : null; // @PrePersist will default to now()

        Transaction saved = transactionRepository.save(Transaction.builder()
                .submittedBy(user).medicine(medicine).quantity(req.getQuantity())
                .status(TransactionStatus.PENDING).notes(req.getNotes())
                .pricePerUnit(req.getPricePerUnit())
                .inventoryType(invType)
                .submittedAt(submittedAt)
                .build());

        List<String> dataList = req.getPaymentScreenshots();
        List<String> mimeList = req.getPaymentScreenshotTypes();
        for (int i = 0; i < dataList.size(); i++) {
            screenshotRepository.save(TransactionScreenshot.builder()
                    .transaction(saved)
                    .data(dataList.get(i))
                    .mimeType(mimeList.get(i))
                    .displayOrder(i)
                    .build());
        }

        return transactionMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAllPaged(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Long> idPage;
        if (status == null || "ALL".equalsIgnoreCase(status)) {
            idPage = transactionRepository.findAllIds(pageable);
        } else {
            TransactionStatus txStatus = TransactionStatus.valueOf(status.toUpperCase());
            idPage = transactionRepository.findIdsByStatus(txStatus, pageable);
        }
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }
        List<Transaction> txList = transactionRepository.findByIdsWithDetails(ids);
        List<TransactionResponse> content = txList.stream().map(transactionMapper::toResponse).toList();
        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getByUserPaged(String username, int page, int size) {
        User user = findUserByUsername(username);
        PageRequest pageable = PageRequest.of(page, size);
        Page<Long> idPage = transactionRepository.findIdsByUser(user, pageable);
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }
        List<Transaction> txList = transactionRepository.findByIdsWithDetails(ids);
        List<TransactionResponse> content = txList.stream().map(transactionMapper::toResponse).toList();
        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getHistory(LocalDate from, LocalDate to, String status) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end   = to.plusDays(1).atStartOfDay();

        List<Transaction> txList;
        if ("ALL".equalsIgnoreCase(status)) {
            txList = transactionRepository.findBySubmittedAtBetween(start, end);
        } else {
            TransactionStatus txStatus = TransactionStatus.valueOf(status.toUpperCase());
            txList = transactionRepository.findBySubmittedAtBetweenAndStatus(start, end, txStatus);
        }
        return txList.stream().map(transactionMapper::toResponse).toList();
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
            Inventory.InventoryType rollbackType = tx.getInventoryType() != null
                    ? tx.getInventoryType()
                    : Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
            Inventory inv = findInventoryByType(tx.getSubmittedBy().getId(), tx.getMedicine().getId(), rollbackType);
            inv.setQuantity(inv.getQuantity() + tx.getQuantity());
            inventoryRepository.save(inv);
        }
        tx.setApprovedBy(admin);
        tx.setApprovedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        return transactionMapper.toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public void deleteTransaction(Long id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        if (tx.getStatus() == TransactionStatus.PENDING || tx.getStatus() == TransactionStatus.APPROVED) {
            Inventory.InventoryType rollbackType = tx.getInventoryType() != null
                    ? tx.getInventoryType()
                    : Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
            Inventory inv = findInventoryByType(
                    tx.getSubmittedBy().getId(), tx.getMedicine().getId(), rollbackType);
            inv.setQuantity(inv.getQuantity() + tx.getQuantity());
            inventoryRepository.save(inv);
        }
        transactionRepository.delete(tx);
    }

    /**
     * Lets a user delete their own dispatch record while it's still PENDING (i.e. before an
     * admin has acted on it). Restores the inventory that was deducted at submission time.
     */
    @Transactional
    public void deleteOwnPending(Long id, String username) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        User user = findUserByUsername(username);
        if (!tx.getSubmittedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only delete your own dispatch records");
        }
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidStateTransitionException(tx.getStatus().name(), "delete");
        }

        Inventory.InventoryType rollbackType = tx.getInventoryType() != null
                ? tx.getInventoryType() : Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
        Inventory inv = findInventoryByType(
                tx.getSubmittedBy().getId(), tx.getMedicine().getId(), rollbackType);
        inv.setQuantity(inv.getQuantity() + tx.getQuantity());
        inventoryRepository.save(inv);

        transactionRepository.delete(tx);
    }

    @Transactional
    public TransactionResponse updateNotes(Long id, UpdateTransactionRequest req) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
        validateNotes(req.getNotes());
        tx.setNotes(req.getNotes().trim());
        return transactionMapper.toResponse(transactionRepository.save(tx));
    }

    private void validateNotes(String notes) {
        if (notes == null || notes.isBlank())
            throw new IllegalArgumentException("An adjustment note is required");
        String t = notes.trim();
        if (t.length() < 5 || t.length() > 500)
            throw new IllegalArgumentException("Note must be between 5 and 500 characters");
    }

    private void validateSubmittedDate(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Dispatch date cannot be in the future");
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }
    private Medicine findMedicineById(Long id) {
        return medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", id));
    }
    private Inventory findRegularInventory(Long userId, Long medicineId) {
        return findInventoryByType(userId, medicineId, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
    }

    private Inventory findInventoryByType(Long userId, Long medicineId, Inventory.InventoryType type) {
        return inventoryRepository
                .findByUserIdAndMedicineIdAndInventoryType(userId, medicineId, type)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory",
                        "userId=" + userId + ",medicineId=" + medicineId + ",type=" + type));
    }

    private Inventory.InventoryType resolveInventoryType(String rawType) {
        if (rawType == null || rawType.isBlank()) return Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
        try {
            return Inventory.InventoryType.valueOf(rawType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
        }
    }
}
