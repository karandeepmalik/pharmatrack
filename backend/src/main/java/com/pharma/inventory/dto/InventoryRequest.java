package com.pharma.inventory.dto;
import jakarta.validation.constraints.*;
public class InventoryRequest {
    @NotNull private Long userId;
    @NotNull private Long medicineId;
    @NotNull @Min(1) private Integer quantity;
    public Long getUserId(){return userId;} public void setUserId(Long u){this.userId=u;}
    public Long getMedicineId(){return medicineId;} public void setMedicineId(Long m){this.medicineId=m;}
    public Integer getQuantity(){return quantity;} public void setQuantity(Integer q){this.quantity=q;}
}
