package com.consoleadda.service;

import com.consoleadda.config.AppProperties;
import com.consoleadda.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Business dates: a sale belongs to the business day that started at {@code business.day-start-time}
 * (default 06:00 IST), so a 01:30 bill counts towards the previous evening's closing.
 */
@Service
public class BusinessDayService {

    private static final LocalTime DEFAULT_DAY_START = LocalTime.of(6, 0);

    private final Clock clock;
    private final AppProperties properties;
    private final SettingsService settings;
    private final JdbcTemplate jdbc;

    public BusinessDayService(Clock clock, AppProperties properties, SettingsService settings, JdbcTemplate jdbc) {
        this.clock = clock;
        this.properties = properties;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    public LocalDate businessDate(Instant instant) {
        LocalTime dayStart = settings.getTime(SettingKeys.BUSINESS_DAY_START, DEFAULT_DAY_START);
        ZonedDateTime local = instant.atZone(properties.timezone());
        LocalDate date = local.toLocalDate();
        return local.toLocalTime().isBefore(dayStart) ? date.minusDays(1) : date;
    }

    public LocalDate today() {
        return businessDate(clock.instant());
    }

    /** Calendar date in the cafe's timezone (used for pricing-plan validity and weekend detection). */
    public LocalDate localDate(Instant instant) {
        return instant.atZone(properties.timezone()).toLocalDate();
    }

    /** Blocks changes to a business day that has been closed (an admin must reopen it first). */
    public void ensureOpen(LocalDate businessDate) {
        Integer closed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_closings WHERE business_date = ? AND status = 'CLOSED'",
                Integer.class, businessDate);
        if (closed != null && closed > 0) {
            throw BusinessException.conflict("DAY_CLOSED", "Business day " + businessDate
                    + " is already closed. Ask an admin to reopen it.");
        }
    }
}
