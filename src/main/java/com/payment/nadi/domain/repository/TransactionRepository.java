package com.payment.nadi.domain.repository;

import com.payment.nadi.domain.model.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByTransactionReference(String transactionReference);

    List<Transaction> findByPaymentId(UUID paymentId);

    List<Transaction> findByAccountId(UUID accountId, int limit, int offset);
}
