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
        return medicineStockRepository.findAll().stream().map(this::toResponse).toList();
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
