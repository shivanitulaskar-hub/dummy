package com.consoleadda.service.billing;

import com.consoleadda.service.billing.BillCalculator.Discount;
import com.consoleadda.service.billing.BillCalculator.Line;
import com.consoleadda.service.billing.BillCalculator.Result;
import com.consoleadda.service.billing.BillCalculator.TaxRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillCalculatorTest {

    private static Line gaming(String amount) {
        return new Line(TaxCategory.GAMING, "Gaming", 1, new BigDecimal(amount));
    }

    private static Line food(int qty, String unit) {
        return new Line(TaxCategory.FOOD, "Food", qty, new BigDecimal(unit));
    }

    private static final Map<TaxCategory, TaxRule> NO_TAX = Map.of();

    @Test
    void gamingOnly() {
        Result r = BillCalculator.calculate(List.of(gaming("190.00")), null, NO_TAX, true);
        assertThat(r.gamingAmount()).isEqualByComparingTo("190.00");
        assertThat(r.subtotal()).isEqualByComparingTo("190.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("190.00");
        assertThat(r.roundOff()).isEqualByComparingTo("0");
    }

    @Test
    void foodPlusGaming() {
        // Chicken Shawarma × 2 + Virgin Mojito × 1 added to a ₹150 gaming bill.
        Result r = BillCalculator.calculate(List.of(gaming("150.00"), food(2, "120.00"), food(1, "59.00")),
                null, NO_TAX, true);
        assertThat(r.foodAmount()).isEqualByComparingTo("299.00");
        assertThat(r.subtotal()).isEqualByComparingTo("449.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("449.00");
    }

    @Test
    void flatDiscountFromSpecExample() {
        // Subtotal ₹500, discount ₹50, taxable ₹450.
        Result r = BillCalculator.calculate(List.of(gaming("500.00")),
                new Discount(DiscountType.FLAT, new BigDecimal("50"), null), NO_TAX, true);
        assertThat(r.subtotal()).isEqualByComparingTo("500.00");
        assertThat(r.discountAmount()).isEqualByComparingTo("50.00");
        assertThat(r.taxableAmount()).isEqualByComparingTo("450.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("450.00");
    }

    @Test
    void percentageDiscountIsCappedByMaxAmount() {
        Result r = BillCalculator.calculate(List.of(gaming("1000.00")),
                new Discount(DiscountType.PERCENTAGE, new BigDecimal("10"), new BigDecimal("75")), NO_TAX, true);
        assertThat(r.discountAmount()).isEqualByComparingTo("75.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("925.00");
    }

    @Test
    void discountNeverExceedsSubtotal() {
        Result r = BillCalculator.calculate(List.of(gaming("90.00")),
                new Discount(DiscountType.FLAT, new BigDecimal("150"), null), NO_TAX, true);
        assertThat(r.discountAmount()).isEqualByComparingTo("90.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void invalidDiscountsAreRejected() {
        assertThatThrownBy(() -> new Discount(DiscountType.PERCENTAGE, new BigDecimal("101"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Discount(DiscountType.FLAT, new BigDecimal("-1"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exclusiveGstIsAddedOnTop() {
        Map<TaxCategory, TaxRule> gst = Map.of(TaxCategory.GAMING,
                new TaxRule(true, new BigDecimal("18"), PriceMode.EXCLUSIVE));
        Result r = BillCalculator.calculate(List.of(gaming("150.00")), null, gst, false);
        assertThat(r.taxableAmount()).isEqualByComparingTo("150.00");
        assertThat(r.taxAmount()).isEqualByComparingTo("27.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("177.00");
    }

    @Test
    void inclusiveGstIsExtractedFromThePrice() {
        Map<TaxCategory, TaxRule> gst = Map.of(TaxCategory.GAMING,
                new TaxRule(true, new BigDecimal("18"), PriceMode.INCLUSIVE));
        Result r = BillCalculator.calculate(List.of(gaming("118.00")), null, gst, false);
        assertThat(r.taxAmount()).isEqualByComparingTo("18.00");
        assertThat(r.taxableAmount()).isEqualByComparingTo("100.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("118.00");
    }

    @Test
    void disabledGstAddsNothing() {
        Map<TaxCategory, TaxRule> gst = Map.of(TaxCategory.GAMING,
                new TaxRule(false, new BigDecimal("18"), PriceMode.EXCLUSIVE));
        assertThat(BillCalculator.calculate(List.of(gaming("150.00")), null, gst, false).taxAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    void discountIsAllocatedProRataBeforeMixedRateGst() {
        Map<TaxCategory, TaxRule> gst = Map.of(
                TaxCategory.GAMING, new TaxRule(true, new BigDecimal("18"), PriceMode.EXCLUSIVE),
                TaxCategory.FOOD, new TaxRule(true, new BigDecimal("5"), PriceMode.EXCLUSIVE));
        // Gaming 300 + food 100 = 400; 10% discount = 40 → gaming 30, food 10.
        Result r = BillCalculator.calculate(List.of(gaming("300.00"), food(1, "100.00")),
                new Discount(DiscountType.PERCENTAGE, new BigDecimal("10"), null), gst, false);
        assertThat(r.categories()).hasSize(2);
        assertThat(r.categories().get(0).discount()).isEqualByComparingTo("30.00");
        assertThat(r.categories().get(1).discount()).isEqualByComparingTo("10.00");
        assertThat(r.taxAmount()).isEqualByComparingTo("53.10"); // 270 × 18% = 48.60 + 90 × 5% = 4.50
        assertThat(r.totalAmount()).isEqualByComparingTo("413.10");
    }

    @Test
    void allocationsAlwaysSumToTheDiscount() {
        Result r = BillCalculator.calculate(List.of(gaming("100.00"), food(1, "100.00"),
                        new Line(TaxCategory.RENTAL, "Rental", 1, new BigDecimal("100.00"))),
                new Discount(DiscountType.FLAT, new BigDecimal("10"), null), NO_TAX, false);
        BigDecimal sum = r.categories().stream().map(BillCalculator.CategoryTotal::discount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("10.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("290.00");
    }

    @Test
    void grandTotalRoundsHalfUpToRupeeAndRecordsRoundOff() {
        Map<TaxCategory, TaxRule> gst = Map.of(TaxCategory.FOOD,
                new TaxRule(true, new BigDecimal("5"), PriceMode.EXCLUSIVE));
        // 59 + 5% = 61.95 → 62.00, round-off +0.05
        Result r = BillCalculator.calculate(List.of(food(1, "59.00")), null, gst, true);
        assertThat(r.totalAmount()).isEqualByComparingTo("62.00");
        assertThat(r.roundOff()).isEqualByComparingTo("0.05");
        // 50 + 5% = 52.50 → HALF_UP → 53.00
        Result half = BillCalculator.calculate(List.of(food(1, "50.00")), null, gst, true);
        assertThat(half.totalAmount()).isEqualByComparingTo("53.00");
        assertThat(half.roundOff()).isEqualByComparingTo("0.50");
    }

    @Test
    void emptyBillIsZero() {
        Result r = BillCalculator.calculate(List.of(), null, NO_TAX, true);
        assertThat(r.totalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void negativeQuantitiesAndPricesAreRejected() {
        assertThatThrownBy(() -> food(-1, "10.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> food(1, "-10.00")).isInstanceOf(IllegalArgumentException.class);
    }
}
