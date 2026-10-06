package com.payment.nadi.domain.event;

import java.time.Instant;
import java.util.UUID;

public record UserSecurityEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID userId,
        String phoneNumber,
        String action,
        String details
) implements PaymentDomainEvent {

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public UUID getPaymentId() {
        return userId;
    }

    public static UserSecurityEvent now(UUID userId, String phoneNumber, String action, String details) {
        return new UserSecurityEvent(
                UUID.randomUUID(),
                "SECURITY." + action,
                Instant.now(),
                userId,
                phoneNumber,
                action,
                details
        );
    }
}
