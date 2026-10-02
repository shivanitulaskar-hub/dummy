package com.consoleadda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "mobile", unique = true, length = 15)
    private String mobile;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Cached aggregates, updated in the same transaction as each completed bill. */
    @Column(name = "total_sessions", nullable = false)
    private int totalSessions;

    @Column(name = "total_gaming_minutes", nullable = false)
    private int totalGamingMinutes;

    @Column(name = "total_spent", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "last_visit_at")
    private Instant lastVisitAt;
}
