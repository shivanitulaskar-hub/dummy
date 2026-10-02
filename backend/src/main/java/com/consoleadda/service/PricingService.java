package com.consoleadda.service;

import com.consoleadda.dto.pricing.PriceQuoteResponse;
import com.consoleadda.dto.pricing.PricingPlanRequest;
import com.consoleadda.dto.pricing.PricingPlanResponse;
import com.consoleadda.dto.pricing.PricingRuleDto;
import com.consoleadda.entity.DayType;
import com.consoleadda.entity.PricingPlan;
import com.consoleadda.entity.PricingRule;
import com.consoleadda.entity.SessionStatus;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.mapper.PricingMapper;
import com.consoleadda.repository.GamingSessionRepository;
import com.consoleadda.repository.PricingPlanRepository;
import com.consoleadda.service.billing.BillableTimeCalculator;
import com.consoleadda.service.billing.GamingCharge;
import com.consoleadda.service.billing.GamingPriceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pricing master: rate cards are data, never code. Also resolves which plan applies on a date.
 */
@Service
public class PricingService {

    private final PricingPlanRepository planRepository;
    private final GamingSessionRepository sessionRepository;
    private final PricingMapper mapper;
    private final AuditService auditService;
    private final BusinessDayService businessDay;
    private final Clock clock;

    public PricingService(PricingPlanRepository planRepository, GamingSessionRepository sessionRepository,
                          PricingMapper mapper, AuditService auditService, BusinessDayService businessDay, Clock clock) {
        this.clock = clock;
        this.planRepository = planRepository;
        this.sessionRepository = sessionRepository;
        this.mapper = mapper;
        this.auditService = auditService;
        this.businessDay = businessDay;
    }

