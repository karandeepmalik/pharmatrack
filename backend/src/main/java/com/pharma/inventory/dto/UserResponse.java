package com.pharma.inventory.dto;
public class UserResponse {
    private Long id;
    private String username,fullName,email,role;
    private boolean active;
    public Long getId(){return id;} public void setId(Long i){this.id=i;}
    public String getUsername(){return username;} public void setUsername(String u){this.username=u;}
    public String getFullName(){return fullName;} public void setFullName(String f){this.fullName=f;}
    public String getEmail(){return email;} public void setEmail(String e){this.email=e;}
    public String getRole(){return role;} public void setRole(String r){this.role=r;}
    public boolean isActive(){return active;} public void setActive(boolean a){this.active=a;}
}
