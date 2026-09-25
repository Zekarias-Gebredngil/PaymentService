package com.payment.nadi.application.service;

import com.payment.nadi.application.port.in.PaymentCallbackUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.PaymentProviderResolver;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.application.port.out.dto.PaymentStatusResult;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentReconciliationServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentProviderResolver providerResolver;
    private PaymentProvider paymentProvider;
    private PaymentCallbackUseCase callbackUseCase;
    private WalletService walletService;
    private EventPublisher eventPublisher;

    private PaymentReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        providerResolver = mock(PaymentProviderResolver.class);
        paymentProvider = mock(PaymentProvider.class);
        callbackUseCase = mock(PaymentCallbackUseCase.class);
        walletService = mock(WalletService.class);
        eventPublisher = mock(EventPublisher.class);

        when(providerResolver.resolve(PaymentProviderType.TELEBIRR)).thenReturn(paymentProvider);

        reconciliationService = new PaymentReconciliationService(
                paymentRepository,
                providerResolver,
                callbackUseCase,
                walletService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("Should query provider and settle in-doubt payment when provider confirms SUCCESS")
    void shouldSettleInDoubtPaymentWhenProviderConfirmsSuccess() {
        Payment payment = Payment.create(
                "TOP-REC-001",
                PaymentType.TOP_UP,
                Money.of(new BigDecimal("100.00"), "ETB"),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                PaymentProviderType.TELEBIRR,
                "TEST",
                null,
                null
        );
        payment.markAsPending("TEL-REC-123");
        // Created 5 minutes ago
        payment.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(paymentRepository.findByStatus(PaymentStatus.PENDING)).thenReturn(List.of(payment));
        when(paymentRepository.findByStatus(PaymentStatus.PROCESSING)).thenReturn(List.of());
        when(paymentProvider.checkStatus("TEL-REC-123")).thenReturn(
                PaymentStatusResult.success("TEL-REC-123", Money.of(new BigDecimal("100.00"), "ETB"), "OK")
        );

        reconciliationService.reconcilePendingPayments();

        verify(callbackUseCase).processCallback(
                eq(PaymentProviderType.TELEBIRR),
                eq("TOP-REC-001"),
                eq("TEL-REC-123"),
                eq(PaymentStatus.SUCCESS),
                any(Money.class),
                eq("RECONCILIATION_SYNC"),
                eq(true)
        );
    }

    @Test
    @DisplayName("Should mark payment as EXPIRED and revert wallet if older than 30 minutes without confirmation")
    void shouldExpireStalePaymentAfterTimeout() {
        UUID sourceAccount = UUID.randomUUID();
        Payment payment = Payment.create(
                "WTH-REC-OLD",
                PaymentType.WITHDRAW,
                Money.of(new BigDecimal("75.00"), "ETB"),
                UUID.randomUUID(),
                sourceAccount,
                null,
                PaymentProviderType.TELEBIRR,
                "TEST",
                null,
                null
        );
        payment.markAsPending("TEL-REC-STALE");
        // Created 45 minutes ago
        payment.setCreatedAt(Instant.now().minus(45, ChronoUnit.MINUTES));

        when(paymentRepository.findByStatus(PaymentStatus.PENDING)).thenReturn(List.of(payment));
        when(paymentRepository.findByStatus(PaymentStatus.PROCESSING)).thenReturn(List.of());
        when(paymentProvider.checkStatus("TEL-REC-STALE")).thenReturn(
                PaymentStatusResult.pending("TEL-REC-STALE", Money.of(new BigDecimal("75.00"), "ETB"), "PENDING")
        );

        reconciliationService.reconcilePendingPayments();

        assertEquals(PaymentStatus.EXPIRED, payment.getStatus());
        verify(walletService).credit(eq(sourceAccount), eq(Money.of(new BigDecimal("75.00"), "ETB")), anyString(), anyString());
        verify(paymentRepository).save(payment);
        verify(eventPublisher).publish(any());
    }
}
