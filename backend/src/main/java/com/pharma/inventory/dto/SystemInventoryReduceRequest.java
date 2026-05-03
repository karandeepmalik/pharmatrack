package com.pharma.inventory.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class SystemInventoryReduceRequest {
    @NotNull @Min(1) private Integer quantity;
}
