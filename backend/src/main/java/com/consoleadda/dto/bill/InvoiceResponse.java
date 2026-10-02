package com.consoleadda.dto.bill;

/** Everything needed to print an invoice: business header from settings + the stored bill. */
public record InvoiceResponse(Business business, BillDetail bill, String footer) {

    public record Business(String name, String tagline, String address, String phone, String gstin) {
    }
}
