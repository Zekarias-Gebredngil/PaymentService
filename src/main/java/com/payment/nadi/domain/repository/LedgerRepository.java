package com.payment.nadi.domain.repository;

import com.payment.nadi.domain.model.LedgerEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerRepository {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> saveAll(List<LedgerEntry> entries);

    List<LedgerEntry> findByAccountId(UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    BigDecimal calculateBalance(UUID accountId);
}
