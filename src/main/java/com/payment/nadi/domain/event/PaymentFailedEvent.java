package com.payment.nadi.domain.event;

import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentType;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID paymentId,
        String reference,
        PaymentType paymentType,
        Money amount,
        UUID customerId,
        String reason
) implements PaymentDomainEvent {

    public static PaymentFailedEvent now(
            UUID paymentId,
            String reference,
            PaymentType paymentType,
            Money amount,
            UUID customerId,
            String reason
    ) {
        return new PaymentFailedEvent(
                UUID.randomUUID(),
                "PAYMENT_FAILED",
                Instant.now(),
                paymentId,
                reference,
                paymentType,
                amount,
                customerId,
                reason
        );
    }

    @Override public UUID getEventId() { return eventId; }
    @Override public String getEventType() { return eventType; }
    @Override public Instant getOccurredAt() { return occurredAt; }
    @Override public UUID getPaymentId() { return paymentId; }
}
