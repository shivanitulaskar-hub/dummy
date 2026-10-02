package com.consoleadda.service;

import com.consoleadda.dto.discount.DiscountRequest;
import com.consoleadda.dto.discount.DiscountResponse;
import com.consoleadda.entity.Discount;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.repository.DiscountRepository;
import com.consoleadda.service.billing.DiscountType;
import com.consoleadda.service.billing.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Discount master. Operators can only apply discounts configured here (no free-typed amounts), so every
 * discount on a bill is traceable to a rule an admin created.
 */
@Service
public class DiscountService {

    private final DiscountRepository repository;
    private final AuditService auditService;
    private final BusinessDayService businessDay;

    public DiscountService(DiscountRepository repository, AuditService auditService, BusinessDayService businessDay) {
        this.repository = repository;
        this.auditService = auditService;
        this.businessDay = businessDay;
    }

    @Transactional(readOnly = true)
    public List<DiscountResponse> list(boolean onlyUsableToday) {
        LocalDate today = businessDay.today();
        return repository.findAllByOrderByActiveDescNameAsc().stream()
                .filter(d -> !onlyUsableToday || d.isValidOn(today))
                .map(d -> toResponse(d, today)).toList();
    }

    @Transactional
    public DiscountResponse create(DiscountRequest request) {
        validate(request);
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw BusinessException.conflict("DUPLICATE_CODE", "Discount code " + code + " already exists");
        }
        Discount discount = new Discount();
        apply(discount, request, code);
        repository.saveAndFlush(discount);
        DiscountResponse response = toResponse(discount, businessDay.today());
        auditService.record("DISCOUNT_CREATED", "Discount", discount.getId(), null, response);
        return response;
    }

    @Transactional
    public DiscountResponse update(Long id, DiscountRequest request) {
        validate(request);
        Discount discount = repository.findById(id).orElseThrow(() -> new NotFoundException("Discount", id));
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw BusinessException.conflict("DUPLICATE_CODE", "Discount code " + code + " already exists");
        }
        DiscountResponse before = toResponse(discount, businessDay.today());
        apply(discount, request, code);
        repository.saveAndFlush(discount);
        DiscountResponse after = toResponse(discount, businessDay.today());
        auditService.record("DISCOUNT_UPDATED", "Discount", id, before, after);
        return after;
    }

    /** Loads a discount that may be applied on {@code date}, or explains why not. */
    @Transactional(readOnly = true)
    public Discount resolve(Long id, LocalDate date) {
        if (id == null) {
            return null;
        }
        Discount discount = repository.findById(id).orElseThrow(() -> new NotFoundException("Discount", id));
        if (!discount.isValidOn(date)) {
            throw BusinessException.badRequest("INVALID_DISCOUNT", "Discount '" + discount.getName()
                    + "' is not active today");
        }
        return discount;
    }

    private static void validate(DiscountRequest r) {
        if (r.discountType() == DiscountType.PERCENTAGE && r.value().compareTo(Money.HUNDRED) > 0) {
            throw BusinessException.badRequest("INVALID_DISCOUNT", "A percentage discount cannot exceed 100%");
        }
        if (r.validFrom() != null && r.validTo() != null && r.validTo().isBefore(r.validFrom())) {
            throw BusinessException.badRequest("INVALID_DATES", "'Valid to' must be on or after 'valid from'");
        }
    }

    private static void apply(Discount d, DiscountRequest r, String code) {
        d.setCode(code);
        d.setName(r.name().trim());
        d.setDiscountType(r.discountType());
        d.setValue(r.value());
        d.setMaxAmount(r.maxAmount());
        d.setValidFrom(r.validFrom());
        d.setValidTo(r.validTo());
        d.setActive(r.active());
    }

    public static DiscountResponse toResponse(Discount d, LocalDate today) {
        return new DiscountResponse(d.getId(), d.getCode(), d.getName(), d.getDiscountType(), d.getValue(),
                d.getMaxAmount(), d.getValidFrom(), d.getValidTo(), d.isActive(), d.isValidOn(today));
    }
}
