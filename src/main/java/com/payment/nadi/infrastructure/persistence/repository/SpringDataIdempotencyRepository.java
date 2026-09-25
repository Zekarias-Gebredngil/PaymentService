package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.infrastructure.persistence.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SpringDataIdempotencyRepository extends JpaRepository<IdempotencyRecordEntity, String> {

    @Modifying
    @Query("DELETE FROM IdempotencyRecordEntity r WHERE r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
