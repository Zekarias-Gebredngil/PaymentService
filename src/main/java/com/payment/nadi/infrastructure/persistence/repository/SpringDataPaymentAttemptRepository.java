package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.infrastructure.persistence.entity.PaymentAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataPaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {

    List<PaymentAttemptEntity> findByPaymentIdOrderByAttemptNumberAsc(UUID paymentId);

    int countByPaymentId(UUID paymentId);
}
