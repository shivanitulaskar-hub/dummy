package com.consoleadda.entity;

import com.consoleadda.service.billing.PriceMode;
import com.consoleadda.service.billing.TaxCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "tax_settings")
public class TaxSetting extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, unique = true, length = 20)
    private TaxCategory category;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    private PriceMode priceMode;

    @Column(name = "hsn_sac_code", length = 20)
    private String hsnSacCode;
}
