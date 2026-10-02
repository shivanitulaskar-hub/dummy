package com.consoleadda.dto.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A report as a table. Row values are String, Integer/Long, BigDecimal, LocalDate or Instant, keyed by
 * column key. {@code totals} (optional) holds the summary row; {@code note} explains how figures are counted.
 */
public record ReportTable(
        String type,
        String title,
        LocalDate from,
        LocalDate to,
        List<ReportColumn> columns,
        List<Map<String, Object>> rows,
        Map<String, Object> totals,
        String note
) {
}
