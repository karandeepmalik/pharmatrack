package com.pharma.medicinestock.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
@Entity @Table(name="medicine_stock",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","medicine_id","medicine_stock_type"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MedicineStock {

    public enum MedicineStockType {
        REGULAR_MEDICINE_STOCK, ADMIN_MEDICINE_STOCK
    }

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="medicine_id",nullable=false) private Medicine medicine;
    @Column(nullable=false, columnDefinition="NUMERIC(10,1)") private BigDecimal quantity;
    @Column(length=500) private String lastNote;
    @Column(nullable=false) private LocalDateTime lastUpdated;

    @Convert(converter = MedicineStockTypeConverter.class)
    @Column(name="medicine_stock_type", nullable=false, columnDefinition="VARCHAR(30) DEFAULT 'REGULAR_MEDICINE_STOCK'")
    @Builder.Default
    private MedicineStockType medicineStockType = MedicineStockType.REGULAR_MEDICINE_STOCK;

    @PrePersist @PreUpdate protected void onUpdate(){ lastUpdated=LocalDateTime.now(ZoneId.of("Asia/Kolkata")); }
}
