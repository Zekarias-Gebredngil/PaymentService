package com.payment.nadi.domain.exception;

public class InsufficientBalanceException extends PaymentException {
    public InsufficientBalanceException(String message) {
        super("INSUFFICIENT_BALANCE", message);
    }
}
