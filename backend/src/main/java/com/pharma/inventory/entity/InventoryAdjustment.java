package com.pharma.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="inventory_adjustments", indexes = {
    @Index(name = "idx_adj_adjusted_at",  columnList = "adjusted_at"),
    @Index(name = "idx_adj_user_id",      columnList = "user_id"),
    @Index(name = "idx_adj_medicine_id",  columnList = "medicine_id"),
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryAdjustment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="medicine_id",nullable=false) private Medicine medicine;
    @Column(nullable=false) private Integer quantity;
    @Column(nullable=false) private String adjustmentType;
    @Column(length=500) private String note;
    @Column(nullable=false) private boolean internalMovement;
    @Column(nullable=false, columnDefinition="boolean default false") private boolean inTransit;
    @Column(nullable=false, columnDefinition="boolean default false") private boolean wasInTransit;
    @Builder.Default @Column(nullable=false, columnDefinition="integer default 2") private int transitDays = 2;
    @Convert(converter = InventoryTypeConverter.class) @Column(name="inventory_type",nullable=false)
    private Inventory.InventoryType inventoryType;
    @Column(nullable=false) private LocalDateTime adjustedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="adjusted_by_id") private User adjustedBy;
}
