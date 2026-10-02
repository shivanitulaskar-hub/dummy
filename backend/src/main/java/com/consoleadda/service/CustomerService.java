package com.consoleadda.service;

import com.consoleadda.dto.PageResponse;
import com.consoleadda.dto.customer.CustomerHistory;
import com.consoleadda.dto.customer.CustomerRequest;
import com.consoleadda.dto.customer.CustomerResponse;
import com.consoleadda.entity.Customer;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.mapper.RentalMapper;
import com.consoleadda.repository.BillRepository;
import com.consoleadda.repository.CustomerRepository;
import com.consoleadda.repository.GameRentalRepository;
import com.consoleadda.repository.GamingSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Customer master: lookup by mobile at the counter, search, edit and visit history. */
@Service
public class CustomerService {

    private static final Pattern INDIAN_MOBILE = Pattern.compile("[6-9]\\d{9}");
    static final String DEFAULT_NAME = "Guest";
    public static final int HISTORY_LIMIT = 100;

    private final CustomerRepository repository;
    private final GamingSessionRepository sessionRepository;
    private final BillRepository billRepository;
    private final GameRentalRepository rentalRepository;
    private final RentalMapper rentalMapper;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    public CustomerService(CustomerRepository repository, GamingSessionRepository sessionRepository,
                           BillRepository billRepository, GameRentalRepository rentalRepository,
                           RentalMapper rentalMapper, AuditService auditService, JdbcTemplate jdbc) {
        this.repository = repository;
        this.sessionRepository = sessionRepository;
        this.billRepository = billRepository;
        this.rentalRepository = rentalRepository;
        this.rentalMapper = rentalMapper;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- master

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String query, int page, int size) {
        String q = StringUtils.hasText(query) ? "%" + query.trim().toLowerCase() + "%" : null;
        return PageResponse.of(repository.search(q, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)))
                .map(CustomerService::toResponse));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        repository.saveAndFlush(customer);
        auditService.record("CUSTOMER_CREATED", "Customer", customer.getId(), null, snapshot(customer));
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = load(id);
        Map<String, Object> before = snapshot(customer);
        apply(customer, request);
        repository.saveAndFlush(customer);
        auditService.record("CUSTOMER_UPDATED", "Customer", id, before, snapshot(customer));
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerHistory history(Long id) {
        Customer customer = load(id);
        PageRequest limit = PageRequest.of(0, HISTORY_LIMIT);
        List<CustomerHistory.Session> sessions = sessionRepository.findByCustomer(id, limit).stream()
                .map(s -> new CustomerHistory.Session(s.getId(), s.getStation().getCode(), s.getPlayerCount(),
                        s.getStartedAt(), s.getEndedAt(), s.getBillableMinutes(), s.getGamingAmount(), s.getStatus(),
                        s.getBill().getId(), s.getBill().getBillNumber()))
                .toList();
        var bills = billRepository.findByCustomer(id, limit).stream().map(BillService::summary).toList();
        List<CustomerHistory.FoodPurchase> food = jdbc.query("""
                        SELECT bi.description, SUM(bi.quantity) AS qty, SUM(bi.line_total) AS amount,
                               MAX(b.paid_at) AS last_at
                        FROM bill_items bi JOIN bills b ON b.id = bi.bill_id
                        WHERE b.customer_id = ? AND b.status = 'PAID' AND bi.item_type = 'FOOD'
                        GROUP BY bi.description ORDER BY qty DESC, bi.description LIMIT ?""",
                (rs, i) -> {
                    Timestamp last = rs.getTimestamp("last_at");
                    return new CustomerHistory.FoodPurchase(rs.getString("description"), rs.getLong("qty"),
                            rs.getBigDecimal("amount"), last == null ? null : last.toInstant());
                }, id, HISTORY_LIMIT);
        var rentals = rentalRepository.findByCustomer(id, limit).stream().map(rentalMapper::toResponse).toList();
        return new CustomerHistory(toResponse(customer), sessions, bills, food, rentals);
    }

    // ---------------------------------------------------------------- counter lookup & totals

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

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordPurchase(Customer customer, BigDecimal amount, Instant at) {
        repository.recordPurchase(customer.getId(), amount, at);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reverse(Customer customer, int sessions, int gamingMinutes, BigDecimal amount) {
        repository.reverseTotals(customer.getId(), sessions, gamingMinutes, amount);
    }

    // ---------------------------------------------------------------- helpers

    private Customer load(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    private void apply(Customer customer, CustomerRequest request) {
        Optional<String> mobile = normalizeMobile(request.mobile());
        if (mobile.isPresent() && (customer.getId() == null
                ? repository.findByMobile(mobile.get()).isPresent()
                : repository.existsByMobileAndIdNot(mobile.get(), customer.getId()))) {
            throw BusinessException.conflict("DUPLICATE_MOBILE", "Another customer already has mobile " + mobile.get());
        }
        customer.setName(request.name().trim());
        customer.setMobile(mobile.orElse(null));
        customer.setEmail(StringUtils.hasText(request.email()) ? request.email().trim() : null);
        customer.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
    }

    private static Map<String, Object> snapshot(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.getName());
        m.put("mobile", c.getMobile());
        m.put("email", c.getEmail());
        m.put("notes", c.getNotes());
        return m;
    }

    static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getMobile(), c.getEmail(), c.getNotes(),
                c.getTotalSessions(), c.getTotalGamingMinutes(), c.getTotalSpent(), c.getLastVisitAt(),
                c.getCreatedAt());
    }
}
