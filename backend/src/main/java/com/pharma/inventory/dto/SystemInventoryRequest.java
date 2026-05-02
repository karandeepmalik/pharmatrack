package com.pharma.inventory.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class SystemInventoryRequest {
    @NotNull private Long medicineId;
    @NotNull @Min(1) private Integer quantity;
}
