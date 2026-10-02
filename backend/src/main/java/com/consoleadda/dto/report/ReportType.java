package com.consoleadda.dto.report;

import java.util.Arrays;
import java.util.Optional;

/** Available reports. Operators may only open the daily report (ARCHITECTURE.md A24); the rest are admin-only. */
public enum ReportType {
    DAILY("daily", "Daily sales", false),
    MONTHLY("monthly", "Monthly sales", true),
    GAMING("gaming", "Gaming revenue", true),
    FOOD("food", "Food revenue", true),
    PAYMENTS("payments", "Payment methods", true),
    STATIONS("stations", "Station utilization", true),
    CUSTOMERS("customers", "Customers", true),
    RENTALS("rentals", "Game rentals", true),
    GAME_ROI("game-roi", "Game rental ROI", true),
    INVENTORY("inventory", "Inventory", true);

    private final String slug;
    private final String title;
    private final boolean adminOnly;

    ReportType(String slug, String title, boolean adminOnly) {
        this.slug = slug;
        this.title = title;
        this.adminOnly = adminOnly;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }

    public boolean adminOnly() {
        return adminOnly;
    }

    public static Optional<ReportType> fromSlug(String slug) {
        return Arrays.stream(values()).filter(t -> t.slug.equalsIgnoreCase(slug)).findFirst();
    }
}
