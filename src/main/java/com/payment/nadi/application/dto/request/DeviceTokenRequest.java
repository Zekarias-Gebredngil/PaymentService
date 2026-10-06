package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenRequest(
        @NotBlank(message = "Identifier (phone or account number) is required")
        String identifier,

        @NotBlank(message = "Push device token is required")
        String pushToken,

        Boolean pushEnabled,
        Boolean smsEnabled
) {}
