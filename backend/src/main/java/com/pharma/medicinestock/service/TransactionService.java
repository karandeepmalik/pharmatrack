package com.pharma.medicinestock.service;

import com.pharma.medicinestock.dto.ApprovalRequest;
import com.pharma.medicinestock.dto.TransactionRequest;
import com.pharma.medicinestock.dto.TransactionResponse;
import com.pharma.medicinestock.dto.UpdateTransactionRequest;
import com.pharma.medicinestock.entity.MedicineStock;
import com.pharma.medicinestock.entity.Medicine;
import com.pharma.medicinestock.entity.Transaction;
import com.pharma.medicinestock.entity.Transaction.TransactionStatus;
import com.pharma.medicinestock.entity.User;
import com.pharma.medicinestock.exception.InsufficientMedicineStockException;
import com.pharma.medicinestock.exception.InvalidStateTransitionException;
import com.pharma.medicinestock.exception.ResourceNotFoundException;
import com.pharma.medicinestock.entity.TransactionScreenshot;
import com.pharma.medicinestock.mapper.TransactionMapper;
import com.pharma.medicinestock.repository.MedicineStockRepository;
import com.pharma.medicinestock.repository.MedicineRepository;
import com.pharma.medicinestock.repository.TransactionRepository;
import com.pharma.medicinestock.repository.TransactionScreenshotRepository;
import com.pharma.medicinestock.repository.UserRepository;
import com.pharma.medicinestock.util.QuantityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionScreenshotRepository screenshotRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineStockRepository medicineStockRepository;
    private final CurrentStockCalculator currentStockCalculator;
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionScreenshotRepository screenshotRepository,
                              UserRepository userRepository,
                              MedicineRepository medicineRepository,
                              MedicineStockRepository medicineStockRepository,
                              CurrentStockCalculator currentStockCalculator,
                              TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.screenshotRepository = screenshotRepository;
        this.userRepository = userRepository;
        this.medicineRepository = medicineRepository;
        this.medicineStockRepository = medicineStockRepository;
        this.currentStockCalculator = currentStockCalculator;
        this.transactionMapper = transactionMapper;
    }

    @Transactional
    public TransactionResponse submit(TransactionRequest req, String username) {
        validateNotes(req.getNotes());
        validateSubmittedDate(req.getSubmittedDate());
        validateQuantity(req.getQuantity());
        req.setQuantity(QuantityUtil.round(req.getQuantity()));
        User user = findUserByUsername(username);
        Medicine medicine = findMedicineById(req.getMedicineId());

        MedicineStock.MedicineStockType invType = resolveMedicineStockType(req.getMedicineStockType());
        MedicineStock medicineStock = findMedicineStockByType(user.getId(), medicine.getId(), invType);

        // Validate against forward-reconstructed settled stock (same algorithm ReportService uses
        // for historical reports) rather than the raw medicineStock.quantity field, which can drift
        // from the true adjustment/transaction ledger and include not-yet-arrived in-transit stock.
        BigDecimal settledQty = currentStockCalculator.settledQuantity(user.getId(), medicine.getId(), invType);
        if (settledQty.compareTo(req.getQuantity()) < 0) {
            throw new InsufficientMedicineStockException(settledQty, req.getQuantity());
        }
        medicineStock.setQuantity(medicineStock.getQuantity().subtract(req.getQuantity()));
        medicineStockRepository.save(medicineStock);

        LocalDateTime submittedAt = req.getSubmittedDate() != null
                ? req.getSubmittedDate().atStartOfDay()
                : null; // @PrePersist will default to now()

        Transaction saved = transactionRepository.save(Transaction.builder()
                .submittedBy(user).medicine(medicine).quantity(req.getQuantity())
                .status(TransactionStatus.PENDING).notes(req.getNotes())
                .pricePerUnit(req.getPricePerUnit())
                .medicineStockType(invType)
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
        List<Transaction> txList = orderByIds(transactionRepository.findByIdsWithDetails(ids), ids);
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
        List<Transaction> txList = orderByIds(transactionRepository.findByIdsWithDetails(ids), ids);
        List<TransactionResponse> content = txList.stream().map(transactionMapper::toResponse).toList();
        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    /**
     * findByIdsWithDetails() has its own fixed ORDER BY that has nothing to do with the
     * page's intended order (which comes from whichever id-lookup query produced {@code ids}) —
     * re-sort the fetched entities to match {@code ids} instead of trusting the two queries'
     * ORDER BY clauses to happen to agree.
     */
    private List<Transaction> orderByIds(List<Transaction> txList, List<Long> ids) {
        Map<Long, Transaction> byId = new LinkedHashMap<>();
        for (Transaction t : txList) byId.put(t.getId(), t);
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
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
            MedicineStock.MedicineStockType rollbackType = tx.getMedicineStockType() != null
                    ? tx.getMedicineStockType()
                    : MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
            MedicineStock inv = findMedicineStockByType(tx.getSubmittedBy().getId(), tx.getMedicine().getId(), rollbackType);
            inv.setQuantity(inv.getQuantity().add(tx.getQuantity()));
            medicineStockRepository.save(inv);
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
            MedicineStock.MedicineStockType rollbackType = tx.getMedicineStockType() != null
                    ? tx.getMedicineStockType()
                    : MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
            MedicineStock inv = findMedicineStockByType(
                    tx.getSubmittedBy().getId(), tx.getMedicine().getId(), rollbackType);
            inv.setQuantity(inv.getQuantity().add(tx.getQuantity()));
            medicineStockRepository.save(inv);
        }
        transactionRepository.delete(tx);
    }

    /**
     * Lets a user delete their own dispatch record while it's still PENDING (i.e. before an
     * admin has acted on it). Restores the medicine stock that was deducted at submission time.
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

        MedicineStock.MedicineStockType rollbackType = tx.getMedicineStockType() != null
                ? tx.getMedicineStockType() : MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
        MedicineStock inv = findMedicineStockByType(
                tx.getSubmittedBy().getId(), tx.getMedicine().getId(), rollbackType);
        inv.setQuantity(inv.getQuantity().add(tx.getQuantity()));
        medicineStockRepository.save(inv);

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

    // submit() binds quantity from a multipart @RequestParam, not @RequestBody, so
    // TransactionRequest's @DecimalMin("0.1") annotation never runs (@Valid isn't applied) —
    // this is the actual enforcement. Without it, a negative quantity passes the "insufficient
    // stock" check trivially and medicineStock.subtract(negative) credits the user's own stock.
    private void validateQuantity(BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.valueOf(0.1)) < 0)
            throw new IllegalArgumentException("Quantity must be at least 0.1");
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }
    private Medicine findMedicineById(Long id) {
        return medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", id));
    }
    private MedicineStock findMedicineStockByType(Long userId, Long medicineId, MedicineStock.MedicineStockType type) {
        return medicineStockRepository
                .findByUserIdAndMedicineIdAndMedicineStockType(userId, medicineId, type)
                .orElseThrow(() -> new ResourceNotFoundException("MedicineStock",
                        "userId=" + userId + ",medicineId=" + medicineId + ",type=" + type));
    }

    private MedicineStock.MedicineStockType resolveMedicineStockType(String rawType) {
        if (rawType == null || rawType.isBlank()) return MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
        try {
            return MedicineStock.MedicineStockType.valueOf(rawType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
        }
    }
}
