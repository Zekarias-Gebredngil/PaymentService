package com.payment.nadi.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.nadi.application.dto.request.TransferRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.IdempotencyStore;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.domain.exception.InvalidPaymentException;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.repository.PaymentRepository;
import com.payment.nadi.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferServiceTest {

    private PaymentRepository paymentRepository;
    private TransactionRepository transactionRepository;
    private WalletService walletService;
    private EventPublisher eventPublisher;
    private IdempotencyStore idempotencyStore;
    private ObjectMapper objectMapper;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        walletService = mock(WalletService.class);
        eventPublisher = mock(EventPublisher.class);
        idempotencyStore = mock(IdempotencyStore.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyStore.lock(any(), anyLong())).thenReturn(true);

        transferService = new TransferService(
                paymentRepository,
                transactionRepository,
                walletService,
                eventPublisher,
                idempotencyStore,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should successfully transfer funds between distinct accounts")
    void shouldSuccessfullyTransfer() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();

        TransferRequest request = new TransferRequest(
                source,
                destination,
                new BigDecimal("150.00"),
                "ETB",
                UUID.randomUUID(),
                "P2P Transfer",
                "MOBILE_APP",
                null
        );

        when(walletService.hasSufficientBalance(eq(source), any(Money.class))).thenReturn(true);

        PaymentResponse response = transferService.initiateTransfer(request, "tx-key-1");

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.status());

        verify(walletService).debit(eq(source), any(Money.class), anyString(), anyString());
        verify(walletService).credit(eq(destination), any(Money.class), anyString(), anyString());
        verify(transactionRepository).save(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("Should reject transfer when source and destination are the same account")
    void shouldRejectSameAccountTransfer() {
        UUID account = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                account,
                account,
                new BigDecimal("50.00"),
                "ETB",
                UUID.randomUUID(),
                null,
                null,
                null
        );

        assertThrows(InvalidPaymentException.class, () ->
                transferService.initiateTransfer(request, "tx-key-err")
        );

        verifyNoInteractions(walletService);
    }
}
