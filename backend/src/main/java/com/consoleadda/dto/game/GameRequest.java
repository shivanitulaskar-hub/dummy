package com.consoleadda.dto.game;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GameRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull @Min(1) @Max(999) Integer copyNumber,
        @Size(max = 20) String platform,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal purchasePrice,
        LocalDate purchaseDate,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal rentalPrice,
        @NotNull @Min(1) @Max(365) Integer rentalPeriodDays,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal lateFeePerDay,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal defaultDeposit,
        @NotNull Boolean availableForRental,
        @NotNull Boolean active,
        @Size(max = 500) String notes
) {
}
