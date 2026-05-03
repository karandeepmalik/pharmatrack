package com.pharma.inventory.dto;
public class InventoryResponse {
    private Long id,userId,medicineId,pharmaId;
    private String username,medicineName,medicineType,pharmaName,specUnit;
    private Double specification;
    private Integer quantity;
    private Integer price;
    public Long getId(){return id;} public void setId(Long i){this.id=i;}
    public Long getUserId(){return userId;} public void setUserId(Long u){this.userId=u;}
    public String getUsername(){return username;} public void setUsername(String u){this.username=u;}
    public Long getMedicineId(){return medicineId;} public void setMedicineId(Long m){this.medicineId=m;}
    public String getMedicineName(){return medicineName;} public void setMedicineName(String n){this.medicineName=n;}
    public String getMedicineType(){return medicineType;} public void setMedicineType(String t){this.medicineType=t;}
    public Double getSpecification(){return specification;} public void setSpecification(Double s){this.specification=s;}
    public String getSpecUnit(){return specUnit;} public void setSpecUnit(String s){this.specUnit=s;}
    public Long getPharmaId(){return pharmaId;} public void setPharmaId(Long p){this.pharmaId=p;}
    public String getPharmaName(){return pharmaName;} public void setPharmaName(String p){this.pharmaName=p;}
    public Integer getQuantity(){return quantity;} public void setQuantity(Integer q){this.quantity=q;}
    public Integer getPrice(){return price;} public void setPrice(Integer p){this.price=p;}
}
