package com.payment.nadi.application.dto.response;

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
) {}
