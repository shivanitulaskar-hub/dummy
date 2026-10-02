package com.consoleadda.dto.discount;

import com.consoleadda.service.billing.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscountResponse(Long id, String code, String name, DiscountType discountType, BigDecimal value,
                               BigDecimal maxAmount, LocalDate validFrom, LocalDate validTo, boolean active,
                               boolean validToday) {
}
