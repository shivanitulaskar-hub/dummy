-- Phase 4: game rentals, daily closing and reports.

-- A late fee is charged on its own bill at return time (the original rental bill is never edited).
ALTER TABLE game_rentals
    ADD COLUMN late_fee_bill_id BIGINT NULL AFTER bill_id,
    ADD CONSTRAINT fk_game_rentals_late_fee_bill FOREIGN KEY (late_fee_bill_id) REFERENCES bills (id);

CREATE INDEX idx_game_rentals_out_at ON game_rentals (out_at);
CREATE INDEX idx_game_rentals_customer ON game_rentals (customer_id);
CREATE INDEX idx_gaming_sessions_customer ON gaming_sessions (customer_id);

-- Every business day gets a row the first time money moves on it (status OPEN). Payments take a shared
-- lock on that row and closing takes an exclusive one, so a bill can never be paid "during" a closing and
-- be missing from its totals.
ALTER TABLE daily_closings DROP CHECK ck_daily_closings_status;
ALTER TABLE daily_closings
    ADD CONSTRAINT ck_daily_closings_status CHECK (status IN ('OPEN', 'CLOSED', 'REOPENED')),
    ADD COLUMN bill_count            INT            NOT NULL DEFAULT 0 AFTER session_count,
    ADD COLUMN round_off_total       DECIMAL(12,2)  NOT NULL DEFAULT 0.00 AFTER tax_total,
    ADD COLUMN deposit_total         DECIMAL(12,2)  NOT NULL DEFAULT 0.00 AFTER refund_total,
    ADD COLUMN deposit_refund_total  DECIMAL(12,2)  NOT NULL DEFAULT 0.00 AFTER deposit_total;

INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('reports.station-hours-per-day', '12', 'INTEGER',
     'Opening hours per day, used as 100% when calculating station utilization');
