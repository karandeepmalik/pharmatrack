package com.pharma.medicinestock.dto;

import jakarta.validation.constraints.NotBlank;

public class CreatePharmaCompanyRequest {
    @NotBlank(message = "Name is required") private String name;
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
