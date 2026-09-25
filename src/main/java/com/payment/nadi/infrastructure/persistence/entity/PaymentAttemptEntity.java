package com.payment.nadi.infrastructure.persistence.entity;

import com.payment.nadi.domain.model.PaymentProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts", indexes = {
        @Index(name = "idx_attempts_payment_id", columnList = "payment_id"),
        @Index(name = "idx_attempts_provider_ref", columnList = "provider_reference")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttemptEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private PaymentProviderType provider;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
