package com.consoleadda.report;

import com.consoleadda.support.Phase4TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIntegrationTest extends Phase4TestSupport {

    private JsonNode report(String token, String type, LocalDate from, LocalDate to) throws Exception {
        return readJson(mockMvc.perform(get("/api/reports/{type}", type).param("from", from.toString())
                        .param("to", to.toString()).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode rowWhere(JsonNode report, String key, String value) {
        for (JsonNode row : report.get("rows")) {
            if (value.equals(row.get(key).asText())) {
                return row;
            }
        }
        throw new AssertionError("No row with " + key + " = " + value + " in " + report.get("type"));
    }

    @Test
    void reportsCoverADaysActivityAndExport() throws Exception {
        String op = operatorToken();
        String admin = adminToken();
        long station = createStation();
        String stationCode = jdbc.queryForObject("SELECT code FROM gaming_stations WHERE id = ?", String.class, station);
        long food = createFoodItem("Report Nachos", "80.00", 0, true);
        long game = createGame("Report Game", "1000.00", "100.00", 7, "10.00", "200.00");
        String mobile = uniqueMobile();
        LocalDate day = moveToFreshBusinessDay();

        long session = readJson(startRequest(op, station, 2, mobile, "=HYPERLINK(\"http://x\")")
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        clock.advance(Duration.ofMinutes(90));
        JsonNode gaming = complete(op, session, "UPI");
        BigDecimal gamingTotal = gaming.get("charges").get("totalAmount").decimalValue();
        BigDecimal foodTotal = walkIn(op, food, 3, "CASH", mobile).get("totalAmount").decimalValue();
        JsonNode rental = rent(op, game, mobile, null, null, "CASH");
        BigDecimal net = gamingTotal.add(foodTotal).add(rental.get("totalAmount").decimalValue());

        // Daily: operators may open it.
        JsonNode daily = report(op, "daily", day, day);
        assertThat(daily.get("rows")).hasSize(1);
        JsonNode d = daily.get("rows").get(0);
        assertThat(d.get("date").asText()).isEqualTo(day.toString());
        assertThat(d.get("bills").asInt()).isEqualTo(3);
        assertThat(d.get("sessions").asInt()).isEqualTo(1);
        assertThat(d.get("gamingHours").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(d.get("foodUnits").asInt()).isEqualTo(3);
        assertThat(d.get("netSales").decimalValue()).isEqualByComparingTo(net);
        assertThat(d.get("upi").decimalValue()).isEqualByComparingTo(gamingTotal);
        assertThat(daily.get("totals").get("netSales").decimalValue()).isEqualByComparingTo(net);

        // Everything else is admin-only.
        mockMvc.perform(get("/api/reports/monthly").header("Authorization", bearer(op)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/nonsense").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        assertThat(readJson(mockMvc.perform(get("/api/reports").header("Authorization", bearer(op)))
                .andReturn().getResponse().getContentAsString()).findValuesAsText("type")).containsExactly("daily");

        JsonNode monthly = report(admin, "monthly", day, day);
        assertThat(rowWhere(monthly, "month", day.toString().substring(0, 7)).get("netSales").decimalValue())
                .isEqualByComparingTo(net);

        JsonNode gamingReport = report(admin, "gaming", day, day);
        JsonNode g = rowWhere(gamingReport, "billNumber", gaming.get("billNumber").asText());
        assertThat(g.get("station").asText()).isEqualTo(stationCode);
        assertThat(g.get("players").asInt()).isEqualTo(2);
        assertThat(g.get("billedMinutes").asInt()).isEqualTo(90);

        JsonNode foodReport = report(admin, "food", day, day);
        JsonNode fr = foodReport.get("rows").get(0);
        assertThat(fr.get("item").asText()).startsWith("Report Nachos");
        assertThat(fr.get("quantity").asInt()).isEqualTo(3);
        assertThat(fr.get("sales").decimalValue()).isEqualByComparingTo("240");
        assertThat(fr.get("cost").decimalValue()).isEqualByComparingTo("60"); // 3 × cost 20
        assertThat(fr.get("margin").decimalValue()).isEqualByComparingTo("180");

        JsonNode payments = report(admin, "payments", day, day);
        assertThat(rowWhere(payments, "mode", "UPI").get("received").decimalValue()).isEqualByComparingTo(gamingTotal);
        assertThat(rowWhere(payments, "mode", "CASH").get("deposits").decimalValue()).isEqualByComparingTo("200");

        JsonNode stations = report(admin, "stations", day, day);
        JsonNode s = rowWhere(stations, "station", stationCode);
        assertThat(s.get("sessions").asInt()).isEqualTo(1);
        assertThat(s.get("playedHours").decimalValue()).isEqualByComparingTo("1.5");
        // 90 min of a 12-hour day = 12.5 %
        assertThat(s.get("utilization").decimalValue()).isEqualByComparingTo("12.50");

        JsonNode customers = report(admin, "customers", day, day);
        JsonNode c = rowWhere(customers, "mobile", mobile);
        assertThat(c.get("bills").asInt()).isEqualTo(3);
        assertThat(c.get("spent").decimalValue()).isEqualByComparingTo(net);

        JsonNode rentals = report(admin, "rentals", day, day);
        assertThat(rowWhere(rentals, "rentalNumber", rental.get("rentalNumber").asText()).get("status").asText())
                .isEqualTo("OUT");
        JsonNode roi = report(admin, "game-roi", day, day);
        assertThat(roi.get("rows").size()).isGreaterThan(0);
        JsonNode inventory = report(admin, "inventory", day, day);
        assertThat(rowWhere(inventory, "item", fr.get("item").asText()).get("sales").asInt()).isEqualTo(3);

        // CSV: BOM, header, the day's row, a totals row, and formula-looking names neutralised.
        MvcResult csv = mockMvc.perform(get("/api/reports/daily/export").param("format", "csv")
                        .param("from", day.toString()).param("to", day.toString()).header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"console-adda-daily-" + day + "-to-" + day + ".csv\""))
                .andReturn();
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        String text = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Date,Bills,Sessions,Gaming hours");
        List<String> lines = text.lines().toList();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).startsWith(day + ",3,1,1.50,3,");
        assertThat(lines.get(2)).startsWith("TOTAL,3,1,");

        String customersCsv = new String(mockMvc.perform(get("/api/reports/customers/export").param("format", "csv")
                        .param("from", day.toString()).param("to", day.toString()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(customersCsv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"").doesNotContain(",=HYPERLINK");

        // Excel: real numbers and dates.
        byte[] xlsx = mockMvc.perform(get("/api/reports/daily/export").param("format", "xlsx")
                        .param("from", day.toString()).param("to", day.toString()).header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(2);
            Row data = sheet.getRow(3);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(data.getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(day);
            int netColumn = -1;
            for (int i = 0; i < header.getLastCellNum(); i++) {
                if ("Net sales".equals(header.getCell(i).getStringCellValue())) {
                    netColumn = i;
                }
            }
            assertThat(BigDecimal.valueOf(data.getCell(netColumn).getNumericCellValue())).isEqualByComparingTo(net);
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("TOTAL");
        }

        mockMvc.perform(get("/api/reports/daily/export").param("format", "pdf").header("Authorization", bearer(op)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRanges() throws Exception {
        String admin = adminToken();
        mockMvc.perform(get("/api/reports/daily").param("from", "2026-10-05").param("to", "2026-10-01")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATES"));
        mockMvc.perform(get("/api/reports/daily").param("from", "2020-01-01").param("to", "2026-01-01")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
        mockMvc.perform(get("/api/reports/daily").param("from", "not-a-date").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reports/daily")).andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardShowsTodayToEveryoneAndTheRestToAdmins() throws Exception {
        String op = operatorToken();
        String admin = adminToken();
        long food = createFoodItem("Dash Tea", "25.00", 0, false);
        LocalDate day = moveToFreshBusinessDay();
        BigDecimal t1 = walkIn(op, food, 2, "CASH", null).get("totalAmount").decimalValue();
        BigDecimal t2 = walkIn(op, food, 1, "UPI", null).get("totalAmount").decimalValue();

        mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(day.toString()))
                .andExpect(jsonPath("$.today.billCount").value(2))
                .andExpect(jsonPath("$.today.netSales").value(t1.add(t2).doubleValue()))
                .andExpect(jsonPath("$.today.foodUnits").value(3))
                .andExpect(jsonPath("$.today.profitEstimate").isEmpty())
                .andExpect(jsonPath("$.month").isEmpty())
                .andExpect(jsonPath("$.revenueByDay").isEmpty());

        JsonNode dash = readJson(mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(dash.get("today").get("averageBill").decimalValue())
                .isEqualByComparingTo(t1.add(t2).divide(BigDecimal.TWO, 2, java.math.RoundingMode.HALF_UP));
        // Profit estimate = sales excl. GST − food cost (3 × 20).
        BigDecimal tax = jdbc.queryForObject("SELECT COALESCE(SUM(tax_amount), 0) FROM bills WHERE business_date = ? "
                + "AND status = 'PAID'", BigDecimal.class, day);
        assertThat(dash.get("today").get("profitEstimate").decimalValue())
                .isEqualByComparingTo(t1.add(t2).subtract(tax).subtract(new BigDecimal("60")));
        assertThat(dash.get("month").get("month").asText()).isEqualTo(day.toString().substring(0, 7));
        assertThat(dash.get("revenueByDay")).hasSize(30);
        assertThat(dash.get("revenueByDay").get(29).get("date").asText()).isEqualTo(day.toString());
        assertThat(dash.get("revenueByDay").get(29).get("food").decimalValue()).isEqualByComparingTo("75");
        assertThat(dash.get("paymentMethods")).hasSize(4);
        assertThat(dash.get("stationUtilization").size()).isGreaterThan(0);
    }
}
