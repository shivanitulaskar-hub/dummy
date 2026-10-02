package com.consoleadda.dto.bill;

import com.consoleadda.dto.session.SessionItemRequest;
import com.consoleadda.entity.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Counter sale without a gaming session (food/drinks only). Created and paid in one step. */
public record WalkInBillRequest(
        @Size(max = 100) String customerName,
        @Size(max = 20) String customerMobile,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull SessionItemRequest> items,
        Long discountId,
        @NotNull PaymentMode paymentMode,
        @Size(max = 100) String referenceNo
) {
}
