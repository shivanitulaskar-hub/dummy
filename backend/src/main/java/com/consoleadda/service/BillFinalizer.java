package com.consoleadda.service;

import com.consoleadda.entity.Bill;
import com.consoleadda.entity.BillStatus;
import com.consoleadda.entity.BillTaxLine;
import com.consoleadda.entity.Discount;
import com.consoleadda.entity.Payment;
import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.PaymentType;
import com.consoleadda.entity.TaxSetting;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.service.billing.BillCalculator;
import com.consoleadda.service.billing.TaxCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns an OPEN bill into a PAID one: stores totals, discount, GST snapshot, bill number and payment, and
 * writes the audit entries. Shared by gaming-session bills and walk-in sales so both follow identical rules.
 */
@Service
public class BillFinalizer {

    private final BillNumberService billNumberService;
    private final TaxRuleService taxRuleService;
    private final AuditService auditService;

    public BillFinalizer(BillNumberService billNumberService, TaxRuleService taxRuleService, AuditService auditService) {
        this.billNumberService = billNumberService;
        this.taxRuleService = taxRuleService;
        this.auditService = auditService;
    }

    public record PaymentInfo(PaymentMode mode, String referenceNo) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void finalizeBill(Bill bill, BillCalculator.Result totals, Discount discount, PaymentInfo payment,
                             String idempotencyKey, LocalDate businessDate, Instant now, AuthenticatedUser user) {
        bill.setGamingAmount(totals.gamingAmount());
        bill.setFoodAmount(totals.foodAmount());
        bill.setRentalAmount(totals.rentalAmount());
        bill.setSubtotal(totals.subtotal());
        bill.setDiscountId(discount == null ? null : discount.getId());
        bill.setDiscountAmount(totals.discountAmount());
        bill.setDiscountReason(discount == null ? null : discount.getName());
        bill.setTaxableAmount(totals.taxableAmount());
        bill.setTaxAmount(totals.taxAmount());
        bill.setRoundOff(totals.roundOff());
        bill.setTotalAmount(totals.totalAmount());
        bill.setBusinessDate(businessDate);
        bill.setBillNumber(billNumberService.next(businessDate));
        bill.setStatus(BillStatus.PAID);
        bill.setPaidAt(now);
        bill.setPaidBy(user.id());
        bill.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);

        Map<TaxCategory, TaxSetting> taxSettings = taxRuleService.settings();
        bill.getTaxLines().clear();
        for (BillCalculator.CategoryTotal c : totals.categories()) {
            TaxSetting setting = taxSettings.get(c.category());
            BillTaxLine line = new BillTaxLine();
            line.setTaxCategory(c.category());
            line.setHsnSacCode(setting == null ? null : setting.getHsnSacCode());
            line.setSubtotal(c.subtotal());
            line.setDiscount(c.discount());
            line.setTaxableValue(c.taxableValue());
            line.setRatePercent(c.taxRate());
            line.setPriceMode(setting == null ? com.consoleadda.service.billing.PriceMode.EXCLUSIVE : setting.getPriceMode());
            line.setTaxAmount(c.tax());
            bill.addTaxLine(line);
        }

        if (totals.totalAmount().signum() > 0) {
            Payment p = new Payment();
            p.setPaymentType(PaymentType.PAYMENT);
            p.setMode(payment.mode());
            p.setAmount(totals.totalAmount());
            p.setReferenceNo(StringUtils.hasText(payment.referenceNo()) ? payment.referenceNo().trim() : null);
            p.setBusinessDate(businessDate);
            p.setReceivedBy(user.id());
            p.setReceivedAt(now);
            bill.addPayment(p);
        }
    }

    /** Audit entries are written after the bill is flushed, so they reference its id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void audit(Bill bill, Discount discount, PaymentMode mode, Map<String, Object> context) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("billNumber", bill.getBillNumber());
        entry.put("billType", bill.getBillType());
        if (context != null) {
            entry.putAll(context);
        }
        entry.put("subtotal", bill.getSubtotal());
        entry.put("discountAmount", bill.getDiscountAmount());
        entry.put("taxAmount", bill.getTaxAmount());
        entry.put("totalAmount", bill.getTotalAmount());
        entry.put("paymentMode", mode);
        auditService.record("BILL_CREATED", "Bill", bill.getId(), null, entry);
        if (discount != null) {
            auditService.record("DISCOUNT_APPLIED", "Bill", bill.getId(), null, Map.of(
                    "billNumber", bill.getBillNumber(), "discountCode", discount.getCode(),
                    "discountAmount", bill.getDiscountAmount()));
        }
    }
}
