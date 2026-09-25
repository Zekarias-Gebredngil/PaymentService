package com.payment.nadi.domain.exception;

public class InvalidPaymentException extends PaymentException {
    public InvalidPaymentException(String message) {
        super("INVALID_PAYMENT", message);
    }
}
