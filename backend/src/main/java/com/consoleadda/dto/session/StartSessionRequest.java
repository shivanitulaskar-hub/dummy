package com.consoleadda.dto.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Customer fields are optional: a blank mobile is a walk-in. The pricing plan is auto-selected if omitted. */
public record StartSessionRequest(
        @NotNull Long stationId,
        @NotNull @Min(1) @Max(50) Integer playerCount,
        Long pricingPlanId,
        @Size(max = 100) String customerName,
        @Size(max = 20) String customerMobile,
        @Size(max = 500) String notes
) {
}
