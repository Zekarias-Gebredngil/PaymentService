package com.payment.nadi.domain.model;

/**
 * Supported payment providers.
 * Designed for multi-provider extensibility (Telebirr, Chapa, CBE Birr, Stripe, Internal Ledger, etc.)
 */
public enum PaymentProviderType {
    TELEBIRR,
    CHAPA,
    CBE_BIRR,
    STRIPE,
    INTERNAL_LEDGER
}
