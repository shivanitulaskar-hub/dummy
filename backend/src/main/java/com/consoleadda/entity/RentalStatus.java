package com.consoleadda.entity;

/**
 * Stored rental states. OVERDUE is never stored by the application: a rental is reported as overdue while it
 * is OUT past its expected return time plus the grace period (see {@code RentalService}).
 */
public enum RentalStatus {
    OUT,
    RETURNED,
    OVERDUE,
    CANCELLED
}
