package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByAccountNumber(String accountNumber);
}
