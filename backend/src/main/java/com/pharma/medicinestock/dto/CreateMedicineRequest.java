package com.pharma.medicinestock.dto;

import com.pharma.medicinestock.entity.Medicine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateMedicineRequest {
    @NotNull(message = "pharmaCompanyId is required") private Long pharmaCompanyId;
    @NotBlank(message = "name is required") private String name;
    @NotNull(message = "type is required") private Medicine.MedicineType type;
    @NotNull(message = "specification is required") private Double specification;
    private Double concentrationMgPerMl;
    @NotNull(message = "price is required") private Integer price;

    public Long getPharmaCompanyId() { return pharmaCompanyId; }
    public void setPharmaCompanyId(Long pharmaCompanyId) { this.pharmaCompanyId = pharmaCompanyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Medicine.MedicineType getType() { return type; }
    public void setType(Medicine.MedicineType type) { this.type = type; }
    public Double getSpecification() { return specification; }
    public void setSpecification(Double specification) { this.specification = specification; }
    public Double getConcentrationMgPerMl() { return concentrationMgPerMl; }
    public void setConcentrationMgPerMl(Double concentrationMgPerMl) { this.concentrationMgPerMl = concentrationMgPerMl; }
    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }
}
