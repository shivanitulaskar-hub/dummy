package com.consoleadda.service;

import com.consoleadda.entity.Customer;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class CustomerService {

    private static final Pattern INDIAN_MOBILE = Pattern.compile("[6-9]\\d{9}");
    static final String DEFAULT_NAME = "Guest";

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    /**
     * Normalises an Indian mobile number: strips spaces, dashes, a +91/91 prefix and a leading 0.
     * Returns empty for blank input; throws a validation error for anything that isn't a valid number.
     */
    public static Optional<String> normalizeMobile(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        String digits = raw.replaceAll("[\\s()-]", "");
        if (digits.startsWith("+91")) {
            digits = digits.substring(3);
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!INDIAN_MOBILE.matcher(digits).matches()) {
            throw BusinessException.badRequest("INVALID_MOBILE", "Mobile number must be a valid 10-digit Indian number");
        }
        return Optional.of(digits);
    }

    /**
     * Finds the customer by mobile or creates one. Returns empty for walk-ins without a mobile number
     * (their name, if any, is kept on the bill only). An existing customer's name is never overwritten here.
     */
    @Transactional
    public Optional<Customer> findOrCreate(String name, String mobile) {
        Optional<String> normalized = normalizeMobile(mobile);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(repository.findByMobile(normalized.get()).orElseGet(() -> {
            Customer customer = new Customer();
            customer.setName(StringUtils.hasText(name) ? name.trim() : DEFAULT_NAME);
            customer.setMobile(normalized.get());
            return repository.save(customer);
        }));
    }

    /** Updates cached totals when a gaming bill is paid. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordVisit(Customer customer, int gamingMinutes, BigDecimal amount, Instant at) {
        repository.recordVisit(customer.getId(), gamingMinutes, amount, at);
    }
}
