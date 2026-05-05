package com.pharma.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="inventory",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","medicine_id","inventory_type"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Inventory {

    public enum InventoryType {
        REGULAR_MEDICINE_STOCK, ADMIN_MEDICINE_STOCK
    }

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="medicine_id",nullable=false) private Medicine medicine;
    @Column(nullable=false) private Integer quantity;
    @Column(length=500) private String lastNote;
    @Column(nullable=false) private LocalDateTime lastUpdated;

    @Convert(converter = InventoryTypeConverter.class)
    @Column(name="inventory_type", nullable=false, columnDefinition="VARCHAR(30) DEFAULT 'REGULAR_MEDICINE_STOCK'")
    @Builder.Default
    private InventoryType inventoryType = InventoryType.REGULAR_MEDICINE_STOCK;

    @PrePersist @PreUpdate protected void onUpdate(){ lastUpdated=LocalDateTime.now(); }
}
