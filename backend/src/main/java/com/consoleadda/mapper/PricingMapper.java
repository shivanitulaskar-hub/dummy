package com.consoleadda.mapper;

import com.consoleadda.dto.pricing.PricingPlanResponse;
import com.consoleadda.dto.pricing.PricingRuleDto;
import com.consoleadda.entity.PricingPlan;
import com.consoleadda.entity.PricingRule;
import com.consoleadda.service.billing.BillingTimeRules;
import com.consoleadda.service.billing.PlanPricing;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Component
public class PricingMapper {

    public PricingPlanResponse toResponse(PricingPlan plan, boolean inUse) {
        return new PricingPlanResponse(plan.getId(), plan.getName(), plan.getDayType(), plan.getActiveFrom(),
                plan.getActiveTo(), plan.isActive(), plan.getPriority(), plan.getMinBillableMinutes(),
                plan.getBillingBlockMinutes(), plan.getGraceMinutes(), plan.isGraceEveryBoundary(),
                plan.getExtensionBlockMinutes(), plan.getExtensionPrice(), plan.getAdditionalPlayerPrice(),
                plan.getAdditionalPlayerBasis(), plan.getMaxPlayers(), plan.getNotes(),
                plan.getRules().stream().map(this::toDto).toList(), inUse, plan.getUpdatedAt());
    }

    public PricingRuleDto toDto(PricingRule rule) {
        return new PricingRuleDto(rule.getPlayerCount(), rule.getDurationMinutes(), rule.getPrice());
    }

    /** Converts a plan entity into the plain values used by the pure billing engine. */
    public PlanPricing toPlanPricing(PricingPlan plan) {
        Map<Integer, NavigableMap<Integer, BigDecimal>> slabs = new HashMap<>();
        for (PricingRule rule : plan.getRules()) {
            slabs.computeIfAbsent(rule.getPlayerCount(), k -> new TreeMap<>())
                    .put(rule.getDurationMinutes(), rule.getPrice());
        }
        return new PlanPricing(slabs, plan.getExtensionBlockMinutes(), plan.getExtensionPrice(),
                plan.getAdditionalPlayerPrice(), plan.getAdditionalPlayerBasis(), plan.getMaxPlayers());
    }

    public BillingTimeRules toTimeRules(PricingPlan plan) {
        return new BillingTimeRules(plan.getMinBillableMinutes(), plan.getBillingBlockMinutes(),
                plan.getGraceMinutes(), plan.isGraceEveryBoundary());
    }
}
