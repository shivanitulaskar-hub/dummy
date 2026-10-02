package com.consoleadda.service;

/** Keys of rows in {@code app_settings}. Values are business configuration, edited by admins. */
public final class SettingKeys {

    public static final String BUSINESS_DAY_START = "business.day-start-time";
    public static final String BILL_PREFIX = "billing.bill-prefix";
    public static final String ROUND_TO_RUPEE = "billing.round-to-rupee";
    public static final String CREDIT_MAX_MINUTES = "gaming.credit.max-minutes-per-session";
    public static final String OPERATOR_CANCEL_WINDOW = "session.operator-cancel-window-minutes";
    public static final String MAX_END_BACKDATE = "session.max-end-backdate-minutes";

    private SettingKeys() {
    }
}
