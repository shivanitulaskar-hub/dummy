package com.consoleadda.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * Gap-free, unique bill numbers such as {@code CA-2026-000001}, one sequence per prefix and year.
 * <p>
 * The counter row is locked {@code FOR UPDATE} inside the caller's transaction, so concurrent bills queue up
 * and a rolled-back bill releases its number. The UNIQUE index on {@code bills.bill_number} is the final
 * safeguard.
 * <p>
 * The row for a new year is created in its own short transaction <em>before</em> locking. Doing
 * {@code INSERT IGNORE} inside the billing transaction would take a shared lock on the existing row and then
 * need an exclusive one, which deadlocks when two counters bill at the same moment.
 */
@Service
public class BillNumberService {

    private static final String SELECT_FOR_UPDATE =
            "SELECT next_value FROM bill_number_sequences WHERE prefix = ? AND seq_year = ? FOR UPDATE";

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final TransactionTemplate newTransaction;

    public BillNumberService(JdbcTemplate jdbc, SettingsService settings, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.newTransaction = new TransactionTemplate(txManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(LocalDate businessDate) {
        String prefix = settings.getString(SettingKeys.BILL_PREFIX, "CA");
        int year = businessDate.getYear();
        List<Long> current = jdbc.queryForList(SELECT_FOR_UPDATE, Long.class, prefix, year);
        if (current.isEmpty()) {
            // First bill of the year: create the counter row and commit it, then lock it normally.
            newTransaction.executeWithoutResult(status -> jdbc.update(
                    "INSERT IGNORE INTO bill_number_sequences (prefix, seq_year, next_value) VALUES (?, ?, 1)",
                    prefix, year));
            current = jdbc.queryForList(SELECT_FOR_UPDATE, Long.class, prefix, year);
        }
        long value = current.getFirst();
        jdbc.update("UPDATE bill_number_sequences SET next_value = next_value + 1 WHERE prefix = ? AND seq_year = ?",
                prefix, year);
        return String.format("%s-%d-%06d", prefix, year, value);
    }
}
