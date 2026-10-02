package com.consoleadda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A play session on one station. The generated column {@code active_station_id} (unique) is maintained by
 * MySQL and deliberately not mapped: it guarantees one ACTIVE session per station at database level.
 */
@Getter
@Setter
@Entity
@Table(name = "gaming_sessions")
public class GamingSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private GamingStation station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false, unique = true)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_plan_id", nullable = false)
    private PricingPlan pricingPlan;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Column(name = "credit_minutes", nullable = false)
    private int creditMinutes;

    @Column(name = "billable_minutes")
    private Integer billableMinutes;

    @Column(name = "gaming_amount", precision = 12, scale = 2)
    private BigDecimal gamingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "started_by")
    private Long startedBy;

    @Column(name = "ended_by")
    private Long endedBy;
}
