package com.pharma.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transaction_screenshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionScreenshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String data;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
