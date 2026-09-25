package com.payment.nadi.domain.exception;

import java.util.UUID;

public class PaymentNotFoundException extends PaymentException {
    public PaymentNotFoundException(UUID id) {
        super("PAYMENT_NOT_FOUND", "Payment not found with id: " + id);
    }

    public PaymentNotFoundException(String reference) {
        super("PAYMENT_NOT_FOUND", "Payment not found with reference: " + reference);
    }
}
