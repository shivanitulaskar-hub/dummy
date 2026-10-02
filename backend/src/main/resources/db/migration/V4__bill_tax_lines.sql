-- Phase 3: GST breakdown per bill and category, stored at payment time so invoices never recompute tax
-- with today's rates.
CREATE TABLE bill_tax_lines (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    bill_id        BIGINT         NOT NULL,
    tax_category   VARCHAR(20)    NOT NULL,
    hsn_sac_code   VARCHAR(20)    NULL,
    subtotal       DECIMAL(12,2)  NOT NULL,
    discount       DECIMAL(12,2)  NOT NULL,
    taxable_value  DECIMAL(12,2)  NOT NULL,
    rate_percent   DECIMAL(5,2)   NOT NULL,
    price_mode     VARCHAR(20)    NOT NULL,
    tax_amount     DECIMAL(12,2)  NOT NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_bill_tax_lines UNIQUE (bill_id, tax_category),
    CONSTRAINT fk_bill_tax_lines_bill FOREIGN KEY (bill_id) REFERENCES bills (id),
    CONSTRAINT ck_bill_tax_lines_category CHECK (tax_category IN ('GAMING', 'FOOD', 'RENTAL')),
    CONSTRAINT ck_bill_tax_lines_mode CHECK (price_mode IN ('INCLUSIVE', 'EXCLUSIVE')),
    CONSTRAINT ck_bill_tax_lines_amounts CHECK (subtotal >= 0 AND discount >= 0 AND taxable_value >= 0
        AND rate_percent >= 0 AND tax_amount >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_bills_type_date ON bills (bill_type, business_date);
CREATE INDEX idx_bills_paid_at ON bills (paid_at);
