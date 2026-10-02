-- =====================================================================
-- Console Adda - baseline schema
-- Conventions:
--   * money: DECIMAL(12,2); percentages: DECIMAL(5,2)
--   * timestamps: DATETIME(6) holding UTC instants
--   * enums: VARCHAR + CHECK constraint (readable, easy to extend via migration)
--   * mutable aggregates carry `version` for optimistic locking
-- =====================================================================

-- ---------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE users (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    username               VARCHAR(50)   NOT NULL,
    password_hash          VARCHAR(100)  NOT NULL,
    full_name              VARCHAR(100)  NOT NULL,
    role_id                BIGINT        NOT NULL,
    active                 BOOLEAN       NOT NULL DEFAULT TRUE,
    must_change_password   BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_login_attempts  INT           NOT NULL DEFAULT 0,
    locked_until           DATETIME(6)   NULL,
    last_login_at          DATETIME(6)   NULL,
    password_changed_at    DATETIME(6)   NULL,
    created_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Configuration
-- ---------------------------------------------------------------------
CREATE TABLE app_settings (
    setting_key    VARCHAR(100)  NOT NULL,
    setting_value  VARCHAR(1000) NOT NULL,
    value_type     VARCHAR(20)   NOT NULL,
    description    VARCHAR(255)  NULL,
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by     BIGINT        NULL,
    PRIMARY KEY (setting_key),
    CONSTRAINT ck_app_settings_type CHECK (value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'TIME')),
    CONSTRAINT fk_app_settings_user FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE tax_settings (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    category      VARCHAR(20)   NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT FALSE,
    rate_percent  DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    price_mode    VARCHAR(20)   NOT NULL DEFAULT 'EXCLUSIVE',
    hsn_sac_code  VARCHAR(20)   NULL,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_tax_settings_category UNIQUE (category),
    CONSTRAINT ck_tax_settings_category CHECK (category IN ('GAMING', 'FOOD', 'RENTAL')),
    CONSTRAINT ck_tax_settings_mode CHECK (price_mode IN ('INCLUSIVE', 'EXCLUSIVE')),
    CONSTRAINT ck_tax_settings_rate CHECK (rate_percent >= 0 AND rate_percent <= 100)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Customers
-- ---------------------------------------------------------------------
CREATE TABLE customers (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(100)   NOT NULL,
    mobile                VARCHAR(15)    NULL,
    email                 VARCHAR(150)   NULL,
    notes                 VARCHAR(500)   NULL,
    total_sessions        INT            NOT NULL DEFAULT 0,
    total_gaming_minutes  INT            NOT NULL DEFAULT 0,
    total_spent           DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    last_visit_at         DATETIME(6)    NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version               BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_mobile UNIQUE (mobile),
    INDEX idx_customers_name (name)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Gaming stations & pricing
-- ---------------------------------------------------------------------
CREATE TABLE gaming_stations (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    code           VARCHAR(20)   NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    console_label  VARCHAR(50)   NULL,
    tv_label       VARCHAR(50)   NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    display_order  INT           NOT NULL DEFAULT 0,
    notes          VARCHAR(500)  NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_gaming_stations_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE pricing_plans (
    id                        BIGINT         NOT NULL AUTO_INCREMENT,
    name                      VARCHAR(100)   NOT NULL,
    day_type                  VARCHAR(20)    NOT NULL,
    active_from               DATE           NOT NULL,
    active_to                 DATE           NULL,
    active                    BOOLEAN        NOT NULL DEFAULT TRUE,
    priority                  INT            NOT NULL DEFAULT 0,
    min_billable_minutes      INT            NOT NULL DEFAULT 60,
    billing_block_minutes     INT            NOT NULL DEFAULT 30,
    grace_minutes             INT            NOT NULL DEFAULT 10,
    grace_every_boundary      BOOLEAN        NOT NULL DEFAULT TRUE,
    extension_block_minutes   INT            NOT NULL DEFAULT 30,
    extension_price           DECIMAL(12,2)  NOT NULL,
    additional_player_price   DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    additional_player_basis   VARCHAR(20)    NOT NULL DEFAULT 'PER_HOUR',
    max_players               INT            NOT NULL DEFAULT 4,
    notes                     VARCHAR(500)   NULL,
    created_at                DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                   BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_pricing_plans_name UNIQUE (name),
    CONSTRAINT ck_pricing_plans_day_type CHECK (day_type IN ('WEEKDAY', 'WEEKEND', 'SPECIAL')),
    CONSTRAINT ck_pricing_plans_basis CHECK (additional_player_basis IN ('PER_SESSION', 'PER_HOUR')),
    CONSTRAINT ck_pricing_plans_dates CHECK (active_to IS NULL OR active_to >= active_from),
    CONSTRAINT ck_pricing_plans_minutes CHECK (min_billable_minutes > 0 AND billing_block_minutes > 0
        AND grace_minutes >= 0 AND grace_minutes < billing_block_minutes AND extension_block_minutes > 0),
    CONSTRAINT ck_pricing_plans_money CHECK (extension_price >= 0 AND additional_player_price >= 0),
    CONSTRAINT ck_pricing_plans_players CHECK (max_players >= 1),
    INDEX idx_pricing_plans_lookup (active, day_type, active_from)
) ENGINE = InnoDB;

CREATE TABLE pricing_rules (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    pricing_plan_id   BIGINT         NOT NULL,
    player_count      INT            NOT NULL,
    duration_minutes  INT            NOT NULL,
    price             DECIMAL(12,2)  NOT NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_pricing_rules_slab UNIQUE (pricing_plan_id, player_count, duration_minutes),
    CONSTRAINT fk_pricing_rules_plan FOREIGN KEY (pricing_plan_id) REFERENCES pricing_plans (id) ON DELETE CASCADE,
    CONSTRAINT ck_pricing_rules_values CHECK (player_count >= 1 AND duration_minutes > 0 AND price >= 0)
) ENGINE = InnoDB;

CREATE TABLE discounts (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    code           VARCHAR(30)    NOT NULL,
    name           VARCHAR(100)   NOT NULL,
    discount_type  VARCHAR(20)    NOT NULL,
    value          DECIMAL(12,2)  NOT NULL,
    max_amount     DECIMAL(12,2)  NULL,
    valid_from     DATE           NULL,
    valid_to       DATE           NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_discounts_code UNIQUE (code),
    CONSTRAINT ck_discounts_type CHECK (discount_type IN ('PERCENTAGE', 'FLAT')),
    CONSTRAINT ck_discounts_value CHECK (value >= 0 AND (discount_type <> 'PERCENTAGE' OR value <= 100)),
    CONSTRAINT ck_discounts_max CHECK (max_amount IS NULL OR max_amount >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Food, beverages & inventory
-- ---------------------------------------------------------------------
CREATE TABLE food_categories (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(60)  NOT NULL,
    display_order  INT          NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_food_categories_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE food_items (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(100)   NOT NULL,
    category_id            BIGINT         NOT NULL,
    selling_price          DECIMAL(12,2)  NOT NULL,
    cost_price             DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    stock_quantity         INT            NOT NULL DEFAULT 0,
    reorder_level          INT            NOT NULL DEFAULT 0,
    track_inventory        BOOLEAN        NOT NULL DEFAULT TRUE,
    gaming_credit_minutes  INT            NOT NULL DEFAULT 0,
    active                 BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at             DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_food_items_name UNIQUE (name),
    CONSTRAINT fk_food_items_category FOREIGN KEY (category_id) REFERENCES food_categories (id),
    CONSTRAINT ck_food_items_prices CHECK (selling_price >= 0 AND cost_price >= 0),
    CONSTRAINT ck_food_items_levels CHECK (reorder_level >= 0 AND gaming_credit_minutes >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Bills & payments
-- ---------------------------------------------------------------------
CREATE TABLE bill_number_sequences (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    prefix      VARCHAR(10)  NOT NULL,
    seq_year    INT          NOT NULL,
    next_value  BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_bill_number_sequences UNIQUE (prefix, seq_year)
) ENGINE = InnoDB;

CREATE TABLE bills (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    bill_number       VARCHAR(30)    NULL,
    bill_type         VARCHAR(20)    NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    customer_id       BIGINT         NULL,
    customer_name     VARCHAR(100)   NULL,
    customer_mobile   VARCHAR(15)    NULL,
    gaming_amount     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    food_amount       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    rental_amount     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    subtotal          DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    discount_id       BIGINT         NULL,
    discount_amount   DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    discount_reason   VARCHAR(255)   NULL,
    taxable_amount    DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    tax_amount        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    round_off         DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    total_amount      DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    business_date     DATE           NULL,
    idempotency_key   VARCHAR(64)    NULL,
    paid_at           DATETIME(6)    NULL,
    paid_by           BIGINT         NULL,
    cancelled_at      DATETIME(6)    NULL,
    cancelled_by      BIGINT         NULL,
    cancel_reason     VARCHAR(255)   NULL,
    notes             VARCHAR(500)   NULL,
    created_by        BIGINT         NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_bills_bill_number UNIQUE (bill_number),
    CONSTRAINT uk_bills_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_bills_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_bills_discount FOREIGN KEY (discount_id) REFERENCES discounts (id),
    CONSTRAINT fk_bills_paid_by FOREIGN KEY (paid_by) REFERENCES users (id),
    CONSTRAINT fk_bills_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (id),
    CONSTRAINT fk_bills_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_bills_type CHECK (bill_type IN ('GAMING', 'WALK_IN', 'RENTAL')),
    CONSTRAINT ck_bills_status CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_bills_amounts CHECK (gaming_amount >= 0 AND food_amount >= 0 AND rental_amount >= 0
        AND subtotal >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal
        AND taxable_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    INDEX idx_bills_business_date (business_date, status),
    INDEX idx_bills_customer (customer_id),
    INDEX idx_bills_customer_mobile (customer_mobile)
) ENGINE = InnoDB;

CREATE TABLE bill_items (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    bill_id        BIGINT         NOT NULL,
    item_type      VARCHAR(20)    NOT NULL,
    food_item_id   BIGINT         NULL,
    description    VARCHAR(200)   NOT NULL,
    quantity       INT            NOT NULL,
    unit_price     DECIMAL(12,2)  NOT NULL,
    line_total     DECIMAL(12,2)  NOT NULL,
    tax_category   VARCHAR(20)    NOT NULL,
    created_by     BIGINT         NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bill_items_bill FOREIGN KEY (bill_id) REFERENCES bills (id),
    CONSTRAINT fk_bill_items_food FOREIGN KEY (food_item_id) REFERENCES food_items (id),
    CONSTRAINT fk_bill_items_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_bill_items_type CHECK (item_type IN ('GAMING', 'FOOD', 'RENTAL', 'LATE_FEE', 'OTHER')),
    CONSTRAINT ck_bill_items_tax_category CHECK (tax_category IN ('GAMING', 'FOOD', 'RENTAL')),
    CONSTRAINT ck_bill_items_values CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0),
    INDEX idx_bill_items_food (food_item_id)
) ENGINE = InnoDB;

CREATE TABLE payments (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    bill_id        BIGINT         NOT NULL,
    payment_type   VARCHAR(20)    NOT NULL,
    mode           VARCHAR(20)    NOT NULL,
    amount         DECIMAL(12,2)  NOT NULL,
    reference_no   VARCHAR(100)   NULL,
    reason         VARCHAR(255)   NULL,
    business_date  DATE           NOT NULL,
    received_by    BIGINT         NULL,
    received_at    DATETIME(6)    NOT NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_bill FOREIGN KEY (bill_id) REFERENCES bills (id),
    CONSTRAINT fk_payments_user FOREIGN KEY (received_by) REFERENCES users (id),
    CONSTRAINT ck_payments_type CHECK (payment_type IN ('PAYMENT', 'REFUND', 'DEPOSIT', 'DEPOSIT_REFUND')),
    CONSTRAINT ck_payments_mode CHECK (mode IN ('CASH', 'UPI', 'CARD', 'OTHER')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    INDEX idx_payments_business_date (business_date, mode),
    INDEX idx_payments_bill (bill_id)
) ENGINE = InnoDB;

CREATE TABLE inventory_transactions (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    food_item_id    BIGINT         NOT NULL,
    txn_type        VARCHAR(20)    NOT NULL,
    quantity        INT            NOT NULL,
    unit_cost       DECIMAL(12,2)  NULL,
    reference_type  VARCHAR(30)    NULL,
    reference_id    BIGINT         NULL,
    notes           VARCHAR(255)   NULL,
    business_date   DATE           NOT NULL,
    created_by      BIGINT         NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_inventory_txn_item FOREIGN KEY (food_item_id) REFERENCES food_items (id),
    CONSTRAINT fk_inventory_txn_user FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_inventory_txn_type CHECK (txn_type IN ('OPENING', 'PURCHASE', 'SALE', 'SALE_REVERSAL', 'WASTAGE', 'ADJUSTMENT')),
    CONSTRAINT ck_inventory_txn_sign CHECK (
        (txn_type IN ('OPENING', 'PURCHASE', 'SALE_REVERSAL') AND quantity > 0)
        OR (txn_type IN ('SALE', 'WASTAGE') AND quantity < 0)
        OR (txn_type = 'ADJUSTMENT' AND quantity <> 0)),
    INDEX idx_inventory_txn_item_date (food_item_id, business_date),
    INDEX idx_inventory_txn_reference (reference_type, reference_id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Gaming sessions
-- ---------------------------------------------------------------------
CREATE TABLE gaming_sessions (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    station_id         BIGINT         NOT NULL,
    customer_id        BIGINT         NULL,
    bill_id            BIGINT         NOT NULL,
    pricing_plan_id    BIGINT         NOT NULL,
    player_count       INT            NOT NULL,
    started_at         DATETIME(6)    NOT NULL,
    ended_at           DATETIME(6)    NULL,
    actual_minutes     INT            NULL,
    credit_minutes     INT            NOT NULL DEFAULT 0,
    billable_minutes   INT            NULL,
    gaming_amount      DECIMAL(12,2)  NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    cancel_reason      VARCHAR(255)   NULL,
    notes              VARCHAR(500)   NULL,
    started_by         BIGINT         NULL,
    ended_by           BIGINT         NULL,
    -- DB-level guarantee: at most one ACTIVE session per station.
    active_station_id  BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN station_id ELSE NULL END) STORED,
    created_at         DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version            BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_gaming_sessions_active_station UNIQUE (active_station_id),
    CONSTRAINT uk_gaming_sessions_bill UNIQUE (bill_id),
    CONSTRAINT fk_gaming_sessions_station FOREIGN KEY (station_id) REFERENCES gaming_stations (id),
    CONSTRAINT fk_gaming_sessions_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_gaming_sessions_bill FOREIGN KEY (bill_id) REFERENCES bills (id),
    CONSTRAINT fk_gaming_sessions_plan FOREIGN KEY (pricing_plan_id) REFERENCES pricing_plans (id),
    CONSTRAINT fk_gaming_sessions_started_by FOREIGN KEY (started_by) REFERENCES users (id),
    CONSTRAINT fk_gaming_sessions_ended_by FOREIGN KEY (ended_by) REFERENCES users (id),
    CONSTRAINT ck_gaming_sessions_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_gaming_sessions_players CHECK (player_count >= 1),
    CONSTRAINT ck_gaming_sessions_minutes CHECK (credit_minutes >= 0
        AND (actual_minutes IS NULL OR actual_minutes >= 0)
        AND (billable_minutes IS NULL OR billable_minutes >= 0)),
    INDEX idx_gaming_sessions_status (status),
    INDEX idx_gaming_sessions_started (started_at),
    INDEX idx_gaming_sessions_station_started (station_id, started_at)
) ENGINE = InnoDB;

CREATE TABLE session_time_credits (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    session_id            BIGINT        NOT NULL,
    source_bill_item_id   BIGINT        NULL,
    minutes               INT           NOT NULL,
    reason                VARCHAR(255)  NOT NULL,
    created_by            BIGINT        NULL,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_session_credits_session FOREIGN KEY (session_id) REFERENCES gaming_sessions (id),
    CONSTRAINT fk_session_credits_bill_item FOREIGN KEY (source_bill_item_id) REFERENCES bill_items (id),
    CONSTRAINT fk_session_credits_user FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_session_credits_minutes CHECK (minutes > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Game rentals
-- ---------------------------------------------------------------------
CREATE TABLE game_master (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(150)   NOT NULL,
    copy_number           INT            NOT NULL DEFAULT 1,
    platform              VARCHAR(20)    NOT NULL DEFAULT 'PS5',
    purchase_price        DECIMAL(12,2)  NOT NULL,
    purchase_date         DATE           NULL,
    rental_price          DECIMAL(12,2)  NOT NULL,
    rental_period_days    INT            NOT NULL DEFAULT 7,
    late_fee_per_day      DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    default_deposit       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    available_for_rental  BOOLEAN        NOT NULL DEFAULT TRUE,
    active                BOOLEAN        NOT NULL DEFAULT TRUE,
    notes                 VARCHAR(500)   NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version               BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_game_master_copy UNIQUE (name, copy_number),
    CONSTRAINT ck_game_master_money CHECK (purchase_price >= 0 AND rental_price >= 0
        AND late_fee_per_day >= 0 AND default_deposit >= 0),
    CONSTRAINT ck_game_master_period CHECK (rental_period_days > 0 AND copy_number > 0)
) ENGINE = InnoDB;

CREATE TABLE game_rentals (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    rental_number       VARCHAR(30)    NOT NULL,
    game_id             BIGINT         NOT NULL,
    customer_id         BIGINT         NULL,
    customer_name       VARCHAR(100)   NOT NULL,
    customer_mobile     VARCHAR(15)    NOT NULL,
    bill_id             BIGINT         NULL,
    rental_days         INT            NOT NULL,
    rental_price        DECIMAL(12,2)  NOT NULL,
    deposit_amount      DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    out_at              DATETIME(6)    NOT NULL,
    expected_return_at  DATETIME(6)    NOT NULL,
    returned_at         DATETIME(6)    NULL,
    late_days           INT            NOT NULL DEFAULT 0,
    late_fee            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    total_amount        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    deposit_refunded    DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    payment_mode        VARCHAR(20)    NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'OUT',
    notes               VARCHAR(500)   NULL,
    created_by          BIGINT         NULL,
    returned_by         BIGINT         NULL,
    -- DB-level guarantee: a physical copy can only be out once at a time.
    active_game_id      BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('OUT', 'OVERDUE') THEN game_id ELSE NULL END) STORED,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_game_rentals_number UNIQUE (rental_number),
    CONSTRAINT uk_game_rentals_active_game UNIQUE (active_game_id),
    CONSTRAINT fk_game_rentals_game FOREIGN KEY (game_id) REFERENCES game_master (id),
    CONSTRAINT fk_game_rentals_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_game_rentals_bill FOREIGN KEY (bill_id) REFERENCES bills (id),
    CONSTRAINT fk_game_rentals_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_game_rentals_returned_by FOREIGN KEY (returned_by) REFERENCES users (id),
    CONSTRAINT ck_game_rentals_status CHECK (status IN ('OUT', 'RETURNED', 'OVERDUE', 'CANCELLED')),
    CONSTRAINT ck_game_rentals_payment_mode CHECK (payment_mode IS NULL OR payment_mode IN ('CASH', 'UPI', 'CARD', 'OTHER')),
    CONSTRAINT ck_game_rentals_money CHECK (rental_price >= 0 AND deposit_amount >= 0 AND late_fee >= 0
        AND total_amount >= 0 AND deposit_refunded >= 0 AND deposit_refunded <= deposit_amount),
    CONSTRAINT ck_game_rentals_days CHECK (rental_days > 0 AND late_days >= 0),
    INDEX idx_game_rentals_status (status, expected_return_at),
    INDEX idx_game_rentals_game (game_id),
    INDEX idx_game_rentals_customer_mobile (customer_mobile)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Daily closing
-- ---------------------------------------------------------------------
CREATE TABLE daily_closings (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    business_date        DATE           NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    gaming_revenue       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    food_revenue         DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    rental_revenue       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    total_revenue        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    cash_total           DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    upi_total            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    card_total           DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    other_total          DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    session_count        INT            NOT NULL DEFAULT 0,
    customer_count       INT            NOT NULL DEFAULT 0,
    gaming_minutes       INT            NOT NULL DEFAULT 0,
    food_units_sold      INT            NOT NULL DEFAULT 0,
    discount_total       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    tax_total            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    refund_total         DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    opening_cash         DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    expected_cash        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    actual_cash          DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    cash_difference      DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    notes                VARCHAR(500)   NULL,
    closed_by            BIGINT         NULL,
    closed_at            DATETIME(6)    NULL,
    reopened_by          BIGINT         NULL,
    reopened_at          DATETIME(6)    NULL,
    reopen_reason        VARCHAR(255)   NULL,
    created_at           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version              BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_closings_date UNIQUE (business_date),
    CONSTRAINT fk_daily_closings_closed_by FOREIGN KEY (closed_by) REFERENCES users (id),
    CONSTRAINT fk_daily_closings_reopened_by FOREIGN KEY (reopened_by) REFERENCES users (id),
    CONSTRAINT ck_daily_closings_status CHECK (status IN ('CLOSED', 'REOPENED'))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Audit
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NULL,
    username     VARCHAR(50)   NULL,
    action       VARCHAR(50)   NOT NULL,
    entity_type  VARCHAR(50)   NOT NULL,
    entity_id    VARCHAR(50)   NULL,
    old_value    JSON          NULL,
    new_value    JSON          NULL,
    ip_address   VARCHAR(45)   NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_created (created_at),
    INDEX idx_audit_logs_action (action, created_at)
) ENGINE = InnoDB;
