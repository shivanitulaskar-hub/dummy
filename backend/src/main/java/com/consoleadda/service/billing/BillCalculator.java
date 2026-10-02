package com.consoleadda.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Computes bill totals from lines, an optional discount and per-category GST rules. Pure function.
 * <ol>
 *   <li>line total = quantity × unit price (2 dp, HALF_UP); item prices are never modified</li>
 *   <li>discount on the subtotal (percentage capped by max amount, never above the subtotal)</li>
 *   <li>discount allocated pro-rata to categories; the last category absorbs rounding so allocations sum exactly</li>
 *   <li>GST per category: exclusive = taxable × rate / 100; inclusive = taxable × rate / (100 + rate)</li>
 *   <li>grand total optionally rounded to the nearest rupee, the difference recorded as round-off</li>
 * </ol>
 */
public final class BillCalculator {

    private BillCalculator() {
    }

    public record Line(TaxCategory category, String description, int quantity, BigDecimal unitPrice) {
        public Line {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            if (unitPrice == null || unitPrice.signum() < 0) {
                throw new IllegalArgumentException("Unit price cannot be negative");
            }
        }

        public BigDecimal total() {
            return Money.round(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }
    }

    public record Discount(DiscountType type, BigDecimal value, BigDecimal maxAmount) {
        public Discount {
            if (value == null || value.signum() < 0) {
                throw new IllegalArgumentException("Discount value cannot be negative");
            }
            if (type == DiscountType.PERCENTAGE && value.compareTo(Money.HUNDRED) > 0) {
                throw new IllegalArgumentException("Percentage discount cannot exceed 100");
            }
        }
    }

    public record TaxRule(boolean enabled, BigDecimal ratePercent, PriceMode priceMode) {
        public static final TaxRule NONE = new TaxRule(false, BigDecimal.ZERO, PriceMode.EXCLUSIVE);
    }

    public record CategoryTotal(TaxCategory category, BigDecimal subtotal, BigDecimal discount,
                                BigDecimal taxableValue, BigDecimal taxRate, BigDecimal tax) {
    }

    public record Result(
            BigDecimal gamingAmount,
            BigDecimal foodAmount,
            BigDecimal rentalAmount,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxableAmount,
            BigDecimal taxAmount,
            BigDecimal roundOff,
            BigDecimal totalAmount,
            List<CategoryTotal> categories
    ) {
    }

    public static Result calculate(List<Line> lines, Discount discount, Map<TaxCategory, TaxRule> taxRules,
                                   boolean roundToRupee) {
        Map<TaxCategory, BigDecimal> subtotals = new EnumMap<>(TaxCategory.class);
        for (TaxCategory c : TaxCategory.values()) {
            subtotals.put(c, Money.ZERO);
        }
        for (Line line : lines) {
            subtotals.merge(line.category(), line.total(), BigDecimal::add);
        }
        BigDecimal subtotal = subtotals.values().stream().reduce(Money.ZERO, BigDecimal::add);
        BigDecimal discountAmount = discountAmount(subtotal, discount);
        Map<TaxCategory, BigDecimal> allocation = allocate(discountAmount, subtotals, subtotal);

        List<CategoryTotal> categories = new ArrayList<>();
        BigDecimal taxable = Money.ZERO;
        BigDecimal tax = Money.ZERO;
        for (TaxCategory c : TaxCategory.values()) {
            BigDecimal catSubtotal = subtotals.get(c);
            if (catSubtotal.signum() == 0) {
                continue;
            }
            BigDecimal catDiscount = allocation.get(c);
            BigDecimal afterDiscount = catSubtotal.subtract(catDiscount);
            TaxRule rule = taxRules.getOrDefault(c, TaxRule.NONE);
            BigDecimal rate = rule.enabled() ? rule.ratePercent() : BigDecimal.ZERO;
            BigDecimal catTax;
            BigDecimal catTaxable;
            if (rate.signum() == 0) {
                catTax = Money.ZERO;
                catTaxable = afterDiscount;
            } else if (rule.priceMode() == PriceMode.INCLUSIVE) {
                catTax = Money.round(afterDiscount.multiply(rate).divide(Money.HUNDRED.add(rate), 10, Money.ROUNDING));
                catTaxable = afterDiscount.subtract(catTax);
            } else {
                catTax = Money.round(afterDiscount.multiply(rate).divide(Money.HUNDRED, 10, Money.ROUNDING));
                catTaxable = afterDiscount;
            }
            taxable = taxable.add(catTaxable);
            tax = tax.add(catTax);
            categories.add(new CategoryTotal(c, catSubtotal, catDiscount, catTaxable, rate, catTax));
        }
        BigDecimal exact = taxable.add(tax);
        BigDecimal total = roundToRupee ? exact.setScale(0, RoundingMode.HALF_UP).setScale(Money.SCALE) : exact;
        return new Result(subtotals.get(TaxCategory.GAMING), subtotals.get(TaxCategory.FOOD),
                subtotals.get(TaxCategory.RENTAL), subtotal, discountAmount, taxable, tax, total.subtract(exact),
                total, List.copyOf(categories));
    }

    static BigDecimal discountAmount(BigDecimal subtotal, Discount discount) {
        if (discount == null || subtotal.signum() == 0) {
            return Money.ZERO;
        }
        BigDecimal amount = discount.type() == DiscountType.PERCENTAGE
                ? Money.round(subtotal.multiply(discount.value()).divide(Money.HUNDRED, 10, Money.ROUNDING))
                : Money.round(discount.value());
        if (discount.maxAmount() != null && amount.compareTo(discount.maxAmount()) > 0) {
            amount = Money.round(discount.maxAmount());
        }
        return amount.min(subtotal);
    }

    private static Map<TaxCategory, BigDecimal> allocate(BigDecimal discount, Map<TaxCategory, BigDecimal> subtotals,
                                                         BigDecimal subtotal) {
        Map<TaxCategory, BigDecimal> result = new EnumMap<>(TaxCategory.class);
        for (TaxCategory c : TaxCategory.values()) {
            result.put(c, Money.ZERO);
        }
        if (discount.signum() == 0) {
            return result;
        }
        List<TaxCategory> present = subtotals.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0).map(Map.Entry::getKey).toList();
        BigDecimal remaining = discount;
        for (int i = 0; i < present.size(); i++) {
            TaxCategory c = present.get(i);
            BigDecimal share = i == present.size() - 1
                    ? remaining
                    : Money.round(discount.multiply(subtotals.get(c)).divide(subtotal, 10, Money.ROUNDING));
            result.put(c, share);
            remaining = remaining.subtract(share);
        }
        return result;
    }
}
