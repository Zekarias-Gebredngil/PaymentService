package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataTransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByTransactionReference(String transactionReference);

    List<TransactionEntity> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    List<TransactionEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
