package com.consoleadda.mapper;

import com.consoleadda.dto.session.ChargeBreakdown;
import com.consoleadda.dto.session.SessionResponse;
import com.consoleadda.entity.Bill;
import com.consoleadda.entity.GamingSession;
import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.PaymentType;
import org.springframework.stereotype.Component;

@Component
public class SessionMapper {

    public SessionResponse toResponse(GamingSession s, ChargeBreakdown charges) {
        Bill bill = s.getBill();
        PaymentMode mode = bill.getPayments().stream()
                .filter(p -> p.getPaymentType() == PaymentType.PAYMENT)
                .map(p -> p.getMode()).findFirst().orElse(null);
        return new SessionResponse(s.getId(), s.getStation().getId(), s.getStation().getCode(), bill.getCustomerName(),
                bill.getCustomerMobile(), s.getPlayerCount(), s.getPricingPlan().getId(), s.getPricingPlan().getName(),
                s.getStartedAt(), s.getEndedAt(), s.getStatus(), bill.getId(), bill.getBillNumber(), mode,
                s.getCancelReason(), charges);
    }
}
