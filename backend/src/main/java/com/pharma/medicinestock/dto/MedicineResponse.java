package com.pharma.medicinestock.dto;

public class MedicineResponse {
    private Long id;
    private String name;
    private String type;
    private Double specification;
    private Double concentrationMgPerMl;
    private Integer price;
    private PharmaRef pharmaCompany;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getType() { return type; } public void setType(String type) { this.type = type; }
    public Double getSpecification() { return specification; } public void setSpecification(Double specification) { this.specification = specification; }
    public Double getConcentrationMgPerMl() { return concentrationMgPerMl; } public void setConcentrationMgPerMl(Double concentrationMgPerMl) { this.concentrationMgPerMl = concentrationMgPerMl; }
    public Integer getPrice() { return price; } public void setPrice(Integer price) { this.price = price; }
    public PharmaRef getPharmaCompany() { return pharmaCompany; } public void setPharmaCompany(PharmaRef pharmaCompany) { this.pharmaCompany = pharmaCompany; }

    public static class PharmaRef {
        private Long id;
        private String name;
        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public String getName() { return name; } public void setName(String name) { this.name = name; }
    }
}
