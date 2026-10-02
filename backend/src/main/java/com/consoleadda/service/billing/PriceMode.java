package com.consoleadda.service.billing;

public enum PriceMode {
    /** Listed prices already include GST; tax is extracted from them. */
    INCLUSIVE,
    /** GST is added on top of listed prices. */
    EXCLUSIVE
}
