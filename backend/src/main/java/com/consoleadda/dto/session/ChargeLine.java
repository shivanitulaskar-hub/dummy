package com.consoleadda.dto.session;

import java.math.BigDecimal;

/** {@code id} is the bill line id (null for the computed gaming line), used to change or remove food lines. */
public record ChargeLine(Long id, String type, Long foodItemId, String description, int quantity, BigDecimal unitPrice,
                        BigDecimal amount) {
}
