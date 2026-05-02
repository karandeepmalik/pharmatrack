package com.pharma.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="pharma_companies") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PharmaCompany {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=100) private String name;
    @Column(length=500) private String description;
    @Column(nullable=false) private boolean active=true;
}
