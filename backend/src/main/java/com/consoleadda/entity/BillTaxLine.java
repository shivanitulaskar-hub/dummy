package com.consoleadda.entity;

import com.consoleadda.service.billing.PriceMode;
import com.consoleadda.service.billing.TaxCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Per-category GST snapshot of a paid bill (rate, mode and amounts as they were at payment time). */
@Getter
@Setter
@Entity
@Table(name = "bill_tax_lines")
public class BillTaxLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_category", nullable = false, length = 20)
    private TaxCategory taxCategory;

    @Column(name = "hsn_sac_code", length = 20)
    private String hsnSacCode;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(name = "taxable_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    private PriceMode priceMode;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
