package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequest(
        @NotBlank(message = "Identifier (phone or account number) is required")
        String identifier,

        @NotBlank(message = "Verification code is required")
        String code
) {}
