-- =====================================================================
-- Console Adda - reference / business seed data.
-- Everything here is editable from the admin panel; nothing below is
-- referenced by code as a constant.
-- Users are NOT seeded here: they are created at startup by
-- UserBootstrapService from environment-specific configuration.
-- =====================================================================

INSERT INTO roles (code, name) VALUES
    ('ADMIN', 'Administrator'),
    ('OPERATOR', 'Counter Operator');

INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('business.name',                         'CONSOLE ADDA',                   'STRING',  'Cafe name printed on invoices'),
    ('business.tagline',                      'PS5 GAMING CAFE',                'STRING',  'Subtitle printed on invoices'),
    ('business.address',                      'Mumbai, Maharashtra',            'STRING',  'Address printed on invoices'),
    ('business.phone',                        '',                               'STRING',  'Contact number printed on invoices'),
    ('business.gstin',                        '',                               'STRING',  'GSTIN printed on invoices when GST is enabled'),
    ('business.day-start-time',               '06:00',                          'TIME',    'Business day starts at this local time; earlier sales belong to the previous day'),
    ('billing.bill-prefix',                   'CA',                             'STRING',  'Bill number prefix, e.g. CA-2026-000001'),
    ('billing.round-to-rupee',                'true',                           'BOOLEAN', 'Round grand total to the nearest rupee and record round-off'),
    ('gaming.credit.max-minutes-per-session', '15',                             'INTEGER', 'Maximum free gaming minutes a session can earn from qualifying items'),
    ('inventory.allow-negative-stock',        'true',                           'BOOLEAN', 'Allow selling items even when recorded stock is zero (sale is never blocked at the counter)'),
    ('rental.number-prefix',                  'RN',                             'STRING',  'Rental number prefix'),
    ('rental.late-grace-hours',               '2',                              'INTEGER', 'Hours after expected return before late fees start'),
    ('invoice.footer',                        'Thank you for gaming with us!',  'STRING',  'Invoice footer text');

-- GST disabled by default; rates are placeholders until confirmed by the owner's CA.
INSERT INTO tax_settings (category, enabled, rate_percent, price_mode, hsn_sac_code) VALUES
    ('GAMING', FALSE, 18.00, 'EXCLUSIVE', '998439'),
    ('FOOD',   FALSE,  5.00, 'EXCLUSIVE', '996331'),
    ('RENTAL', FALSE, 18.00, 'EXCLUSIVE', '997329');

INSERT INTO gaming_stations (code, name, console_label, tv_label, active, display_order) VALUES
    ('PS5-01', 'PS5 Station 1', 'PS5 #1', 'TV #1 (44")', TRUE, 1),
    ('PS5-02', 'PS5 Station 2', 'PS5 #2', 'TV #2 (44")', TRUE, 2),
    ('PS5-03', 'PS5 Station 3', 'PS5 #3', 'TV #3 (44")', TRUE, 3),
    ('PS5-04', 'PS5 Station 4', 'PS5 #4', 'TV #4 (44")', TRUE, 4),
    ('PS5-05', 'PS5 Station 5', 'PS5 #5', 'TV #5 (44")', TRUE, 5);

-- Base (weekday) rate card. Weekend falls back to this plan until a WEEKEND plan is added.
INSERT INTO pricing_plans (name, day_type, active_from, active_to, active, priority,
                           min_billable_minutes, billing_block_minutes, grace_minutes, grace_every_boundary,
                           extension_block_minutes, extension_price,
                           additional_player_price, additional_player_basis, max_players, notes)
VALUES ('Standard Weekday', 'WEEKDAY', '2026-01-01', NULL, TRUE, 0,
        60, 30, 10, TRUE,
        30, 45.00,
        50.00, 'PER_HOUR', 4,
        'Base pricing. Additional-player basis pending owner confirmation (see docs/ARCHITECTURE.md A1).');

INSERT INTO pricing_rules (pricing_plan_id, player_count, duration_minutes, price)
SELECT p.id, 1, s.duration_minutes, s.price
FROM pricing_plans p
JOIN (SELECT 60 AS duration_minutes, 90.00 AS price
      UNION ALL SELECT 120, 150.00
      UNION ALL SELECT 180, 240.00
      UNION ALL SELECT 240, 330.00) s
WHERE p.name = 'Standard Weekday';

INSERT INTO food_categories (name, display_order) VALUES
    ('Food', 1), ('Drinks', 2), ('Snacks', 3), ('Cup Noodles', 4), ('Mojito', 5), ('Other', 6);

-- Cost prices are 0.00 until the owner enters them (needed for profit estimates).
-- Stock starts at 0; record opening stock from the Inventory screen.
INSERT INTO food_items (name, category_id, selling_price, cost_price, stock_quantity, reorder_level,
                        track_inventory, gaming_credit_minutes)
SELECT i.name, c.id, i.price, 0.00, 0, 0, i.track_inventory, i.credit
FROM (SELECT 'Chicken Shawarma' AS name, 'Food' AS category, 120.00 AS price, TRUE AS track_inventory, 0 AS credit
      UNION ALL SELECT 'Veg Cheese Shawarma',    'Food',   120.00, TRUE, 0
      UNION ALL SELECT 'Chicken Momos Steamed',  'Food',   170.00, TRUE, 0
      UNION ALL SELECT 'Chicken Momos Fried',    'Food',   190.00, TRUE, 0
      UNION ALL SELECT 'Veg Momos Steamed',      'Food',   160.00, TRUE, 0
      UNION ALL SELECT 'Veg Momos Fried',        'Food',   180.00, TRUE, 0
      UNION ALL SELECT 'Adda Virgin Mojito',     'Mojito',  59.00, TRUE, 15
      UNION ALL SELECT 'Adda Maggi',             'Snacks',  50.00, TRUE, 0) i
JOIN food_categories c ON c.name = i.category;

-- Sample rental catalogue. Prices are the spec's example values: update them in Game Master.
INSERT INTO game_master (name, copy_number, purchase_price, rental_price, rental_period_days,
                         late_fee_per_day, default_deposit, notes)
VALUES
    ('WuKong',             1, 2599.00, 199.00, 7, 0.00, 0.00, 'Sample prices - update before use'),
    ('God of War',         1, 2599.00, 199.00, 7, 0.00, 0.00, 'Sample prices - update before use'),
    ('Ghost of Yotei',     1, 2599.00, 199.00, 7, 0.00, 0.00, 'Sample prices - update before use'),
    ('Ghost of Tsushima',  1, 2599.00, 199.00, 7, 0.00, 0.00, 'Sample prices - update before use'),
    ('Hitman',             1, 2599.00, 199.00, 7, 0.00, 0.00, 'Sample prices - update before use'),
    ('Assassin''s Creed',  1, 2599.00, 199.00, 7, 0.00, 0.00, 'Sample prices - update before use'),
    ('Dragon Ball Z',      1, 2599.00, 199.00, 7, 0.00, 0.00, 'Sample prices - update before use');
