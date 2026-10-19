package com.payment.nadi.application.dto.response;

public record ActivityLogDto(
        String id,
        String timestamp,
        String actor,
        String actorRole,
        String action,
        String actionLabel,
        String category,
        String status,
        String targetEntity,
        String details,
        String ipAddress,
        String channel
) {}
