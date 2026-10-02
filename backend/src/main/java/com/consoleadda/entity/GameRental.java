package com.consoleadda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A game disc rented to a customer. The generated column {@code active_game_id} (unique) is maintained by
 * MySQL and deliberately not mapped: it guarantees a disc can only be out once at a time.
 * <p>
 * Money lives on bills: {@link #bill} holds the rental charge (and the deposit as a DEPOSIT payment),
 * {@link #lateFeeBill} the late fee charged at return.
 */
@Getter
@Setter
@Entity
@Table(name = "game_rentals")
public class GameRental extends BaseEntity {

    @Column(name = "rental_number", nullable = false, unique = true, length = 30)
    private String rentalNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private GameMaster game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_mobile", nullable = false, length = 15)
    private String customerMobile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "late_fee_bill_id")
    private Bill lateFeeBill;

    @Column(name = "rental_days", nullable = false)
    private int rentalDays;

    @Column(name = "rental_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentalPrice;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "out_at", nullable = false)
    private Instant outAt;

    @Column(name = "expected_return_at", nullable = false)
    private Instant expectedReturnAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "late_days", nullable = false)
    private int lateDays;

    @Column(name = "late_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;

    /** Rental bill total plus late-fee bill total (deposit excluded: it is not revenue). */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "deposit_refunded", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositRefunded = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 20)
    private PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RentalStatus status = RentalStatus.OUT;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "returned_by")
    private Long returnedBy;
}
