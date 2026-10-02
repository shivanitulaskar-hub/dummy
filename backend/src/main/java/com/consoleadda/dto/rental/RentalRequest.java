package com.consoleadda.dto.rental;

import com.consoleadda.entity.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Rent a disc out. Days and deposit default to the game's settings when omitted. */
public record RentalRequest(
        @NotNull Long gameId,
        @NotBlank @Size(max = 100) String customerName,
        @NotBlank @Size(max = 20) String customerMobile,
        @Min(1) @Max(365) Integer rentalDays,
        @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal depositAmount,
        @NotNull PaymentMode paymentMode,
        @Size(max = 100) String referenceNo,
        @Size(max = 500) String notes
) {
}
