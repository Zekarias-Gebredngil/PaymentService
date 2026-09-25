package com.payment.nadi.infrastructure.persistence.adapter;

import com.payment.nadi.domain.model.Transaction;
import com.payment.nadi.domain.repository.TransactionRepository;
import com.payment.nadi.infrastructure.persistence.entity.TransactionEntity;
import com.payment.nadi.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionRepositoryAdapter implements TransactionRepository {

    private final SpringDataTransactionRepository repository;
    private final PaymentPersistenceMapper mapper;

    public TransactionRepositoryAdapter(SpringDataTransactionRepository repository, PaymentPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = mapper.toEntity(transaction);
        TransactionEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transaction> findByTransactionReference(String transactionReference) {
        return repository.findByTransactionReference(transactionReference).map(mapper::toDomain);
    }

    @Override
    public List<Transaction> findByPaymentId(UUID paymentId) {
        return repository.findByPaymentIdOrderByCreatedAtDesc(paymentId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findByAccountId(UUID accountId, int limit, int offset) {
        int pageNumber = offset / limit;
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(pageNumber, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
