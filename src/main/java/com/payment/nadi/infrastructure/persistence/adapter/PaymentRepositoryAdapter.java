package com.payment.nadi.infrastructure.persistence.adapter;

import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.repository.PaymentRepository;
import com.payment.nadi.infrastructure.persistence.entity.PaymentEntity;
import com.payment.nadi.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataPaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final SpringDataPaymentRepository repository;
    private final PaymentPersistenceMapper mapper;

    public PaymentRepositoryAdapter(SpringDataPaymentRepository repository, PaymentPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() != null) {
            Optional<PaymentEntity> existingOpt = repository.findById(payment.getId());
            if (existingOpt.isPresent()) {
                PaymentEntity existing = existingOpt.get();
                if (payment.getVersion() != null && !payment.getVersion().equals(existing.getVersion())) {
                    throw new ObjectOptimisticLockingFailureException(PaymentEntity.class, payment.getId());
                }
                mapper.updateEntity(existing, payment);
                PaymentEntity saved = repository.save(existing);
                return mapper.toDomain(saved);
            }
        }
        PaymentEntity entity = mapper.toEntity(payment);
        PaymentEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByReference(String reference) {
        return repository.findByReference(reference).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByProviderReference(String providerReference) {
        return repository.findByProviderReference(providerReference).map(mapper::toDomain);
    }

    @Override
    public List<Payment> findByCustomerId(UUID customerId, int limit, int offset) {
        int pageSize = Math.max(1, limit);
        int pageNumber = Math.max(0, offset / pageSize);
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(pageNumber, pageSize))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Payment> findByStatus(PaymentStatus status) {
        return repository.findByStatus(status)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByReference(String reference) {
        return repository.existsByReference(reference);
    }
}
