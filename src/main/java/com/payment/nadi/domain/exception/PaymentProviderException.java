package com.payment.nadi.domain.exception;

public class PaymentProviderException extends PaymentException {

    private final String provider;

    public PaymentProviderException(String provider, String message) {
        super("PROVIDER_ERROR", "[" + provider + "] " + message);
        this.provider = provider;
    }

    public PaymentProviderException(String provider, String errorCode, String message) {
        super(errorCode, "[" + provider + "] " + message);
        this.provider = provider;
    }

    public PaymentProviderException(String provider, String message, Throwable cause) {
        super("PROVIDER_ERROR", "[" + provider + "] " + message, cause);
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
