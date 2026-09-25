package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataPaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByReference(String reference);

    Optional<PaymentEntity> findByProviderReference(String providerReference);

    List<PaymentEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    List<PaymentEntity> findByStatus(PaymentStatus status);

    boolean existsByReference(String reference);
}
