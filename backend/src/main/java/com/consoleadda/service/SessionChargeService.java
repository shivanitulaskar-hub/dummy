package com.consoleadda.service;

import com.consoleadda.dto.session.ChargeBreakdown;
import com.consoleadda.dto.session.ChargeLine;
import com.consoleadda.entity.Bill;
import com.consoleadda.entity.BillItem;
import com.consoleadda.entity.BillItemType;
import com.consoleadda.entity.Discount;
import com.consoleadda.entity.GamingSession;
import com.consoleadda.mapper.PricingMapper;
import com.consoleadda.service.billing.BillCalculator;
import com.consoleadda.service.billing.BillableTimeCalculator;
import com.consoleadda.service.billing.GamingCharge;
import com.consoleadda.service.billing.GamingPriceCalculator;
import com.consoleadda.service.billing.TaxCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter between persisted sessions and the pure billing engine: loads plan, tax and rounding settings,
 * then delegates every calculation to {@link BillableTimeCalculator}, {@link GamingPriceCalculator} and
 * {@link BillCalculator}.
 */
@Service
public class SessionChargeService {

    private final PricingMapper pricingMapper;
    private final BillTotalsService totalsService;

    public SessionChargeService(PricingMapper pricingMapper, BillTotalsService totalsService) {
        this.pricingMapper = pricingMapper;
        this.totalsService = totalsService;
    }

    public record Computed(int actualMinutes, int creditMinutes, int billableMinutes, GamingCharge gaming,
                           String gamingDescription, BillCalculator.Result totals, ChargeBreakdown breakdown) {
    }

    @Transactional(readOnly = true)
    public Computed compute(GamingSession session, Instant endedAt) {
        return compute(session, endedAt, null);
    }

    @Transactional(readOnly = true)
    public Computed compute(GamingSession session, Instant endedAt, Discount discount) {
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
        chargeLines.add(new ChargeLine(null, BillItemType.GAMING.name(), null, description, 1, gaming.total(),
                gaming.total()));
        for (BillItem item : session.getBill().getItems()) {
            if (item.getItemType() == BillItemType.GAMING) {
                continue; // recalculated above
            }
            lines.add(new BillCalculator.Line(item.getTaxCategory(), item.getDescription(), item.getQuantity(),
                    item.getUnitPrice()));
            chargeLines.add(toLine(item));
        }
        BillCalculator.Result totals = totalsService.calculate(lines, discount);
        ChargeBreakdown breakdown = new ChargeBreakdown(endedAt, actual, credit, billable, gaming.total(),
                gaming.explanation(), totals.foodAmount(), totals.subtotal(),
                discount == null ? null : discount.getName(), totals.discountAmount(), totals.taxAmount(),
                totals.roundOff(), totals.totalAmount(), chargeLines);
        return new Computed(actual, credit, billable, gaming, description, totals, breakdown);
    }

    /** Breakdown of an already-paid bill, read from stored values (never recalculated). */
    public ChargeBreakdown stored(GamingSession session) {
        Bill bill = session.getBill();
        List<ChargeLine> lines = bill.getItems().stream().map(SessionChargeService::toLine).toList();
        return new ChargeBreakdown(session.getEndedAt(), nz(session.getActualMinutes()), session.getCreditMinutes(),
                nz(session.getBillableMinutes()), session.getGamingAmount(), null, bill.getFoodAmount(),
                bill.getSubtotal(), bill.getDiscountReason(), bill.getDiscountAmount(), bill.getTaxAmount(),
                bill.getRoundOff(), bill.getTotalAmount(), lines);
    }

    static ChargeLine toLine(BillItem i) {
        return new ChargeLine(i.getId(), i.getItemType().name(), i.getFoodItemId(), i.getDescription(), i.getQuantity(),
                i.getUnitPrice(), i.getLineTotal());
    }

    private static String gamingDescription(GamingSession session, int billableMinutes) {
        int players = session.getPlayerCount();
        return String.format("%s gaming · %s · %d player%s", session.getStation().getCode(),
                GamingPriceCalculator.formatMinutes(billableMinutes), players, players == 1 ? "" : "s");
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
