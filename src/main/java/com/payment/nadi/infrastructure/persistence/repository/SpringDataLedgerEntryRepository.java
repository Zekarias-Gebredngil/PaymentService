package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.infrastructure.persistence.entity.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataLedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    List<LedgerEntryEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<LedgerEntryEntity> findByTransactionId(UUID transactionId);

    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END),
            0.0
        )
        FROM LedgerEntryEntity e
        WHERE e.accountId = :accountId
    """)
    BigDecimal calculateAccountBalance(@Param("accountId") UUID accountId);
}
