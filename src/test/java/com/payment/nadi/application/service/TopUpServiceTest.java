package com.payment.nadi.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.nadi.application.dto.request.PaymentMethodDto;
import com.payment.nadi.application.dto.request.TopUpRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.IdempotencyStore;
import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.PaymentProviderResolver;
import com.payment.nadi.application.port.out.dto.PaymentInitiationResult;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.repository.PaymentAttemptRepository;
import com.payment.nadi.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TopUpServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentAttemptRepository attemptRepository;
    private PaymentProviderResolver providerResolver;
    private PaymentProvider paymentProvider;
    private EventPublisher eventPublisher;
    private IdempotencyStore idempotencyStore;
    private ObjectMapper objectMapper;

    private TopUpService topUpService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        attemptRepository = mock(PaymentAttemptRepository.class);
        providerResolver = mock(PaymentProviderResolver.class);
        paymentProvider = mock(PaymentProvider.class);
        eventPublisher = mock(EventPublisher.class);
        idempotencyStore = mock(IdempotencyStore.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerResolver.resolve(PaymentProviderType.TELEBIRR)).thenReturn(paymentProvider);
        when(paymentProvider.getProvider()).thenReturn(PaymentProviderType.TELEBIRR);

        topUpService = new TopUpService(
                paymentRepository,
                attemptRepository,
                providerResolver,
                eventPublisher,
                idempotencyStore,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should successfully initiate top-up through Telebirr provider")
    void shouldInitiateTopUpSuccessfully() {
        TopUpRequest request = new TopUpRequest(
                new BigDecimal("500.00"),
                "ETB",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new PaymentMethodDto(PaymentProviderType.TELEBIRR, "0911223344"),
                "https://example.com/return",
                "Top-up wallet",
                "MOBILE_APP",
                null
        );

        when(paymentProvider.initiateTopUp(any())).thenReturn(
                PaymentInitiationResult.pending("ORDER_123", "https://telebirr.et/pay?token=xyz", "{\"code\":\"0\"}")
        );

        PaymentResponse response = topUpService.initiateTopUp(request, "idemp-key-1");

        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING, response.status());
        assertEquals("https://telebirr.et/pay?token=xyz", response.redirectUrl());
        verify(attemptRepository).save(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("Should return cached response on idempotent top-up request")
    void shouldReturnCachedIdempotentResponse() throws Exception {
        TopUpRequest request = new TopUpRequest(
                new BigDecimal("100.00"),
                "ETB",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new PaymentMethodDto(PaymentProviderType.TELEBIRR, "0911223344"),
                null,
                null,
                null,
                null
        );

        Payment payment = Payment.create(
                "TOP-CACHED-01",
                com.payment.nadi.domain.model.PaymentType.TOP_UP,
                Money.of(new BigDecimal("100.00"), "ETB"),
                request.customerId(),
                null,
                request.destinationAccountId(),
                PaymentProviderType.TELEBIRR,
                "MOBILE_APP",
                "Cached topup",
                null
        );
        payment.markAsPending("ORDER_CACHED");
        PaymentResponse cached = PaymentResponse.from(payment, "https://cached.url");

        Instant now = Instant.now();
        when(idempotencyStore.find("idemp-duplicate")).thenReturn(
                Optional.of(new IdempotencyStore.IdempotencyEntry("idemp-duplicate", "hash", cached.paymentId(), objectMapper.writeValueAsString(cached), now, now.plusSeconds(3600)))
        );

        PaymentResponse response = topUpService.initiateTopUp(request, "idemp-duplicate");

        assertEquals("TOP-CACHED-01", response.reference());
        verifyNoInteractions(paymentProvider);
    }
}