    @Transactional(readOnly = true)
    public List<PricingPlanResponse> list() {
        return planRepository.findAllByOrderByActiveDescPriorityDescNameAsc().stream()
                .map(p -> mapper.toResponse(p, inUse(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public PricingPlanResponse get(Long id) {
        PricingPlan plan = load(id);
        return mapper.toResponse(plan, inUse(id));
    }

    /** The plan a new session started now would use. */
    @Transactional(readOnly = true)
    public PricingPlanResponse current() {
        PricingPlan plan = resolve(businessDay.localDate(clock.instant()), null);
        return mapper.toResponse(plan, inUse(plan.getId()));
    }

    @Transactional
    public PricingPlanResponse create(PricingPlanRequest request) {
        validate(request);
        if (planRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw BusinessException.conflict("DUPLICATE_NAME", "A pricing plan named '" + request.name() + "' already exists");
        }
        PricingPlan plan = new PricingPlan();
        apply(plan, request);
        planRepository.saveAndFlush(plan);
        auditService.record("PRICING_CREATED", "PricingPlan", plan.getId(), null, request);
        return mapper.toResponse(plan, false);
    }

    @Transactional
    public PricingPlanResponse update(Long id, PricingPlanRequest request) {
        validate(request);
        PricingPlan plan = load(id);
        if (planRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw BusinessException.conflict("DUPLICATE_NAME", "A pricing plan named '" + request.name() + "' already exists");
        }
        // Running sessions are billed by their plan at END; changing it now would silently re-price them.
        if (inUse(id)) {
            throw BusinessException.conflict("PLAN_IN_USE", "This plan is used by an active session. Wait until those "
                    + "sessions end, or create a new plan with the new prices.");
        }
        PricingPlanResponse before = mapper.toResponse(plan, false);
        plan.getRules().clear();
        planRepository.flush(); // delete old slabs before inserting replacements (unique key)
        apply(plan, request);
        planRepository.saveAndFlush(plan);
        auditService.record("PRICING_CHANGED", "PricingPlan", id, before, request);
        return mapper.toResponse(plan, false);
    }

    @Transactional
    public void delete(Long id) {
        PricingPlan plan = load(id);
        if (sessionRepository.existsByPricingPlanId(id)) {
            throw BusinessException.conflict("PLAN_HAS_HISTORY",
                    "This plan has been used for sessions. Deactivate it instead of deleting it.");
        }
        PricingPlanResponse before = mapper.toResponse(plan, false);
        planRepository.delete(plan);
        auditService.record("PRICING_DELETED", "PricingPlan", id, before, null);
    }

    @Transactional(readOnly = true)
    public PriceQuoteResponse quote(Long planId, int playedMinutes, int creditMinutes, int playerCount) {
        if (playedMinutes < 0 || creditMinutes < 0) {
            throw BusinessException.badRequest("INVALID_MINUTES", "Minutes cannot be negative");
        }
        PricingPlan plan = planId != null ? load(planId) : resolve(businessDay.localDate(clock.instant()), null);
        validatePlayers(plan, playerCount);
        int net = BillableTimeCalculator.netMinutes(playedMinutes, creditMinutes);
        int billable = BillableTimeCalculator.billableMinutes(net, mapper.toTimeRules(plan));
        GamingCharge charge = GamingPriceCalculator.price(billable, playerCount, mapper.toPlanPricing(plan));
        return new PriceQuoteResponse(plan.getId(), plan.getName(), playedMinutes, creditMinutes, billable, playerCount,
                charge.baseAmount(), charge.additionalPlayerAmount(), charge.total(), charge.explanation());
    }

    /**
     * Picks the plan for a session starting on {@code date}. An explicitly requested plan must be valid that day.
     * Otherwise: an active SPECIAL plan covering the date wins, then WEEKEND (Sat/Sun), then WEEKDAY;
     * ties go to the highest priority, then the most recent start date.
     */
    @Transactional(readOnly = true)
    public PricingPlan resolve(LocalDate date, Long requestedPlanId) {
        if (requestedPlanId != null) {
            PricingPlan plan = load(requestedPlanId);
            if (!plan.isValidOn(date)) {
                throw BusinessException.badRequest("PLAN_NOT_VALID", "Pricing plan '" + plan.getName()
                        + "' is not active on " + date);
            }
            return plan;
        }
        List<PricingPlan> valid = planRepository.findByActiveTrue().stream().filter(p -> p.isValidOn(date)).toList();
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        List<DayType> preference = weekend
                ? List.of(DayType.SPECIAL, DayType.WEEKEND, DayType.WEEKDAY)
                : List.of(DayType.SPECIAL, DayType.WEEKDAY);
        Comparator<PricingPlan> best = Comparator.comparingInt(PricingPlan::getPriority)
                .thenComparing(PricingPlan::getActiveFrom).reversed();
        for (DayType type : preference) {
            Optional<PricingPlan> match = valid.stream().filter(p -> p.getDayType() == type).min(best);
            if (match.isPresent()) {
                return match.get();
            }
        }
        throw BusinessException.conflict("NO_PRICING_PLAN",
                "No active pricing plan covers " + date + ". An admin must configure pricing.");
    }

    public void validatePlayers(PricingPlan plan, int playerCount) {
        if (playerCount < 1 || playerCount > plan.getMaxPlayers()) {
            throw BusinessException.badRequest("INVALID_PLAYER_COUNT",
                    "Number of players must be between 1 and " + plan.getMaxPlayers() + " for plan '" + plan.getName() + "'");
        }
    }

    private boolean inUse(Long planId) {
        return sessionRepository.existsByPricingPlanIdAndStatus(planId, SessionStatus.ACTIVE);
    }

    private PricingPlan load(Long id) {
        return planRepository.findWithRulesById(id).orElseThrow(() -> new NotFoundException("Pricing plan", id));
    }

    private void validate(PricingPlanRequest r) {
        if (r.activeTo() != null && r.activeTo().isBefore(r.activeFrom())) {
            throw BusinessException.badRequest("INVALID_DATES", "'Active to' must be on or after 'active from'");
        }
        if (r.graceMinutes() >= r.billingBlockMinutes()) {
            throw BusinessException.badRequest("INVALID_GRACE", "Grace minutes must be smaller than the billing block");
        }
        Set<String> seen = new HashSet<>();
        boolean hasSinglePlayer = false;
        for (PricingRuleDto rule : r.rules()) {
            if (!seen.add(rule.playerCount() + "/" + rule.durationMinutes())) {
                throw BusinessException.badRequest("DUPLICATE_SLAB", "Duplicate slab for " + rule.playerCount()
                        + " player(s) × " + rule.durationMinutes() + " minutes");
            }
            if (rule.playerCount() > r.maxPlayers()) {
                throw BusinessException.badRequest("INVALID_SLAB", "Slab for " + rule.playerCount()
                        + " players exceeds max players (" + r.maxPlayers() + ")");
            }
            hasSinglePlayer |= rule.playerCount() == 1;
        }
        if (!hasSinglePlayer) {
            throw BusinessException.badRequest("MISSING_BASE_SLAB", "At least one 1-player slab is required");
        }
    }

    private void apply(PricingPlan plan, PricingPlanRequest r) {
        plan.setName(r.name().trim());
        plan.setDayType(r.dayType());
        plan.setActiveFrom(r.activeFrom());
        plan.setActiveTo(r.activeTo());
        plan.setActive(r.active());
        plan.setPriority(r.priority());
        plan.setMinBillableMinutes(r.minBillableMinutes());
        plan.setBillingBlockMinutes(r.billingBlockMinutes());
        plan.setGraceMinutes(r.graceMinutes());
        plan.setGraceEveryBoundary(r.graceEveryBoundary());
        plan.setExtensionBlockMinutes(r.extensionBlockMinutes());
        plan.setExtensionPrice(r.extensionPrice());
        plan.setAdditionalPlayerPrice(r.additionalPlayerPrice());
        plan.setAdditionalPlayerBasis(r.additionalPlayerBasis());
        plan.setMaxPlayers(r.maxPlayers());
        plan.setNotes(r.notes());
        for (PricingRuleDto dto : r.rules()) {
            PricingRule rule = new PricingRule();
            rule.setPricingPlan(plan);
            rule.setPlayerCount(dto.playerCount());
            rule.setDurationMinutes(dto.durationMinutes());
            rule.setPrice(dto.price());
            plan.getRules().add(rule);
        }
    }
}
