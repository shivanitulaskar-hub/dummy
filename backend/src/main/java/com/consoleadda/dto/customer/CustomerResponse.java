package com.consoleadda.dto.customer;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerResponse(Long id, String name, String mobile, String email, String notes, int totalSessions,
                               int totalGamingMinutes, BigDecimal totalSpent, Instant lastVisitAt, Instant createdAt) {
}
