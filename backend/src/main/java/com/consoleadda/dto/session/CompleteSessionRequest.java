package com.consoleadda.dto.session;

import com.consoleadda.entity.PaymentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * @param endedAt the end time shown in the END preview, so the customer pays for what they reviewed even if
 *                completing the bill takes a minute. Defaults to now; may not be in the future or older than
 *                {@code session.max-end-backdate-minutes}.
 */
public record CompleteSessionRequest(
        Instant endedAt,
        @NotNull PaymentMode paymentMode,
        @Size(max = 100) String referenceNo
) {
}
