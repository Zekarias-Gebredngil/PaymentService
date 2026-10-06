package com.payment.nadi.application.dto.response;

import com.payment.nadi.infrastructure.persistence.entity.NotificationEntity;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        UUID userId,
        String phoneNumber,
        String channel,
        String type,
        String title,
        String message,
        String status,
        String referenceId,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationDto fromEntity(NotificationEntity e) {
        return new NotificationDto(
                e.getId(),
                e.getUserId(),
                e.getPhoneNumber(),
                e.getChannel() != null ? e.getChannel().name() : null,
                e.getType() != null ? e.getType().name() : null,
                e.getTitle(),
                e.getMessage(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.getReferenceId(),
                e.getCreatedAt(),
                e.getReadAt()
        );
    }
}
