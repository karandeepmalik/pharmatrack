package com.pharma.inventory.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_tx_status",        columnList = "status"),
    @Index(name = "idx_tx_submitted_at",  columnList = "submitted_at"),
    @Index(name = "idx_tx_submitted_by",  columnList = "submitted_by"),
    @Index(name = "idx_tx_medicine_id",   columnList = "medicine_id"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @Column(nullable = false, columnDefinition = "NUMERIC(10,1)")
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    @Column(length = 500, nullable = false)
    private String notes;

    /**
     * Stores the payment screenshot as a Base64-encoded string.
     * Stored as TEXT/CLOB to avoid binary column complexity.
     * Max ~5 MB image → ~6.7 MB Base64 — fits in PostgreSQL TEXT.
     */
    @Column(name = "payment_screenshot", columnDefinition = "TEXT")
    private String paymentScreenshot;

    /** MIME type of the uploaded screenshot, e.g. "image/png" */
    @Column(name = "payment_screenshot_type", length = 50)
    private String paymentScreenshotType;

    /**
     * Optional price per unit submitted by the user at the time of transaction.
     * May be null if the user did not override the default medicine price.
     */
    @Column(name = "price_per_unit")
    private Integer pricePerUnit;

    /**
     * The inventory bucket this transaction was deducted from.
     * Null means REGULAR_MEDICINE_STOCK (backward compatible with existing records).
     */
    @Convert(converter = InventoryTypeConverter.class)
    @Column(name = "inventory_type", length = 30)
    private Inventory.InventoryType inventoryType;

    @Builder.Default
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 50)
    private List<TransactionScreenshot> screenshots = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) submittedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }

    public enum TransactionStatus {
        PENDING, APPROVED, REJECTED
    }
}
