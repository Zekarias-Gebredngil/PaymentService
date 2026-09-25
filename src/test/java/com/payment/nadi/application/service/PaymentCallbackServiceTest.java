package com.payment.nadi.application.service;

import com.payment.nadi.application.port.in.PaymentCallbackUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.domain.repository.PaymentAttemptRepository;
import com.payment.nadi.domain.repository.PaymentRepository;
import com.payment.nadi.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentCallbackServiceTest {

    private PaymentRepository paymentRepository;
    private TransactionRepository transactionRepository;
    private PaymentAttemptRepository attemptRepository;
    private WalletService walletService;
    private EventPublisher eventPublisher;

    private PaymentCallbackService callbackService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        attemptRepository = mock(PaymentAttemptRepository.class);
        walletService = mock(WalletService.class);
        eventPublisher = mock(EventPublisher.class);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        callbackService = new PaymentCallbackService(
                paymentRepository,
                transactionRepository,
                attemptRepository,
                walletService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("Should successfully settle top-up on valid callback")
    void shouldSettleTopUpOnCallback() {
        Money amount = Money.of(new BigDecimal("300.00"), "ETB");
        UUID destAccount = UUID.randomUUID();

        Payment payment = Payment.create(
                "TOP-CB-001",
                PaymentType.TOP_UP,
                amount,
                UUID.randomUUID(),
                null,
                destAccount,
                PaymentProviderType.TELEBIRR,
                "TEST",
                null,
                null
        );
        payment.markAsPending("TEL-CB-123");

        when(paymentRepository.findByReference("TOP-CB-001")).thenReturn(Optional.of(payment));

        PaymentCallbackUseCase.CallbackResult result = callbackService.processCallback(
                PaymentProviderType.TELEBIRR,
                "TOP-CB-001",
                "TEL-CB-123",
                PaymentStatus.SUCCESS,
                amount,
                "raw_payload",
                true
        );

        assertTrue(result.accepted());
        assertEquals(PaymentStatus.SUCCESS, result.finalStatus());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(walletService).credit(eq(destAccount), eq(amount), anyString(), anyString());
        verify(transactionRepository).save(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("Should reject callback when signature is invalid")
    void shouldRejectInvalidSignature() {
        PaymentCallbackUseCase.CallbackResult result = callbackService.processCallback(
                PaymentProviderType.TELEBIRR,
                "TOP-CB-002",
                "TEL-CB-123",
                PaymentStatus.SUCCESS,
                Money.of(new BigDecimal("100.00"), "ETB"),
                "raw_payload",
                false
        );

        assertFalse(result.accepted());
        assertEquals("Invalid signature", result.message());
        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("Should acknowledge duplicate callback idempotently without re-crediting wallet")
    void shouldHandleDuplicateCallbackIdempotently() {
        Money amount = Money.of(new BigDecimal("200.00"), "ETB");
        Payment payment = Payment.create(
                "TOP-CB-DUP",
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
        payment.markAsPending("TEL-DUP");
        payment.markAsSuccess("TEL-DUP"); // Already terminal SUCCESS

        when(paymentRepository.findByReference("TOP-CB-DUP")).thenReturn(Optional.of(payment));

        PaymentCallbackUseCase.CallbackResult result = callbackService.processCallback(
                PaymentProviderType.TELEBIRR,
                "TOP-CB-DUP",
                "TEL-DUP",
                PaymentStatus.SUCCESS,
                amount,
                "raw_payload",
                true
        );

        assertTrue(result.accepted());
        assertTrue(result.message().contains("Duplicate callback acknowledged"));
        verifyNoInteractions(walletService);
    }
}
