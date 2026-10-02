package com.consoleadda.mapper;

import com.consoleadda.dto.rental.RentalResponse;
import com.consoleadda.entity.Bill;
import com.consoleadda.entity.GameRental;
import com.consoleadda.entity.RentalStatus;
import com.consoleadda.service.SettingKeys;
import com.consoleadda.service.SettingsService;
import com.consoleadda.service.billing.Money;
import com.consoleadda.service.billing.RentalCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class RentalMapper {

    public static final int DEFAULT_GRACE_HOURS = 2;

    private final SettingsService settings;
    private final Clock clock;

    public RentalMapper(SettingsService settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public Duration grace() {
        return Duration.ofHours(Math.max(0, settings.getInt(SettingKeys.RENTAL_LATE_GRACE_HOURS, DEFAULT_GRACE_HOURS)));
    }

    public boolean isOverdue(GameRental r, Instant now, Duration grace) {
        return r.getStatus() == RentalStatus.OUT && now.isAfter(r.getExpectedReturnAt().plus(grace));
    }

    public RentalResponse toResponse(GameRental r) {
        return toResponse(r, clock.instant(), grace());
    }

    public RentalResponse toResponse(GameRental r, Instant now, Duration grace) {
        boolean overdue = isOverdue(r, now, grace);
        int accruedDays = r.getStatus() == RentalStatus.OUT
                ? RentalCalculator.lateDays(r.getExpectedReturnAt(), now, grace) : r.getLateDays();
        BigDecimal accruedFee = r.getStatus() == RentalStatus.OUT
                ? RentalCalculator.lateFee(accruedDays, r.getGame().getLateFeePerDay()) : r.getLateFee();
        Bill bill = r.getBill();
        Bill lateBill = r.getLateFeeBill();
        return new RentalResponse(r.getId(), r.getRentalNumber(), r.getGame().getId(), r.getGame().getName(),
                r.getGame().getCopyNumber(), r.getCustomer() == null ? null : r.getCustomer().getId(),
                r.getCustomerName(), r.getCustomerMobile(), r.getRentalDays(), r.getRentalPrice(),
                r.getDepositAmount(), r.getOutAt(), r.getExpectedReturnAt(), r.getReturnedAt(),
                overdue ? RentalStatus.OVERDUE : r.getStatus(), r.getLateDays(), r.getLateFee(), accruedDays,
                Money.round(accruedFee), r.getTotalAmount(), r.getDepositRefunded(), r.getPaymentMode(),
                bill == null ? null : bill.getId(), bill == null ? null : bill.getBillNumber(),
                lateBill == null ? null : lateBill.getId(), lateBill == null ? null : lateBill.getBillNumber(),
                r.getNotes());
    }
}
