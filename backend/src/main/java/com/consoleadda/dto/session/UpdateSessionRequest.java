package com.consoleadda.dto.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Partial update of an active session; null fields are left unchanged. */
public record UpdateSessionRequest(
        @Min(1) @Max(50) Integer playerCount,
        @Size(max = 100) String customerName,
        @Size(max = 20) String customerMobile,
        @Size(max = 500) String notes
) {
}
