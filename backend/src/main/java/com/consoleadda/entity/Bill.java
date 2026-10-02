package com.consoleadda.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The single financial document. A gaming session owns an OPEN bill from its start; the bill gets its
 * number, totals and payment only when it is completed (PAID).
 */
@Getter
@Setter
@Entity
@Table(name = "bills")
public class Bill extends BaseEntity {

    @Column(name = "bill_number", unique = true, length = 30)
    private String billNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_type", nullable = false, length = 20)
    private BillType billType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillStatus status = BillStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_mobile", length = 15)
    private String customerMobile;

    @Column(name = "gaming_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal gamingAmount = BigDecimal.ZERO;

    @Column(name = "food_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal foodAmount = BigDecimal.ZERO;

    @Column(name = "rental_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentalAmount = BigDecimal.ZERO;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** Discount master link (Phase 3). */
    @Column(name = "discount_id")
    private Long discountId;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @Column(name = "taxable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "round_off", nullable = false, precision = 12, scale = 2)
    private BigDecimal roundOff = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private Long paidBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BillItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BillTaxLine> taxLines = new ArrayList<>();

    public void addTaxLine(BillTaxLine line) {
        line.setBill(this);
        taxLines.add(line);
    }

    public void addItem(BillItem item) {
        item.setBill(this);
        items.add(item);
    }

    public void addPayment(Payment payment) {
        payment.setBill(this);
        payments.add(payment);
    }
}
