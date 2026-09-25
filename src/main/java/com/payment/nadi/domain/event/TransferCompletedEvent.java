package com.payment.nadi.domain.event;

import com.payment.nadi.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

public record TransferCompletedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID paymentId,
        String reference,
        Money amount,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String description
) implements PaymentDomainEvent {

    public static TransferCompletedEvent now(
            UUID paymentId,
            String reference,
            Money amount,
            UUID sourceAccountId,
            UUID destinationAccountId,
            String description
    ) {
        return new TransferCompletedEvent(
                UUID.randomUUID(),
                "TRANSFER_COMPLETED",
                Instant.now(),
                paymentId,
                reference,
                amount,
                sourceAccountId,
                destinationAccountId,
                description
        );
    }

    @Override public UUID getEventId() { return eventId; }
    @Override public String getEventType() { return eventType; }
    @Override public Instant getOccurredAt() { return occurredAt; }
    @Override public UUID getPaymentId() { return paymentId; }
}
