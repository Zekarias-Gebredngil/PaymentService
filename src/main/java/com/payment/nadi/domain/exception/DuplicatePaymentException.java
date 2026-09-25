package com.payment.nadi.domain.exception;

public class DuplicatePaymentException extends PaymentException {
    public DuplicatePaymentException(String message) {
        super("DUPLICATE_PAYMENT", message);
    }
}
