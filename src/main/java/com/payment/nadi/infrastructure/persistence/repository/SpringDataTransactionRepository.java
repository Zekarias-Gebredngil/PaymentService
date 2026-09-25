package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataTransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByTransactionReference(String transactionReference);

    List<TransactionEntity> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    List<TransactionEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
