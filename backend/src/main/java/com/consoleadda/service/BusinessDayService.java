package com.consoleadda.service;

import com.consoleadda.config.AppProperties;
import com.consoleadda.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate newTransaction;

    public BusinessDayService(Clock clock, AppProperties properties, SettingsService settings, JdbcTemplate jdbc,
                              PlatformTransactionManager txManager) {
        this.clock = clock;
        this.properties = properties;
        this.settings = settings;
        this.jdbc = jdbc;
        this.newTransaction = new TransactionTemplate(txManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

    /**
     * Blocks changes to a business day that has been closed (an admin must reopen it first).
     * <p>
     * Must run inside the transaction that changes the day's money. It takes a shared lock on the day's
     * {@code daily_closings} row until that transaction ends; closing the day takes an exclusive lock on the
     * same row. So a closing waits for bills being paid right now (and includes them), and a bill paid while
     * the closing runs waits and is then refused. The row is created first in its own short transaction
     * (creating it inside this one would turn the shared lock into a deadlock-prone upgrade).
     */
    public void ensureOpen(LocalDate businessDate) {
        createDayRow(businessDate);
        String status = jdbc.queryForObject(
                "SELECT status FROM daily_closings WHERE business_date = ? FOR SHARE", String.class, businessDate);
        if ("CLOSED".equals(status)) {
            throw BusinessException.conflict("DAY_CLOSED", "Business day " + businessDate
                    + " is already closed. Ask an admin to reopen it.");
        }
    }

    /** Creates the OPEN row for a business day if it doesn't exist yet (committed immediately). */
    public void createDayRow(LocalDate businessDate) {
        newTransaction.executeWithoutResult(status -> jdbc.update(
                "INSERT IGNORE INTO daily_closings (business_date, status) VALUES (?, 'OPEN')", businessDate));
    }
}
