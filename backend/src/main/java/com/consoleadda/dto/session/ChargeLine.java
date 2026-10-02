package com.consoleadda.dto.session;

import java.math.BigDecimal;

public record ChargeLine(String type, String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {
}
