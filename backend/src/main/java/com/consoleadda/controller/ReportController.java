package com.consoleadda.controller;

import com.consoleadda.dto.dashboard.DashboardResponse;
import com.consoleadda.dto.report.ReportInfo;
import com.consoleadda.dto.report.ReportTable;
import com.consoleadda.dto.report.ReportType;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.service.BusinessDayService;
import com.consoleadda.service.report.DashboardService;
import com.consoleadda.service.report.ReportExporter;
import com.consoleadda.service.report.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Dashboard and reports. Operators get today's dashboard tiles and the daily report; the rest is admin-only. */
@RestController
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;
    private final ReportExporter exporter;
    private final DashboardService dashboardService;
    private final BusinessDayService businessDay;

    public ReportController(ReportService reportService, ReportExporter exporter, DashboardService dashboardService,
                            BusinessDayService businessDay) {
        this.reportService = reportService;
        this.exporter = exporter;
        this.dashboardService = dashboardService;
        this.businessDay = businessDay;
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardService.dashboard(user.isAdmin());
    }

    /** Reports this user may open. */
    @GetMapping("/api/reports")
    public List<ReportInfo> reports(@AuthenticationPrincipal AuthenticatedUser user) {
        return Arrays.stream(ReportType.values()).filter(t -> user.isAdmin() || !t.adminOnly())
                .map(t -> new ReportInfo(t.slug(), t.title(), t.adminOnly())).toList();
    }

    @GetMapping("/api/reports/{type}")
    public ReportTable report(@PathVariable String type,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        return build(type, from, to, user);
    }

    /** {@code format}: csv or xlsx. */
    @GetMapping("/api/reports/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        ReportTable table = build(type, from, to, user);
        boolean excel = switch (format.toLowerCase()) {
            case "csv" -> false;
            case "xlsx", "excel" -> true;
            default -> throw BusinessException.badRequest("INVALID_FORMAT", "Format must be csv or xlsx");
        };
        byte[] body = excel ? exporter.xlsx(table) : exporter.csv(table);
        String fileName = exporter.fileName(table, excel ? "xlsx" : "csv");
        return ResponseEntity.ok()
                .contentType(excel ? XLSX : new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }

    private ReportTable build(String slug, LocalDate from, LocalDate to, AuthenticatedUser user) {
        ReportType type = ReportType.fromSlug(slug).orElseThrow(() -> new NotFoundException("Report", slug));
        if (type.adminOnly() && !user.isAdmin()) {
            throw BusinessException.forbidden("ACCESS_DENIED", "Only an admin can open the " + type.title() + " report");
        }
        LocalDate end = to != null ? to : businessDay.today();
        LocalDate start = from != null ? from : end;
        return reportService.build(type, start, end);
    }
}
