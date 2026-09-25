package com.payment.nadi.domain.event;

import java.time.Instant;
import java.util.UUID;

public interface PaymentDomainEvent {
    UUID getEventId();
    String getEventType();
    Instant getOccurredAt();
    UUID getPaymentId();
}
