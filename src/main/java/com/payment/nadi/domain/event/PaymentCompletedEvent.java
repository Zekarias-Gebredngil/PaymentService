package com.payment.nadi.domain.event;

import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentType;

import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID paymentId,
        String reference,
        PaymentType paymentType,
        Money amount,
        UUID customerId,
        String providerReference,
        String sourceSystem
) implements PaymentDomainEvent {

    public static PaymentCompletedEvent now(
            UUID paymentId,
            String reference,
            PaymentType paymentType,
            Money amount,
            UUID customerId,
            String providerReference,
            String sourceSystem
    ) {
        return new PaymentCompletedEvent(
                UUID.randomUUID(),
                "PAYMENT_COMPLETED",
                Instant.now(),
                paymentId,
                reference,
                paymentType,
                amount,
                customerId,
                providerReference,
                sourceSystem
        );
    }

    @Override public UUID getEventId() { return eventId; }
    @Override public String getEventType() { return eventType; }
    @Override public Instant getOccurredAt() { return occurredAt; }
    @Override public UUID getPaymentId() { return paymentId; }
}
