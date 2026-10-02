package com.consoleadda.service.report;

import com.consoleadda.config.AppProperties;
import com.consoleadda.dto.report.ReportColumn;
import com.consoleadda.dto.report.ReportTable;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a {@link ReportTable} as CSV (UTF-8 with BOM so Excel shows ₹ and names correctly) or as an Excel
 * workbook with real number/date cells. Times are shown in the cafe's timezone.
 * <p>
 * Text that a spreadsheet would treat as a formula (starting with = + - @) is prefixed with an apostrophe in
 * CSV, because customer names and notes are typed by people (CSV/formula injection).
 */
@Component
public class ReportExporter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String TOTAL_LABEL = "TOTAL";

    private final ZoneId zone;

    public ReportExporter(AppProperties properties) {
        this.zone = properties.timezone();
    }

    public String fileName(ReportTable table, String extension) {
        return "console-adda-" + table.type() + "-" + table.from() + "-to-" + table.to() + "." + extension;
    }

    // ---------------------------------------------------------------- CSV

    public byte[] csv(ReportTable table) {
        StringBuilder out = new StringBuilder("﻿");
        List<ReportColumn> columns = table.columns();
        appendLine(out, columns.stream().map(ReportColumn::label).toList());
        for (Map<String, Object> row : table.rows()) {
            appendLine(out, columns.stream().map(c -> csvValue(c, row.get(c.key()))).toList());
        }
        if (table.totals() != null) {
            appendLine(out, totalsCells(columns, table.totals()));
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<String> totalsCells(List<ReportColumn> columns, Map<String, Object> totals) {
        return columns.stream().map(c -> {
            Object v = totals.get(c.key());
            if (c == columns.get(0) && v == null) {
                return TOTAL_LABEL;
            }
            return csvValue(c, v);
        }).toList();
    }

    private String csvValue(ReportColumn column, Object value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case BigDecimal d -> d.toPlainString();
            case Instant i -> DATE_TIME.format(i.atZone(zone));
            case LocalDate d -> d.toString();
            case Number n -> n.toString();
            default -> guardFormula(value.toString());
        };
    }

    static String guardFormula(String text) {
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }

    private static void appendLine(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            String cell = cells.get(i);
            if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
                out.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                out.append(cell);
            }
        }
        out.append("\r\n");
    }

    // ---------------------------------------------------------------- Excel

    public byte[] xlsx(ReportTable table) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(table.title().length() > 31 ? table.title().substring(0, 31) : table.title());
            Styles normal = new Styles(wb, false);
            Styles bold = new Styles(wb, true);
            List<ReportColumn> columns = table.columns();
            int[] widths = new int[columns.size()];

            int r = 0;
            Row title = sheet.createRow(r++);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("Console Adda: " + table.title() + " (" + table.from() + " to " + table.to() + ")");
            titleCell.setCellStyle(bold.text);
            r++;

            Row header = sheet.createRow(r++);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(columns.get(c).label());
                cell.setCellStyle(bold.text);
                widths[c] = columns.get(c).label().length();
            }
            for (Map<String, Object> row : table.rows()) {
                writeRow(sheet.createRow(r++), columns, row, normal, widths, false);
            }
            if (table.totals() != null) {
                writeRow(sheet.createRow(r++), columns, table.totals(), bold, widths, true);
            }
            if (table.note() != null) {
                r++;
                sheet.createRow(r).createCell(0).setCellValue(table.note());
            }
            for (int c = 0; c < columns.size(); c++) {
                sheet.setColumnWidth(c, Math.min(60, Math.max(8, widths[c] + 2)) * 256);
            }
            sheet.createFreezePane(0, 3);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create Excel report", e);
        }
    }

    private void writeRow(Row row, List<ReportColumn> columns, Map<String, Object> values, Styles styles,
                          int[] widths, boolean totals) {
        for (int c = 0; c < columns.size(); c++) {
            ReportColumn column = columns.get(c);
            Object value = values.get(column.key());
            if (value == null && totals && c == 0) {
                value = TOTAL_LABEL;
            }
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(c);
            int width;
            switch (value) {
                case BigDecimal d -> {
                    cell.setCellValue(d.doubleValue());
                    cell.setCellStyle(styles.forType(column.type()));
                    width = d.toPlainString().length() + 3;
                }
                case Number n -> {
                    cell.setCellValue(n.doubleValue());
                    cell.setCellStyle(styles.forType(ReportColumn.Type.INTEGER));
                    width = n.toString().length() + 2;
                }
                case Instant i -> {
                    cell.setCellValue(i.atZone(zone).toLocalDateTime());
                    cell.setCellStyle(styles.forType(ReportColumn.Type.DATETIME));
                    width = 16;
                }
                case LocalDate d -> {
                    cell.setCellValue(d);
                    cell.setCellStyle(styles.forType(ReportColumn.Type.DATE));
                    width = 10;
                }
                default -> {
                    cell.setCellValue(value.toString());
                    cell.setCellStyle(styles.text);
                    width = value.toString().length();
                }
            }
            widths[c] = Math.max(widths[c], width);
        }
    }

    /** Cell styles per column type (one set plain, one bold for header/totals). */
    private static final class Styles {
        final CellStyle text;
        final Map<ReportColumn.Type, CellStyle> byType = new EnumMap<>(ReportColumn.Type.class);

        Styles(XSSFWorkbook wb, boolean bold) {
            CreationHelper helper = wb.getCreationHelper();
            Font font = wb.createFont();
            font.setBold(bold);
            text = wb.createCellStyle();
            text.setFont(font);
            for (ReportColumn.Type type : ReportColumn.Type.values()) {
                String format = switch (type) {
                    case MONEY -> "#,##0.00";
                    case DECIMAL, PERCENT -> "0.00";
                    case INTEGER -> "0";
                    case DATE -> "yyyy-mm-dd";
                    case DATETIME -> "yyyy-mm-dd hh:mm";
                    case TEXT -> null;
                };
                if (format == null) {
                    byType.put(type, text);
                    continue;
                }
                CellStyle style = wb.createCellStyle();
                style.setFont(font);
                style.setDataFormat(helper.createDataFormat().getFormat(format));
                byType.put(type, style);
            }
        }

        CellStyle forType(ReportColumn.Type type) {
            return byType.get(type);
        }
    }
}
