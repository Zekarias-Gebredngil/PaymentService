package com.payment.nadi.application.service;

import com.payment.nadi.domain.model.PaymentType;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * DRY Utility for generating collision-resistant business references.
 */
public final class PaymentReferenceGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "0123456789ABCDEF";

    private PaymentReferenceGenerator() {}

    public static String generatePaymentReference(PaymentType type) {
        String prefix = switch (type) {
            case TOP_UP -> "TOP";
            case WITHDRAW -> "WDR";
            case TRANSFER -> "TXF";
            case REFUND -> "RFD";
        };
        return generate(prefix);
    }

    public static String generateTransactionReference() {
        return generate("TXN");
    }

    private static String generate(String prefix) {
        String date = LocalDate.now().format(DATE_FORMAT);
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return String.format("%s-%s-%s", prefix, date, sb);
    }
}
