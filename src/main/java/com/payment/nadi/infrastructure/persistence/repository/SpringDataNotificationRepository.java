package com.payment.nadi.infrastructure.persistence.repository;

import com.payment.nadi.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<NotificationEntity> findByPhoneNumberOrderByCreatedAtDesc(String phoneNumber, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    long countByPhoneNumberAndReadAtIsNull(String phoneNumber);

    List<NotificationEntity> findByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.readAt = :readAt WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllAsReadForUser(@Param("userId") UUID userId, @Param("readAt") Instant readAt);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.readAt = :readAt WHERE n.phoneNumber = :phoneNumber AND n.readAt IS NULL")
    int markAllAsReadForPhone(@Param("phoneNumber") String phoneNumber, @Param("readAt") Instant readAt);
}
