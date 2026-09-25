package com.payment.nadi.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for idempotency key tracking to prevent duplicate financial processing.
 */
public interface IdempotencyStore {

    record IdempotencyEntry(
            String idempotencyKey,
            String requestHash,
            UUID paymentId,
            String responseJson,
            Instant createdAt,
            Instant expiresAt
    ) {}

    Optional<IdempotencyEntry> find(String idempotencyKey);

    void save(String idempotencyKey, String requestHash, UUID paymentId, String responseJson, long ttlMinutes);

    boolean lock(String idempotencyKey, long timeoutSeconds);

    void unlock(String idempotencyKey);
}
