package com.payment.nadi.infrastructure.persistence.adapter;

import com.payment.nadi.domain.model.PaymentAttempt;
import com.payment.nadi.domain.repository.PaymentAttemptRepository;
import com.payment.nadi.infrastructure.persistence.entity.PaymentAttemptEntity;
import com.payment.nadi.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataPaymentAttemptRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PaymentAttemptRepositoryAdapter implements PaymentAttemptRepository {

    private final SpringDataPaymentAttemptRepository repository;
    private final PaymentPersistenceMapper mapper;

    public PaymentAttemptRepositoryAdapter(SpringDataPaymentAttemptRepository repository, PaymentPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PaymentAttempt save(PaymentAttempt attempt) {
        PaymentAttemptEntity entity = mapper.toEntity(attempt);
        PaymentAttemptEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(UUID paymentId) {
        return repository.findByPaymentIdOrderByAttemptNumberAsc(paymentId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public int countByPaymentId(UUID paymentId) {
        return repository.countByPaymentId(paymentId);
    }
}
