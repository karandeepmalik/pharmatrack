package com.pharma.inventory.dto;
import jakarta.validation.constraints.*;
public class RegisterRequest {
    @NotBlank @Size(min=3,max=50) private String username;
    @NotBlank @Email private String email;
    @NotBlank @Size(min=8) private String password;
    @NotBlank private String fullName;
    private String role="USER";
    public String getUsername(){return username;} public void setUsername(String u){this.username=u;}
    public String getEmail(){return email;} public void setEmail(String e){this.email=e;}
    public String getPassword(){return password;} public void setPassword(String p){this.password=p;}
    public String getFullName(){return fullName;} public void setFullName(String f){this.fullName=f;}
    public String getRole(){return role;} public void setRole(String r){this.role=r;}
}
