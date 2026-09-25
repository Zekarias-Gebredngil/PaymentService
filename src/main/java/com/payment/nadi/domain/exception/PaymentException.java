package com.payment.nadi.domain.exception;

/**
 * Base domain exception for all payment-related errors.
 */
public class PaymentException extends RuntimeException {

    private final String errorCode;

    public PaymentException(String message) {
        super(message);
        this.errorCode = "PAYMENT_ERROR";
    }

    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PaymentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
