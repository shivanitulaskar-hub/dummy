package com.consoleadda.service;

import com.consoleadda.dto.session.ChargeBreakdown;
import com.consoleadda.dto.session.ChargeLine;
import com.consoleadda.entity.Bill;
import com.consoleadda.entity.BillItem;
import com.consoleadda.entity.BillItemType;
import com.consoleadda.entity.GamingSession;
import com.consoleadda.entity.TaxSetting;
import com.consoleadda.mapper.PricingMapper;
import com.consoleadda.repository.TaxSettingRepository;
import com.consoleadda.service.billing.BillCalculator;
import com.consoleadda.service.billing.BillableTimeCalculator;
import com.consoleadda.service.billing.GamingCharge;
import com.consoleadda.service.billing.GamingPriceCalculator;
import com.consoleadda.service.billing.TaxCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter between persisted sessions and the pure billing engine: loads plan, tax and rounding settings,
 * then delegates every calculation to {@link BillableTimeCalculator}, {@link GamingPriceCalculator} and
 * {@link BillCalculator}.
 */
@Service
public class SessionChargeService {

    private final PricingMapper pricingMapper;
    private final TaxSettingRepository taxSettingRepository;
    private final SettingsService settings;

    public SessionChargeService(PricingMapper pricingMapper, TaxSettingRepository taxSettingRepository,
                                SettingsService settings) {
        this.pricingMapper = pricingMapper;
        this.taxSettingRepository = taxSettingRepository;
        this.settings = settings;
    }

    public record Computed(int actualMinutes, int creditMinutes, int billableMinutes, GamingCharge gaming,
                           String gamingDescription, BillCalculator.Result totals, ChargeBreakdown breakdown) {
    }

    @Transactional(readOnly = true)
    public Computed compute(GamingSession session, Instant endedAt) {
        int actual = BillableTimeCalculator.actualMinutes(session.getStartedAt(), endedAt);
        int credit = session.getCreditMinutes();
        int net = BillableTimeCalculator.netMinutes(actual, credit);
        int billable = BillableTimeCalculator.billableMinutes(net, pricingMapper.toTimeRules(session.getPricingPlan()));
        GamingCharge gaming = GamingPriceCalculator.price(billable, session.getPlayerCount(),
                pricingMapper.toPlanPricing(session.getPricingPlan()));
        String description = gamingDescription(session, billable);

        List<BillCalculator.Line> lines = new ArrayList<>();
        List<ChargeLine> chargeLines = new ArrayList<>();
        lines.add(new BillCalculator.Line(TaxCategory.GAMING, description, 1, gaming.total()));
        chargeLines.add(new ChargeLine(BillItemType.GAMING.name(), description, 1, gaming.total(), gaming.total()));
        for (BillItem item : session.getBill().getItems()) {
            if (item.getItemType() == BillItemType.GAMING) {
                continue; // recalculated above
            }
            lines.add(new BillCalculator.Line(item.getTaxCategory(), item.getDescription(), item.getQuantity(),
                    item.getUnitPrice()));
            chargeLines.add(new ChargeLine(item.getItemType().name(), item.getDescription(), item.getQuantity(),
                    item.getUnitPrice(), item.getLineTotal()));
        }
        BillCalculator.Result totals = BillCalculator.calculate(lines, null, taxRules(),
                settings.getBoolean(SettingKeys.ROUND_TO_RUPEE, true));
        ChargeBreakdown breakdown = new ChargeBreakdown(endedAt, actual, credit, billable, gaming.total(),
                gaming.explanation(), totals.foodAmount(), totals.subtotal(), totals.discountAmount(),
                totals.taxAmount(), totals.roundOff(), totals.totalAmount(), chargeLines);
        return new Computed(actual, credit, billable, gaming, description, totals, breakdown);
    }

    /** Breakdown of an already-paid bill, read from stored values (never recalculated). */
    public ChargeBreakdown stored(GamingSession session) {
        Bill bill = session.getBill();
        List<ChargeLine> lines = bill.getItems().stream()
                .map(i -> new ChargeLine(i.getItemType().name(), i.getDescription(), i.getQuantity(), i.getUnitPrice(),
                        i.getLineTotal()))
                .toList();
        return new ChargeBreakdown(session.getEndedAt(), nz(session.getActualMinutes()), session.getCreditMinutes(),
                nz(session.getBillableMinutes()), session.getGamingAmount(), null, bill.getFoodAmount(),
                bill.getSubtotal(), bill.getDiscountAmount(), bill.getTaxAmount(), bill.getRoundOff(),
                bill.getTotalAmount(), lines);
    }

    private Map<TaxCategory, BillCalculator.TaxRule> taxRules() {
        Map<TaxCategory, BillCalculator.TaxRule> rules = new EnumMap<>(TaxCategory.class);
        for (TaxSetting t : taxSettingRepository.findAll()) {
            rules.put(t.getCategory(), new BillCalculator.TaxRule(t.isEnabled(), t.getRatePercent(), t.getPriceMode()));
        }
        return rules;
    }

    private static String gamingDescription(GamingSession session, int billableMinutes) {
        int players = session.getPlayerCount();
        return String.format("%s gaming · %s · %d player%s", session.getStation().getCode(),
                GamingPriceCalculator.formatMinutes(billableMinutes), players, players == 1 ? "" : "s");
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
