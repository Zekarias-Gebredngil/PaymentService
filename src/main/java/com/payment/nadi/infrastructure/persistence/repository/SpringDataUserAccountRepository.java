package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataUserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    Optional<UserAccountEntity> findByPhoneNumber(String phoneNumber);

    Optional<UserAccountEntity> findByAccountNumber(String accountNumber);

    Optional<UserAccountEntity> findByEmail(String email);

    Optional<UserAccountEntity> findByCustomerId(UUID customerId);

    Optional<UserAccountEntity> findBySourceAccountId(UUID sourceAccountId);

    List<UserAccountEntity> findAllByOrderByCreatedAtDesc();

    List<UserAccountEntity> findByStatusOrderByCreatedAtDesc(String status);

    Page<UserAccountEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<UserAccountEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<UserAccountEntity> findByStatus(String status, Pageable pageable);

    @Query("SELECT u FROM UserAccountEntity u WHERE " +
            "(:status = 'ALL' OR u.status = :status) AND (" +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "u.phoneNumber LIKE CONCAT('%', :query, '%') OR " +
            "u.accountNumber LIKE CONCAT('%', :query, '%') OR " +
            "(u.email IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))) " +
            "ORDER BY u.createdAt DESC")
    Page<UserAccountEntity> searchAccounts(@Param("status") String status, @Param("query") String query, Pageable pageable);

    long countByStatus(String status);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByAccountNumber(String accountNumber);
}