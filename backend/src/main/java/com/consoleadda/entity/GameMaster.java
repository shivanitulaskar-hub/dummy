package com.consoleadda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One physical game disc. Two discs of the same title are two rows (copy 1, copy 2). */
@Getter
@Setter
@Entity
@Table(name = "game_master")
public class GameMaster extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "copy_number", nullable = false)
    private int copyNumber = 1;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform = "PS5";

    @Column(name = "purchase_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    /** Price for one rental period of {@link #rentalPeriodDays} days. */
    @Column(name = "rental_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentalPrice;

    @Column(name = "rental_period_days", nullable = false)
    private int rentalPeriodDays = 7;

    @Column(name = "late_fee_per_day", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFeePerDay = BigDecimal.ZERO;

    @Column(name = "default_deposit", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultDeposit = BigDecimal.ZERO;

    @Column(name = "available_for_rental", nullable = false)
    private boolean availableForRental = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "notes", length = 500)
    private String notes;
}
