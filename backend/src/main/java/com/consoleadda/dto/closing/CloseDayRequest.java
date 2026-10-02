package com.consoleadda.dto.closing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CloseDayRequest(
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal openingCash,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal actualCash,
        @Size(max = 500) String notes
) {
}
