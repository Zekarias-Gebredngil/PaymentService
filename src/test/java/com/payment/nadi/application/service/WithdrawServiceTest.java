package com.payment.nadi.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.nadi.application.dto.request.PaymentMethodDto;
import com.payment.nadi.application.dto.request.WithdrawRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.IdempotencyStore;
import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.PaymentProviderResolver;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.application.port.out.dto.PaymentInitiationResult;
import com.payment.nadi.domain.exception.InsufficientBalanceException;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.repository.PaymentAttemptRepository;
import com.payment.nadi.domain.repository.PaymentRepository;
import com.payment.nadi.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WithdrawServiceTest {

    private PaymentRepository paymentRepository;
    private TransactionRepository transactionRepository;
    private PaymentAttemptRepository attemptRepository;
    private PaymentProviderResolver providerResolver;
    private PaymentProvider paymentProvider;
    private WalletService walletService;
    private EventPublisher eventPublisher;
    private IdempotencyStore idempotencyStore;
    private ObjectMapper objectMapper;

    private WithdrawService withdrawService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        attemptRepository = mock(PaymentAttemptRepository.class);
        providerResolver = mock(PaymentProviderResolver.class);
        paymentProvider = mock(PaymentProvider.class);
        walletService = mock(WalletService.class);
        eventPublisher = mock(EventPublisher.class);
        idempotencyStore = mock(IdempotencyStore.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerResolver.resolve(PaymentProviderType.TELEBIRR)).thenReturn(paymentProvider);
        when(paymentProvider.getProvider()).thenReturn(PaymentProviderType.TELEBIRR);

        withdrawService = new WithdrawService(
                paymentRepository,
                transactionRepository,
                attemptRepository,
                providerResolver,
                walletService,
                eventPublisher,
                idempotencyStore,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should successfully withdraw and debit wallet balance")
    void shouldSuccessfullyWithdraw() {
        UUID sourceAccount = UUID.randomUUID();
        WithdrawRequest request = new WithdrawRequest(
                new BigDecimal("250.00"),
                "ETB",
                UUID.randomUUID(),
                sourceAccount,
                new PaymentMethodDto(PaymentProviderType.TELEBIRR, "0911223344"),
                "Withdraw to Telebirr",
                "MOBILE_APP",
                null
        );

        when(walletService.hasSufficientBalance(eq(sourceAccount), any(Money.class))).thenReturn(true);
        when(paymentProvider.initiateWithdraw(any())).thenReturn(
                PaymentInitiationResult.success("B2C_TXN_999", "{\"status\":\"OK\"}")
        );

        PaymentResponse response = withdrawService.initiateWithdraw(request, "wth-idemp-1");

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.status());
        verify(walletService).debit(eq(sourceAccount), any(Money.class), anyString(), anyString());
        verify(transactionRepository).save(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("Should reject withdrawal when wallet balance is insufficient")
    void shouldRejectWhenBalanceInsufficient() {
        UUID sourceAccount = UUID.randomUUID();
        WithdrawRequest request = new WithdrawRequest(
                new BigDecimal("5000.00"),
                "ETB",
                UUID.randomUUID(),
                sourceAccount,
                new PaymentMethodDto(PaymentProviderType.TELEBIRR, "0911223344"),
                null,
                null,
                null
        );

        when(walletService.hasSufficientBalance(eq(sourceAccount), any(Money.class))).thenReturn(false);

        assertThrows(InsufficientBalanceException.class, () ->
                withdrawService.initiateWithdraw(request, "wth-fail-1")
        );

        verifyNoInteractions(paymentProvider);
    }
}
