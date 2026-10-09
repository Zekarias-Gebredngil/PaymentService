package com.payment.nadi.infrastructure.persistence.adapter;

import com.payment.nadi.application.port.out.IdempotencyStore;
import com.payment.nadi.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Component
public class DatabaseIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(DatabaseIdempotencyStore.class);

    private final SpringDataIdempotencyRepository repository;

    public DatabaseIdempotencyStore(SpringDataIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyEntry> find(String idempotencyKey) {
        return repository.findById(idempotencyKey)
                .filter(record -> record.getExpiresAt().isAfter(Instant.now()))
                .map(r -> new IdempotencyEntry(
                        r.getIdempotencyKey(),
                        r.getRequestHash(),
                        r.getPaymentId(),
                        r.getResponseJson(),
                        r.getCreatedAt(),
                        r.getExpiresAt()
                ));
    }

    @Override
    @Transactional
    public void save(String idempotencyKey, String requestHash, UUID paymentId, String responseJson, long ttlMinutes) {
        Instant now = Instant.now();
        IdempotencyRecordEntity entity = IdempotencyRecordEntity.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .paymentId(paymentId)
                .responseJson(responseJson)
                .locked(false)
                .createdAt(now)
                .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
                .build();
        repository.save(entity);
    }

    @Override
    @Transactional
    public boolean lock(String idempotencyKey, long timeoutSeconds) {
        try {
            Optional<IdempotencyRecordEntity> existing = repository.findById(idempotencyKey);
            if (existing.isPresent()) {
                IdempotencyRecordEntity record = existing.get();
                // If existing expired, allow re-lock
                if (record.getExpiresAt().isBefore(Instant.now())) {
                    repository.delete(record);
                } else {
                    return false;
                }
            }
            Instant now = Instant.now();
            IdempotencyRecordEntity entity = IdempotencyRecordEntity.builder()
                    .idempotencyKey(idempotencyKey)
                    .requestHash("IN_PROGRESS")
                    .locked(true)
                    .createdAt(now)
                    .expiresAt(now.plus(timeoutSeconds, ChronoUnit.SECONDS))
                    .build();
            repository.saveAndFlush(entity);
            return true;
        } catch (Exception ex) {
            log.warn("Could not acquire atomic idempotency lock for key {}: {}", idempotencyKey, ex.getMessage());
            return false;
        }
    }

    @Override
    @Transactional
    public void unlock(String idempotencyKey) {
        repository.findById(idempotencyKey).ifPresent(entity -> {
            if (entity.getResponseJson() == null || "IN_PROGRESS".equals(entity.getRequestHash())) {
                repository.delete(entity);
            } else {
                entity.setLocked(false);
                repository.save(entity);
            }
        });
    }

    @Scheduled(fixedRate = 3600000) // Hourly cleanup of expired idempotency keys
    @Transactional
    public void purgeExpiredKeys() {
        log.debug("Purging expired idempotency keys");
        repository.deleteExpired(Instant.now());
    }
}
