package com.pharma.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="inventory",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","medicine_id","inventory_type"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Inventory {

    public enum InventoryType {
        REGULAR, ADMIN_STOCK
    }

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="medicine_id",nullable=false) private Medicine medicine;
    @Column(nullable=false) private Integer quantity;
    @Column(length=500) private String lastNote;
    @Column(nullable=false) private LocalDateTime lastUpdated;

    @Enumerated(EnumType.STRING)
    @Column(name="inventory_type", nullable=false, columnDefinition="VARCHAR(20) DEFAULT 'REGULAR'")
    @Builder.Default
    private InventoryType inventoryType = InventoryType.REGULAR;

    @PrePersist @PreUpdate protected void onUpdate(){ lastUpdated=LocalDateTime.now(); }
}
