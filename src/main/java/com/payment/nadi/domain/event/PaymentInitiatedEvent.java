package com.payment.nadi.domain.event;

import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentType;

import java.time.Instant;
import java.util.UUID;

public record PaymentInitiatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID paymentId,
        String reference,
        PaymentType paymentType,
        Money amount,
        UUID customerId,
        String provider
) implements PaymentDomainEvent {

    public static PaymentInitiatedEvent now(
            UUID paymentId,
            String reference,
            PaymentType paymentType,
            Money amount,
            UUID customerId,
            String provider
    ) {
        return new PaymentInitiatedEvent(
                UUID.randomUUID(),
                "PAYMENT_INITIATED",
                Instant.now(),
                paymentId,
                reference,
                paymentType,
                amount,
                customerId,
                provider
        );
    }

    @Override public UUID getEventId() { return eventId; }
    @Override public String getEventType() { return eventType; }
    @Override public Instant getOccurredAt() { return occurredAt; }
    @Override public UUID getPaymentId() { return paymentId; }
}
