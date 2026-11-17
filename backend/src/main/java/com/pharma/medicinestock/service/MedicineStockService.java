package com.pharma.medicinestock.service;
import com.pharma.medicinestock.dto.*;
import com.pharma.medicinestock.entity.*;
import com.pharma.medicinestock.exception.*;
import com.pharma.medicinestock.repository.*;
import com.pharma.medicinestock.util.QuantityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class MedicineStockService {
    private final MedicineStockRepository medicineStockRepository;
    private final MedicineStockAdjustmentRepository medicineStockAdjustmentRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final CurrentStockCalculator currentStockCalculator;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Transactional
    public MedicineStockResponse adjustMedicineStock(AdjustMedicineStockRequest req, String adjustedByUsername) {
        req.setQuantity(QuantityUtil.round(req.getQuantity()));

        User user = userRepository.findById(req.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", req.getUserId()));
        if (user.getRole() == User.Role.ADMIN) {
            throw new IllegalArgumentException("Admin user cannot hold medicine stock");
        }
        Medicine medicine = medicineRepository.findById(req.getMedicineId())
            .orElseThrow(() -> new ResourceNotFoundException("Medicine", req.getMedicineId()));

        MedicineStock.MedicineStockType invType;
        try {
            invType = MedicineStock.MedicineStockType.valueOf(req.getMedicineStockType());
        } catch (IllegalArgumentException e) {
            invType = MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
        }

        MedicineStock inv = medicineStockRepository
            .findByUserIdAndMedicineIdAndMedicineStockType(user.getId(), medicine.getId(), invType)
            .orElse(MedicineStock.builder()
                .user(user).medicine(medicine).quantity(BigDecimal.ZERO)
                .medicineStockType(invType).build());

        if ("REDUCE".equals(req.getAdjustmentType())) {
            if (inv.getQuantity().compareTo(req.getQuantity()) < 0)
                throw new InsufficientMedicineStockException(inv.getQuantity(), req.getQuantity());
            inv.setQuantity(inv.getQuantity().subtract(req.getQuantity()));
        } else {
            inv.setQuantity(inv.getQuantity().add(req.getQuantity()));
        }
        inv.setLastNote(req.getNote());
        MedicineStockResponse response = toResponse(medicineStockRepository.save(inv));

        LocalDateTime adjustedAt = req.getAdjustmentDate() != null
            ? req.getAdjustmentDate().atStartOfDay()
            : LocalDateTime.now(IST);

        User adjustedBy = userRepository.findByUsername(adjustedByUsername).orElse(null);
        medicineStockAdjustmentRepository.save(MedicineStockAdjustment.builder()
            .user(user)
            .medicine(medicine)
            .quantity(req.getQuantity())
            .adjustmentType(req.getAdjustmentType())
            .note(req.getNote())
            .internalMovement(req.isInternalMovement())
            .inTransit(req.isInTransit())
            .wasInTransit(req.isInTransit())
            .transitDays(req.isInTransit() ? req.getTransitDays() : 2)
            .medicineStockType(invType)
            .adjustedAt(adjustedAt)
            .adjustedBy(adjustedBy)
            .build());

        return response;
    }

    private static final DateTimeFormatter ADJ_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Transactional(readOnly = true)
    public List<MedicineStockAdjustmentResponse> getAdjustments(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end   = to.plusDays(1).atStartOfDay();
        return medicineStockAdjustmentRepository.findWithDetailsBetween(start, end)
                .stream().map(this::toAdjResponse).toList();
    }

    @Transactional
    public void deleteAdjustment(Long id) {
        MedicineStockAdjustment adj = medicineStockAdjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicineStockAdjustment", id));

        // Reverse the medicine stock effect of this adjustment
        medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(
                adj.getUser().getId(), adj.getMedicine().getId(), adj.getMedicineStockType())
                .ifPresent(inv -> {
                    if ("ADD".equals(adj.getAdjustmentType())) {
                        inv.setQuantity(inv.getQuantity().subtract(adj.getQuantity()).max(BigDecimal.ZERO));
                    } else {
                        inv.setQuantity(inv.getQuantity().add(adj.getQuantity()));
                    }
                    medicineStockRepository.save(inv);
                });

        medicineStockAdjustmentRepository.deleteById(id);
    }

    /**
     * Admin edit of a past medicine stock adjustment record — previously the only way to correct
     * one was to delete it outright. Reconciles the affected medicineStock bucket(s) the same way
     * {@link #deleteAdjustment} + {@link #adjustMedicineStock} would in combination: first undo
     * the adjustment's old effect, then (re)apply its new one — against the same bucket if the
     * type didn't change, or across both old and new buckets if it did (mirrors
     * TransactionService.reconcileStockForEdit's two-branch shape for the same reason).
     */
    @Transactional
    public MedicineStockAdjustmentResponse updateAdjustment(Long id, UpdateMedicineStockAdjustmentRequest req) {
        MedicineStockAdjustment adj = medicineStockAdjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicineStockAdjustment", id));

        BigDecimal oldQuantity = adj.getQuantity();
        String oldAdjustmentType = adj.getAdjustmentType();
        MedicineStock.MedicineStockType oldType = adj.getMedicineStockType();

        BigDecimal newQuantity = req.getQuantity() != null ? QuantityUtil.round(req.getQuantity()) : oldQuantity;
        String newAdjustmentType = req.getAdjustmentType() != null ? req.getAdjustmentType() : oldAdjustmentType;
        MedicineStock.MedicineStockType newType = resolveBucketType(req.getMedicineStockType(), oldType);

        Long userId = adj.getUser().getId();
        Long medicineId = adj.getMedicine().getId();

        if (oldType == newType) {
            MedicineStock bucket = medicineStockRepository
                    .findByUserIdAndMedicineIdAndMedicineStockType(userId, medicineId, oldType)
                    .orElseGet(() -> MedicineStock.builder()
                            .user(adj.getUser()).medicine(adj.getMedicine())
                            .quantity(BigDecimal.ZERO).medicineStockType(oldType).build());
            BigDecimal reverted = "ADD".equals(oldAdjustmentType)
                    ? bucket.getQuantity().subtract(oldQuantity).max(BigDecimal.ZERO)
                    : bucket.getQuantity().add(oldQuantity);
            bucket.setQuantity("ADD".equals(newAdjustmentType)
                    ? reverted.add(newQuantity) : reverted.subtract(newQuantity));
            medicineStockRepository.save(bucket);
        } else {
            medicineStockRepository.findByUserIdAndMedicineIdAndMedicineStockType(userId, medicineId, oldType)
                    .ifPresent(bucket -> {
                        bucket.setQuantity("ADD".equals(oldAdjustmentType)
                                ? bucket.getQuantity().subtract(oldQuantity).max(BigDecimal.ZERO)
                                : bucket.getQuantity().add(oldQuantity));
                        medicineStockRepository.save(bucket);
                    });
            MedicineStock newBucket = medicineStockRepository
                    .findByUserIdAndMedicineIdAndMedicineStockType(userId, medicineId, newType)
                    .orElseGet(() -> MedicineStock.builder()
                            .user(adj.getUser()).medicine(adj.getMedicine())
                            .quantity(BigDecimal.ZERO).medicineStockType(newType).build());
            newBucket.setQuantity("ADD".equals(newAdjustmentType)
                    ? newBucket.getQuantity().add(newQuantity) : newBucket.getQuantity().subtract(newQuantity));
            medicineStockRepository.save(newBucket);
        }

        adj.setQuantity(newQuantity);
        adj.setAdjustmentType(newAdjustmentType);
        adj.setMedicineStockType(newType);
        adj.setNote(req.getNote().trim());
        if (req.getAdjustmentDate() != null) {
            adj.setAdjustedAt(req.getAdjustmentDate().atStartOfDay());
        }

        return toAdjResponse(medicineStockAdjustmentRepository.save(adj));
    }

    private MedicineStock.MedicineStockType resolveBucketType(String rawType, MedicineStock.MedicineStockType fallback) {
        if (rawType == null) return fallback;
        try {
            return MedicineStock.MedicineStockType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private MedicineStockAdjustmentResponse toAdjResponse(MedicineStockAdjustment a) {
        return MedicineStockAdjustmentResponse.builder()
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
                .adjustedAtDate(a.getAdjustedAt() != null ? a.getAdjustedAt().toLocalDate().toString() : null)
                .adjustedByUsername(a.getAdjustedBy() != null ? a.getAdjustedBy().getUsername() : null)
                .inTransit(a.isInTransit())
                .transitDays(a.getTransitDays())
                .internalMovement(a.isInternalMovement())
                .medicineStockType(a.getMedicineStockType() != null ? a.getMedicineStockType().name() : "REGULAR_MEDICINE_STOCK")
                .build();
    }

    @Transactional(readOnly=true)
    public List<MedicineStockResponse> getAvailableForUser(Long userId) {
        // Return both REGULAR and ADMIN_STOCK so the user can choose which bucket to draw from
        List<MedicineStock> regular = medicineStockRepository
            .findAvailableByUserIdAndType(userId, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
        List<MedicineStock> adminStock = medicineStockRepository
            .findAvailableByUserIdAndType(userId, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);

        // Same forward-reconstruction ReportService uses for historical reports — so what's
        // shown as "available" can never disagree with what the daily report shows as on hand.
        Map<String, BigDecimal> settledByBucket = currentStockCalculator.settledQuantitiesForUser(userId);

        List<MedicineStockResponse> result = new java.util.ArrayList<>();
        regular.stream().map(i -> toDispatchableResponse(i, settledByBucket)).forEach(result::add);
        adminStock.stream().map(i -> toDispatchableResponse(i, settledByBucket)).forEach(result::add);
        return result;
    }

    private MedicineStockResponse toDispatchableResponse(MedicineStock i, Map<String, BigDecimal> settledByBucket) {
        MedicineStockResponse r = toResponse(i);
        String key = i.getMedicine().getId() + "|" + i.getMedicineStockType().name();
        r.setQuantity(settledByBucket.getOrDefault(key, BigDecimal.ZERO));
        return r;
    }

    @Transactional(readOnly=true)
    public List<MedicineStockResponse> getAll() {
        // Same forward-reconstruction used by getAvailableForUser()/reports — the admin "Modify
        // Medicine Stock" view must never show a quantity that disagrees with what can actually
        // be dispatched, which the cached MedicineStock.quantity field can silently drift from.
        Map<Long, Map<String, BigDecimal>> settledByUser = new HashMap<>();
        return medicineStockRepository.findAll().stream()
            .map(i -> toDispatchableResponse(i,
                settledByUser.computeIfAbsent(i.getUser().getId(), currentStockCalculator::settledQuantitiesForUser)))
            .toList();
    }

    private MedicineStockResponse toResponse(MedicineStock i) {
        MedicineStockResponse r = new MedicineStockResponse();
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
        r.setMedicineStockType(i.getMedicineStockType() != null ? i.getMedicineStockType().name() : "REGULAR_MEDICINE_STOCK");
        return r;
    }
}
