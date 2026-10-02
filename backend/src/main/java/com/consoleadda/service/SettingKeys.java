package com.consoleadda.service;

/** Keys of rows in {@code app_settings}. Values are business configuration, edited by admins. */
public final class SettingKeys {

    public static final String BUSINESS_DAY_START = "business.day-start-time";
    public static final String BILL_PREFIX = "billing.bill-prefix";
    public static final String ROUND_TO_RUPEE = "billing.round-to-rupee";
    public static final String CREDIT_MAX_MINUTES = "gaming.credit.max-minutes-per-session";
    public static final String ALLOW_NEGATIVE_STOCK = "inventory.allow-negative-stock";
    public static final String BUSINESS_NAME = "business.name";
    public static final String BUSINESS_TAGLINE = "business.tagline";
    public static final String BUSINESS_ADDRESS = "business.address";
    public static final String BUSINESS_PHONE = "business.phone";
    public static final String BUSINESS_GSTIN = "business.gstin";
    public static final String INVOICE_FOOTER = "invoice.footer";
    public static final String OPERATOR_CANCEL_WINDOW = "session.operator-cancel-window-minutes";
    public static final String MAX_END_BACKDATE = "session.max-end-backdate-minutes";

    private SettingKeys() {
    }
}
