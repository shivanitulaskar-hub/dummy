package com.consoleadda.service.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalCalculatorTest {

    private static final Instant DUE = Instant.parse("2026-10-09T12:00:00Z");
    private static final Duration GRACE = Duration.ofHours(2);

    @ParameterizedTest(name = "{0} days on a {1}-day period at 199 = {2}")
    @CsvSource({
            "1, 7, 199.00",
            "7, 7, 199.00",
            "8, 7, 398.00",
            "14, 7, 398.00",
            "15, 7, 597.00",
            "3, 1, 597.00",
    })
    void rentalPriceIsPerStartedPeriod(int days, int period, String expected) {
        assertThat(RentalCalculator.rentalPrice(new BigDecimal("199.00"), period, days)).isEqualByComparingTo(expected);
    }

    @Test
    void rentalPriceRejectsInvalidDays() {
        assertThatThrownBy(() -> RentalCalculator.rentalPrice(BigDecimal.TEN, 7, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "returned {0} after due → {1} late days")
    @CsvSource({
            "-PT48H, 0",   // early
            "PT0S, 0",     // exactly on time
            "PT1H, 0",     // within grace
            "PT2H, 0",     // grace boundary is still on time
            "PT2H1S, 1",   // just past grace: one started day
            "PT23H, 1",
            "PT24H, 1",
            "PT24H1S, 2",
            "PT72H, 3",
            "PT73H, 4",
    })
    void lateDaysAfterGraceCountEveryStartedDay(String lateBy, int expectedDays) {
        Instant returned = DUE.plus(Duration.parse(lateBy));
        assertThat(RentalCalculator.lateDays(DUE, returned, GRACE)).isEqualTo(expectedDays);
    }

    @Test
    void lateDaysWithoutGrace() {
        assertThat(RentalCalculator.lateDays(DUE, DUE.plusSeconds(1), Duration.ZERO)).isEqualTo(1);
        assertThat(RentalCalculator.lateDays(DUE, DUE, Duration.ZERO)).isZero();
    }

    @Test
    void lateFeeIsDaysTimesDailyFee() {
        assertThat(RentalCalculator.lateFee(3, new BigDecimal("30.00"))).isEqualByComparingTo("90.00");
        assertThat(RentalCalculator.lateFee(0, new BigDecimal("30.00"))).isEqualByComparingTo("0.00");
        assertThat(RentalCalculator.lateFee(2, new BigDecimal("12.50"))).isEqualByComparingTo("25.00");
    }

    @Test
    void roiFromSpecExample() {
        // Spec: purchase ₹2,599, rental ₹199 → 2599 / 199 = 13.06 → 14 rentals to recover the cost.
        RentalCalculator.Roi none = RentalCalculator.roi(new BigDecimal("2599"), new BigDecimal("199"), BigDecimal.ZERO, 0);
        assertThat(none.breakEvenRentals()).isEqualTo(14);
        assertThat(none.rentalsToRecover()).isEqualTo(14);
        assertThat(none.remaining()).isEqualByComparingTo("2599.00");
        assertThat(none.roiPercent()).isEqualByComparingTo("-100.00");
        assertThat(none.recovered()).isFalse();

        RentalCalculator.Roi five = RentalCalculator.roi(new BigDecimal("2599"), new BigDecimal("199"),
                new BigDecimal("995.00"), 5);
        assertThat(five.remaining()).isEqualByComparingTo("1604.00");
        assertThat(five.rentalsToRecover()).isEqualTo(9); // 1604 / 199 = 8.06
        assertThat(five.roiPercent()).isEqualByComparingTo("-61.72"); // (995 − 2599) / 2599
        assertThat(five.rentals()).isEqualTo(5);
    }

    @Test
    void roiAfterRecovery() {
        RentalCalculator.Roi roi = RentalCalculator.roi(new BigDecimal("2599"), new BigDecimal("199"),
                new BigDecimal("2786.00"), 14);
        assertThat(roi.recovered()).isTrue();
        assertThat(roi.remaining()).isEqualByComparingTo("0.00");
        assertThat(roi.rentalsToRecover()).isZero();
        assertThat(roi.roiPercent()).isEqualByComparingTo("7.20");
    }

    @Test
    void roiWithZeroCostOrPrice() {
        RentalCalculator.Roi free = RentalCalculator.roi(BigDecimal.ZERO, new BigDecimal("199"), new BigDecimal("199"), 1);
        assertThat(free.roiPercent()).isNull();
        assertThat(free.recovered()).isTrue();
        RentalCalculator.Roi noPrice = RentalCalculator.roi(new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, 0);
        assertThat(noPrice.breakEvenRentals()).isNull();
        assertThat(noPrice.rentalsToRecover()).isNull();
    }
}
