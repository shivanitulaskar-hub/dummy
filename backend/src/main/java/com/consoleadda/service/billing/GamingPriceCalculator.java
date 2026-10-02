package com.consoleadda.service.billing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Prices a billable duration for a player count. Pure function.
 *
 * <pre>
 * base(D) = min( min over slabs s ≤ D : price(s) + ceil((D − s) / extBlock) × extPrice,
 *                min over slabs s ≥ D : price(s) )
 * </pre>
 * The customer always gets the cheapest valid combination, so playing less never costs more.
 * Slabs for the exact player count are used when configured; otherwise 1-player slabs plus the
 * additional-player surcharge.
 */
public final class GamingPriceCalculator {

    private static final BigDecimal SIXTY = new BigDecimal("60");

    private GamingPriceCalculator() {
    }

    public static GamingCharge price(int billableMinutes, int playerCount, PlanPricing plan) {
        if (billableMinutes <= 0) {
            throw new IllegalArgumentException("Billable minutes must be positive");
        }
        if (playerCount < 1 || playerCount > plan.maxPlayers()) {
            throw new IllegalArgumentException("Player count must be between 1 and " + plan.maxPlayers());
        }
        NavigableMap<Integer, BigDecimal> explicit = plan.slabsByPlayerCount().get(playerCount);
        boolean useExplicit = playerCount > 1 && explicit != null && !explicit.isEmpty();
        NavigableMap<Integer, BigDecimal> slabs = useExplicit ? explicit : plan.slabsByPlayerCount().get(1);

        Candidate best = cheapest(billableMinutes, slabs, plan);
        BigDecimal base = Money.round(best.amount);

        BigDecimal surcharge = Money.ZERO;
        String surchargeText = "";
        if (!useExplicit && playerCount > 1) {
            int extraPlayers = playerCount - 1;
            BigDecimal perPlayer = Money.nz(plan.additionalPlayerPrice());
            BigDecimal raw = perPlayer.multiply(BigDecimal.valueOf(extraPlayers));
            if (plan.additionalPlayerBasis() == AdditionalPlayerBasis.PER_HOUR) {
                raw = raw.multiply(BigDecimal.valueOf(billableMinutes)).divide(SIXTY, 10, Money.ROUNDING);
                surchargeText = String.format(" + %d extra player(s) × ₹%s/hr × %s", extraPlayers,
                        perPlayer.stripTrailingZeros().toPlainString(), formatMinutes(billableMinutes));
            } else {
                surchargeText = String.format(" + %d extra player(s) × ₹%s", extraPlayers,
                        perPlayer.stripTrailingZeros().toPlainString());
            }
            surcharge = Money.round(raw);
        }
        return new GamingCharge(base, surcharge, base.add(surcharge), best.explanation + surchargeText);
    }

    private static Candidate cheapest(int minutes, NavigableMap<Integer, BigDecimal> slabs, PlanPricing plan) {
        Candidate best = null;
        // Slabs at or below the duration, topped up with extension blocks.
        for (Map.Entry<Integer, BigDecimal> slab : slabs.headMap(minutes, true).entrySet()) {
            int remaining = minutes - slab.getKey();
            int blocks = BillableTimeCalculator.ceilDiv(remaining, plan.extensionBlockMinutes());
            BigDecimal amount = slab.getValue().add(Money.nz(plan.extensionPrice()).multiply(BigDecimal.valueOf(blocks)));
            String text = blocks == 0
                    ? String.format("%s slab ₹%s", formatMinutes(slab.getKey()), plain(slab.getValue()))
                    : String.format("%s slab ₹%s + %d × %s extension ₹%s", formatMinutes(slab.getKey()),
                    plain(slab.getValue()), blocks, formatMinutes(plan.extensionBlockMinutes()),
                    plain(plan.extensionPrice()));
            best = cheaper(best, new Candidate(amount, text));
        }
        // A larger slab is never worse for the customer than an expensive top-up.
        Map.Entry<Integer, BigDecimal> ceiling = slabs.ceilingEntry(minutes);
        if (ceiling != null && ceiling.getKey() > minutes) {
            best = cheaper(best, new Candidate(ceiling.getValue(),
                    String.format("%s slab ₹%s", formatMinutes(ceiling.getKey()), plain(ceiling.getValue()))));
        }
        if (best == null) {
            throw new IllegalStateException("No pricing slab can price " + minutes + " minutes");
        }
        return best;
    }

    private static Candidate cheaper(Candidate current, Candidate candidate) {
        return current == null || candidate.amount.compareTo(current.amount) < 0 ? candidate : current;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public static String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return m + "m";
        }
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    private record Candidate(BigDecimal amount, String explanation) {
    }
}
