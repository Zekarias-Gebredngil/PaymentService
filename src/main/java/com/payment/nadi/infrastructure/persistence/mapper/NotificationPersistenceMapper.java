package com.payment.nadi.infrastructure.persistence.mapper;

import com.payment.nadi.application.dto.response.NotificationDto;
import com.payment.nadi.infrastructure.persistence.entity.NotificationEntity;

public final class NotificationPersistenceMapper {

    private NotificationPersistenceMapper() {}

    public static NotificationDto toDto(NotificationEntity e) {
        if (e == null) return null;
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
