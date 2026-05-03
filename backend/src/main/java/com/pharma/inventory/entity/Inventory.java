package com.pharma.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="inventory",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","medicine_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Inventory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="medicine_id",nullable=false) private Medicine medicine;
    @Column(nullable=false) private Integer quantity;
    @Column(length=500) private String lastNote;
    @Column(nullable=false) private LocalDateTime lastUpdated;
    @PrePersist @PreUpdate protected void onUpdate(){ lastUpdated=LocalDateTime.now(); }
}
