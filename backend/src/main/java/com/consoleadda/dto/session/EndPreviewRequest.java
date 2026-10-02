package com.consoleadda.dto.session;

import java.time.Instant;

/**
 * Optional body of END: preview with a discount, and/or recalculate at an earlier frozen end time (so changing
 * the discount in the dialog never moves the end time).
 */
public record EndPreviewRequest(Long discountId, Instant endedAt) {
}
