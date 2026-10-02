package com.consoleadda.service.billing;

/** How the additional-player charge scales with time. */
public enum AdditionalPlayerBasis {
    /** Charged once per extra player, regardless of duration. */
    PER_SESSION,
    /** Charged per extra player per billable hour, pro-rated by billable minutes. */
    PER_HOUR
}
