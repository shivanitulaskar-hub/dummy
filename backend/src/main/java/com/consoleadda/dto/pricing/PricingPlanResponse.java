package com.consoleadda.dto.pricing;

import com.consoleadda.entity.DayType;
import com.consoleadda.service.billing.AdditionalPlayerBasis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PricingPlanResponse(
        Long id,
        String name,
        DayType dayType,
        LocalDate activeFrom,
        LocalDate activeTo,
        boolean active,
        int priority,
        int minBillableMinutes,
        int billingBlockMinutes,
        int graceMinutes,
        boolean graceEveryBoundary,
        int extensionBlockMinutes,
        BigDecimal extensionPrice,
        BigDecimal additionalPlayerPrice,
        AdditionalPlayerBasis additionalPlayerBasis,
        int maxPlayers,
        String notes,
        List<PricingRuleDto> rules,
        /** True while an ACTIVE session uses this plan (edits are blocked then). */
        boolean inUseByActiveSession,
        Instant updatedAt
) {
}
