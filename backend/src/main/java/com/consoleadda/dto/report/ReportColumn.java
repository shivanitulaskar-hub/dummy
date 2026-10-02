package com.consoleadda.dto.report;

/** A report column; {@code type} tells the UI and the exporters how to format values. */
public record ReportColumn(String key, String label, Type type) {

    public enum Type {
        TEXT,
        INTEGER,
        DECIMAL,
        MONEY,
        PERCENT,
        DATE,
        DATETIME
    }

    public static ReportColumn text(String key, String label) {
        return new ReportColumn(key, label, Type.TEXT);
    }

    public static ReportColumn integer(String key, String label) {
        return new ReportColumn(key, label, Type.INTEGER);
    }

    public static ReportColumn decimal(String key, String label) {
        return new ReportColumn(key, label, Type.DECIMAL);
    }

    public static ReportColumn money(String key, String label) {
        return new ReportColumn(key, label, Type.MONEY);
    }

    public static ReportColumn percent(String key, String label) {
        return new ReportColumn(key, label, Type.PERCENT);
    }

    public static ReportColumn date(String key, String label) {
        return new ReportColumn(key, label, Type.DATE);
    }

    public static ReportColumn dateTime(String key, String label) {
        return new ReportColumn(key, label, Type.DATETIME);
    }
}
