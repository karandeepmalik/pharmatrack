package com.pharma.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="medicines") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Medicine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=100) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private MedicineType type;
    @Column(nullable=false) private Double specification;
    @Column(nullable=true)  private Double concentrationMgPerMl;
    @Column(nullable=false) private Integer price;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pharma_company_id",nullable=false) private PharmaCompany pharmaCompany;
    @Column(nullable=false) private boolean active=true;
    public enum MedicineType { VIAL, TABLET, CAPSULE, SYRUP }
}
