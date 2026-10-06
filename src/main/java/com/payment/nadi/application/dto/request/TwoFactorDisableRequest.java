package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorDisableRequest(
        @NotBlank(message = "Identifier (phone or account number) is required")
        String identifier,

        @NotBlank(message = "Password is required to disable 2FA")
        String password,

        String code
) {}
