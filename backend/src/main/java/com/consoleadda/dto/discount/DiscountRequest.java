package com.consoleadda.dto.discount;

import com.consoleadda.service.billing.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscountRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$", message = "2-30 letters, digits, - or _") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2)
        BigDecimal value,
        @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2)
        BigDecimal maxAmount,
        LocalDate validFrom,
        LocalDate validTo,
        @NotNull Boolean active
) {
}
