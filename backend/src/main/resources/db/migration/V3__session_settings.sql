-- Phase 2: settings used by session management.
INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('session.operator-cancel-window-minutes', '15', 'INTEGER',
     'Operators may cancel a session only within this many minutes of its start; admins can cancel any time'),
    ('session.max-end-backdate-minutes', '30', 'INTEGER',
     'How far in the past an end time may be when completing a session (e.g. bill finished a little after END was pressed)');
