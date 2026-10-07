package com.payment.nadi.infrastructure.persistence.adapter;

import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.infrastructure.persistence.entity.PaymentEntity;
import com.payment.nadi.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentRepositoryAdapterTest {

    private SpringDataPaymentRepository repository;
    private PaymentPersistenceMapper mapper;
    private PaymentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(SpringDataPaymentRepository.class);
        mapper = new PaymentPersistenceMapper();
        adapter = new PaymentRepositoryAdapter(repository, mapper);
    }

    @Test
    @DisplayName("Should persist brand new payment and map version from persisted entity")
    void shouldPersistNewPayment() {
        Payment payment = Payment.create(
                "TOP-NEW-001",
                PaymentType.TOP_UP,
                Money.of(new BigDecimal("100.00"), "ETB"),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                PaymentProviderType.TELEBIRR,
                "WEB",
                "New payment",
                null
        );

        when(repository.findById(payment.getId())).thenReturn(Optional.empty());
        when(repository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity entity = invocation.getArgument(0);
            entity.setVersion(0L);
            return entity;
        });

        Payment saved = adapter.save(payment);

        assertNotNull(saved);
        assertEquals(0L, saved.getVersion());
        assertEquals("TOP-NEW-001", saved.getReference());
        verify(repository).save(any(PaymentEntity.class));
    }

    @Test
    @DisplayName("Should update existing payment without optimistic lock failure")
    void shouldUpdateExistingPaymentWithoutOptimisticLockFailure() {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID destAccountId = UUID.randomUUID();

        PaymentEntity existingEntity = PaymentEntity.builder()
                .id(paymentId)
                .reference("TOP-EXIST-001")
                .type(PaymentType.TOP_UP)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("250.00"))
                .currency("ETB")
                .customerId(customerId)
                .destinationAccountId(destAccountId)
                .provider(PaymentProviderType.TELEBIRR)
                .providerReference("TEL-111")
                .sourceSystem("WEB")
                .version(1L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Payment payment = mapper.toDomain(existingEntity);
        assertEquals(1L, payment.getVersion());

        // Simulate callback transition to SUCCESS
        payment.markAsSuccess("TEL-222");

        when(repository.findById(paymentId)).thenReturn(Optional.of(existingEntity));
        when(repository.save(existingEntity)).thenAnswer(invocation -> {
            PaymentEntity entity = invocation.getArgument(0);
            entity.setVersion(2L); // Hibernate increments version on update
            return entity;
        });

        Payment updated = adapter.save(payment);

        assertNotNull(updated);
        assertEquals(PaymentStatus.SUCCESS, updated.getStatus());
        assertEquals("TEL-222", updated.getProviderReference());
        assertEquals(2L, updated.getVersion());
        verify(repository).save(existingEntity);
    }

    @Test
    @DisplayName("Should detect optimistic lock conflict if entity was concurrently modified")
    void shouldThrowWhenVersionsConflict() {
        UUID paymentId = UUID.randomUUID();

        PaymentEntity dbEntityAtVersion2 = PaymentEntity.builder()
                .id(paymentId)
                .reference("TOP-CONFLICT-001")
                .type(PaymentType.TOP_UP)
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("100.00"))
                .currency("ETB")
                .version(2L) // DB was already incremented by concurrent transaction
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Stale domain object holding version 1
        Payment stalePayment = new Payment();
        stalePayment.setId(paymentId);
        stalePayment.setReference("TOP-CONFLICT-001");
        stalePayment.setType(PaymentType.TOP_UP);
        stalePayment.setStatus(PaymentStatus.PENDING);
        stalePayment.setAmount(Money.of(new BigDecimal("100.00"), "ETB"));
        stalePayment.setVersion(1L); // Stale version

        when(repository.findById(paymentId)).thenReturn(Optional.of(dbEntityAtVersion2));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> adapter.save(stalePayment));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should update entity safely if payment has null version")
    void shouldUpdateSafelyWhenPaymentHasNullVersion() {
        UUID paymentId = UUID.randomUUID();

        PaymentEntity existingEntity = PaymentEntity.builder()
                .id(paymentId)
                .reference("TOP-LEGACY-001")
                .type(PaymentType.TOP_UP)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("50.00"))
                .currency("ETB")
                .version(1L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Payment payment = mapper.toDomain(existingEntity);
        payment.setVersion(null); // Simulate null version
        payment.markAsSuccess("TEL-LEGACY");

        when(repository.findById(paymentId)).thenReturn(Optional.of(existingEntity));
        when(repository.save(existingEntity)).thenAnswer(invocation -> {
            PaymentEntity entity = invocation.getArgument(0);
            entity.setVersion(2L);
            return entity;
        });

        Payment updated = adapter.save(payment);

        assertNotNull(updated);
        assertEquals(PaymentStatus.SUCCESS, updated.getStatus());
        assertEquals(2L, updated.getVersion());
    }
}
