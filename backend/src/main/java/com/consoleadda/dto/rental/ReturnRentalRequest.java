package com.consoleadda.dto.rental;

import com.consoleadda.entity.PaymentMode;
import jakarta.validation.constraints.Size;

/** {@code paymentMode} is required only when a late fee is due. */
public record ReturnRentalRequest(PaymentMode paymentMode, @Size(max = 100) String referenceNo,
                                  @Size(max = 500) String notes) {
}
