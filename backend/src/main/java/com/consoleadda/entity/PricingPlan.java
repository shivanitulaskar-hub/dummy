package com.consoleadda.entity;

import com.consoleadda.service.billing.AdditionalPlayerBasis;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A rate card: day type, validity window, time-rounding rules and player pricing. */
@Getter
@Setter
@Entity
@Table(name = "pricing_plans")
public class PricingPlan extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 20)
    private DayType dayType;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    @Column(name = "active_to")
    private LocalDate activeTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "min_billable_minutes", nullable = false)
    private int minBillableMinutes;

    @Column(name = "billing_block_minutes", nullable = false)
    private int billingBlockMinutes;

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes;

    @Column(name = "grace_every_boundary", nullable = false)
    private boolean graceEveryBoundary = true;

    @Column(name = "extension_block_minutes", nullable = false)
    private int extensionBlockMinutes;

    @Column(name = "extension_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal extensionPrice;

    @Column(name = "additional_player_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalPlayerPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "additional_player_basis", nullable = false, length = 20)
    private AdditionalPlayerBasis additionalPlayerBasis;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "pricingPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("playerCount ASC, durationMinutes ASC")
    private List<PricingRule> rules = new ArrayList<>();

    public boolean isValidOn(LocalDate date) {
        return active && !date.isBefore(activeFrom) && (activeTo == null || !date.isAfter(activeTo));
    }
}
