package com.pharma.inventory.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
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

    @Column(nullable = false)
    private Integer quantity;

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
     * The inventory type this transaction was deducted from (REGULAR or ADMIN_STOCK).
     * Null means REGULAR (backward compatible with existing records).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_type", length = 20)
    private Inventory.InventoryType inventoryType;

    @Builder.Default
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL,
               fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<TransactionScreenshot> screenshots = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
    }

    public enum TransactionStatus {
        PENDING, APPROVED, REJECTED
    }
}
