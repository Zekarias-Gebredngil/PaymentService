package com.payment.nadi.infrastructure.persistence.adapter;

import com.payment.nadi.domain.model.LedgerEntry;
import com.payment.nadi.domain.repository.LedgerRepository;
import com.payment.nadi.infrastructure.persistence.entity.LedgerEntryEntity;
import com.payment.nadi.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataLedgerEntryRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class LedgerRepositoryAdapter implements LedgerRepository {

    private final SpringDataLedgerEntryRepository repository;
    private final PaymentPersistenceMapper mapper;

    public LedgerRepositoryAdapter(SpringDataLedgerEntryRepository repository, PaymentPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        LedgerEntryEntity entity = mapper.toEntity(entry);
        LedgerEntryEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<LedgerEntry> saveAll(List<LedgerEntry> entries) {
        List<LedgerEntryEntity> entities = entries.stream().map(mapper::toEntity).toList();
        return repository.saveAll(entities).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<LedgerEntry> findByAccountId(UUID accountId) {
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<LedgerEntry> findByTransactionId(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public BigDecimal calculateBalance(UUID accountId) {
        BigDecimal balance = repository.calculateAccountBalance(accountId);
        return balance != null ? balance : BigDecimal.ZERO;
    }
}
