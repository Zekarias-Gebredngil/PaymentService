package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record SendNotificationRequest(
        UUID userId,
        String phoneNumber,
        @NotBlank(message = "Channel (SMS or PUSH) is required")
        String channel,
        @NotBlank(message = "Title is required")
        String title,
        @NotBlank(message = "Message is required")
        String message,
        String type,
        String referenceId
) {}
