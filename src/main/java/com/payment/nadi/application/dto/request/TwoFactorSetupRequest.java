package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorSetupRequest(
        @NotBlank(message = "Identifier (phone or account number) is required")
        String identifier
) {}
