package com.consoleadda.controller;

import com.consoleadda.dto.closing.CloseDayRequest;
import com.consoleadda.dto.closing.ClosingSummary;
import com.consoleadda.dto.closing.DailyClosingResponse;
import com.consoleadda.dto.closing.ReopenDayRequest;
import com.consoleadda.service.BusinessDayService;
import com.consoleadda.service.DailyClosingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Daily closing. Operators close the day; only admins reopen a closed day. */
@RestController
@RequestMapping("/api/daily-closing")
public class DailyClosingController {

    private final DailyClosingService closingService;
    private final BusinessDayService businessDay;

    public DailyClosingController(DailyClosingService closingService, BusinessDayService businessDay) {
        this.closingService = closingService;
        this.businessDay = businessDay;
    }

    /** Closed and reopened days, newest first (default: last 31 days). */
    @GetMapping
    public List<ClosingSummary> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : businessDay.today();
        return closingService.history(from != null ? from : end.minusDays(30), end);
    }

    /** The current business date (the day an operator would close now). */
    @GetMapping("/today")
    public DailyClosingResponse today(@RequestParam(required = false) BigDecimal openingCash) {
        return closingService.get(businessDay.today(), openingCash);
    }

    /** Live figures for an open day, or the frozen snapshot of a closed one. */
    @GetMapping("/{date}")
    public DailyClosingResponse get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestParam(required = false) BigDecimal openingCash) {
        return closingService.get(date, openingCash);
    }

    @PostMapping("/{date}/close")
    public DailyClosingResponse close(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @Valid @RequestBody CloseDayRequest request) {
        return closingService.close(date, request);
    }

    @PostMapping("/{date}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public DailyClosingResponse reopen(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @Valid @RequestBody ReopenDayRequest request) {
        return closingService.reopen(date, request);
    }
}
