package com.pharma.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="users") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=50) private String username;
    @Column(nullable=false) private String password;
    @Column(nullable=false,length=100) private String fullName;
    @Column(nullable=false,unique=true,length=100) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role;
    @Column(nullable=false) private boolean active=true;
    public enum Role { ADMIN, USER }
}
