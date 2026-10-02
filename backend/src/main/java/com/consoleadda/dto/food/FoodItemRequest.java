package com.consoleadda.dto.food;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FoodItemRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Long categoryId,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal sellingPrice,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal costPrice,
        @NotNull @Min(0) @Max(100000) Integer reorderLevel,
        @NotNull Boolean trackInventory,
        @NotNull @Min(0) @Max(240) Integer gamingCreditMinutes,
        @NotNull Boolean active
) {
}
