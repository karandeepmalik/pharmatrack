package com.pharma.inventory.dto;
public class AuthResponse {
    private String username; private String fullName; private String role;
    public AuthResponse(String username,String fullName,String role){
        this.username=username; this.fullName=fullName; this.role=role;}
    public String getUsername(){return username;}
    public String getFullName(){return fullName;} public String getRole(){return role;}
}
