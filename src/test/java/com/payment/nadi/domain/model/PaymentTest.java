package com.payment.nadi.domain.model;

import com.payment.nadi.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    @DisplayName("Should create payment in CREATED status")
    void shouldCreatePaymentInCreatedStatus() {
        Money amount = Money.of(new BigDecimal("500.00"), "ETB");
        Payment payment = Payment.create(
                "TOP-2026-001",
                PaymentType.TOP_UP,
                amount,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                PaymentProviderType.TELEBIRR,
                "TEST",
                "Test Payment",
                null
        );

        assertNotNull(payment.getId());
        assertEquals("TOP-2026-001", payment.getReference());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        assertEquals(amount, payment.getAmount());
    }

    @Test
    @DisplayName("Should successfully transition CREATED -> PENDING -> SUCCESS")
    void shouldFollowValidLifecycle() {
        Money amount = Money.of(new BigDecimal("100.00"), "ETB");
        Payment payment = Payment.create(
                "TOP-2026-002",
                PaymentType.TOP_UP,
                amount,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                PaymentProviderType.TELEBIRR,
                "TEST",
                null,
                null
        );

        payment.markAsPending("TEL-12345");
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals("TEL-12345", payment.getProviderReference());

        payment.markAsSuccess("TEL-12345");
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertTrue(payment.getStatus().isTerminal());
    }

    @Test
    @DisplayName("Should successfully transition CREATED -> SUCCESS directly on synchronous settlement")
    void shouldAllowDirectCreatedToSuccessTransition() {
        Money amount = Money.of(new BigDecimal("50.00"), "ETB");
        Payment payment = Payment.create(
                "TX-2026-DIRECT",
                PaymentType.TRANSFER,
                amount,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentProviderType.INTERNAL_LEDGER,
                "TEST",
                null,
                null
        );

        payment.markAsSuccess("TX-REF-123");
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertTrue(payment.getStatus().isTerminal());
    }

    @Test
    @DisplayName("Should reject illegal transition from terminal SUCCESS to FAILED")
    void shouldRejectTransitionFromTerminalSuccess() {
        Money amount = Money.of(new BigDecimal("100.00"), "ETB");
        Payment payment = Payment.create(
                "TOP-2026-003",
                PaymentType.TOP_UP,
                amount,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                PaymentProviderType.TELEBIRR,
                "TEST",
                null,
                null
        );

        payment.markAsPending("TEL-123");
        payment.markAsSuccess("TEL-123");

        assertThrows(InvalidStateTransitionException.class, () ->
                payment.markAsFailed("Network error")
        );
    }
}
