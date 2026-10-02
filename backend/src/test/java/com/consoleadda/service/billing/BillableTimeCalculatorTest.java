package com.consoleadda.service.billing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillableTimeCalculatorTest {

    /** Seed configuration: 1 h minimum, 30-minute blocks, 10-minute grace at every boundary. */
    private static final BillingTimeRules DEFAULT = new BillingTimeRules(60, 30, 10, true);

    @Nested
    class SpecificationExamples {

        @ParameterizedTest(name = "{0} → {1} = {2} billable minutes")
        @CsvSource({
                "12:00, 13:00, 60",   // 12:00 → 1:00 = 1 hour
                "12:00, 13:05, 60",   // 12:00 → 1:05 = 1 hour
                "12:00, 13:10, 60",   // 12:00 → 1:10 = 1 hour
                "12:00, 13:11, 90",   // 12:00 → 1:11 = next billable interval
                "12:35, 12:45, 60",   // 12:35 → 12:45 = 1 hour (minimum)
        })
        void clockTimeExamples(String start, String end, int expected) {
            Instant s = Instant.parse("2026-10-05T" + start + ":00Z");
            Instant e = Instant.parse("2026-10-05T" + end + ":00Z");
            int actual = BillableTimeCalculator.actualMinutes(s, e);
            assertThat(BillableTimeCalculator.billableMinutes(actual, DEFAULT)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} min played → {1} billable")
        @CsvSource({
                "60, 60",    // 1h
                "65, 60",    // 1h 5m
                "70, 60",    // 1h 10m
                "71, 90",    // 1h 11m
                "90, 90",    // 1h 30m
                "120, 120",  // 2h
                "135, 150",  // 2h 15m
                "150, 150",  // 2h 30m
        })
        void mandatoryDurations(int played, int expected) {
            assertThat(BillableTimeCalculator.billableMinutes(played, DEFAULT)).isEqualTo(expected);
        }
    }

    @Nested
    class Boundaries {

        @ParameterizedTest(name = "{0} min → {1}")
        @CsvSource({
                "0, 60", "1, 60", "59, 60",                 // minimum charge
                "89, 90", "100, 90", "101, 120",            // grace after 1:30
                "130, 120", "131, 150",                     // grace after 2:00
                "160, 150", "161, 180",
                "240, 240", "250, 240", "251, 270",         // around the 4h slab
                "600, 600", "611, 630",                     // long sessions keep working
        })
        void graceAtEveryBoundary(int played, int expected) {
            assertThat(BillableTimeCalculator.billableMinutes(played, DEFAULT)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "first-boundary-only grace: {0} min → {1}")
        @CsvSource({"70, 60", "71, 90", "90, 90", "91, 120", "100, 120", "121, 150"})
        void graceOnlyAtFirstBoundary(int played, int expected) {
            BillingTimeRules rules = new BillingTimeRules(60, 30, 10, false);
            assertThat(BillableTimeCalculator.billableMinutes(played, rules)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "custom rules (min 30, block 15, grace 5): {0} → {1}")
        @CsvSource({"10, 30", "35, 30", "36, 45", "50, 45", "51, 60"})
        void otherConfiguration(int played, int expected) {
            BillingTimeRules rules = new BillingTimeRules(30, 15, 5, true);
            assertThat(BillableTimeCalculator.billableMinutes(played, rules)).isEqualTo(expected);
        }

        @Test
        void zeroGraceRoundsUpImmediately() {
            BillingTimeRules rules = new BillingTimeRules(60, 30, 0, true);
            assertThat(BillableTimeCalculator.billableMinutes(60, rules)).isEqualTo(60);
            assertThat(BillableTimeCalculator.billableMinutes(61, rules)).isEqualTo(90);
        }
    }

    @Nested
    class Credits {

        @Test
        void fifteenMinuteCreditMakesOneHourFifteenBillAsOneHour() {
            int net = BillableTimeCalculator.netMinutes(75, 15);
            assertThat(net).isEqualTo(60);
            assertThat(BillableTimeCalculator.billableMinutes(net, DEFAULT)).isEqualTo(60);
        }

        @Test
        void creditAppliesBeforeGrace() {
            // 1h 25m played, 15m credit → 1h 10m net → within grace → 1h.
            assertThat(BillableTimeCalculator.billableMinutes(BillableTimeCalculator.netMinutes(85, 15), DEFAULT))
                    .isEqualTo(60);
            // 1h 26m played, 15m credit → 1h 11m net → 1h 30m.
            assertThat(BillableTimeCalculator.billableMinutes(BillableTimeCalculator.netMinutes(86, 15), DEFAULT))
                    .isEqualTo(90);
        }

        @Test
        void creditNeverGoesBelowZeroOrBelowMinimumCharge() {
            assertThat(BillableTimeCalculator.netMinutes(10, 15)).isZero();
            assertThat(BillableTimeCalculator.billableMinutes(0, DEFAULT)).isEqualTo(60);
        }
    }

    @Nested
    class ActualMinutes {

        @Test
        void partialMinutesAreNotCharged() {
            Instant start = Instant.parse("2026-10-05T12:00:00Z");
            assertThat(BillableTimeCalculator.actualMinutes(start, start.plusSeconds(70 * 60 + 59))).isEqualTo(70);
        }

        @Test
        void sessionsCanCrossMidnight() {
            Instant start = Instant.parse("2026-10-05T23:30:00Z");
            assertThat(BillableTimeCalculator.actualMinutes(start, Instant.parse("2026-10-06T01:00:00Z"))).isEqualTo(90);
        }

        @Test
        void endBeforeStartIsRejected() {
            Instant start = Instant.parse("2026-10-05T12:00:00Z");
            assertThatThrownBy(() -> BillableTimeCalculator.actualMinutes(start, start.minusSeconds(60)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void invalidRulesAreRejected() {
        assertThatThrownBy(() -> new BillingTimeRules(60, 30, 30, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingTimeRules(0, 30, 10, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingTimeRules(60, 30, -1, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
